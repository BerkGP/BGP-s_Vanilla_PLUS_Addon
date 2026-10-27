package bgps.vanillaplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * /bgps:recipe_book - sadece paketteki ozel tarifleri gosteren sandik GUI.
 * Tarifler data pack'ten (sunucuya yuklenmis haliyle) otomatik okunur.
 */
public final class RecipeBook implements Listener {

    // Gosterilecek tarif isim alanlari (yeni data pack eklersen buraya ekle)
    private static final Set<String> NAMESPACES = Set.of("mace_drill", "armored_elytra");

    // Gorsel duzeltme icin: sablon ve buyu kitabi tarifleri (esyalarini oradan aliriz)
    private static final NamespacedKey TEMPLATE_RECIPE = new NamespacedKey("mace_drill", "rainbow_template");
    private static final NamespacedKey BOOK_RECIPE = new NamespacedKey("mace_drill", "drill_book");

    // Renkler
    private static final int C_GOLD = 0xFFAA00;
    private static final int C_CYAN = 0x55FFFF;
    private static final int C_GRAY = 0xAAAAAA;
    private static final int C_WHITE = 0xFFFFFF;
    private static final int C_GREEN = 0x55FF55;
    private static final int C_RED = 0xFF5555;
    private static final int C_YELLOW = 0xFFFF55;
    private static final int C_PURPLE = 0xCC77FF;

    // Ana menu (45 slot)
    private static final int MAIN_SIZE = 45;
    private static final int MAIN_TITLE_SLOT = 4;
    private static final int MAIN_CRAFT_SLOT = 20;
    private static final int MAIN_SMITH_SLOT = 24;
    private static final int MAIN_HINT_SLOT = 40;

    // Tarif sayfalari (54 slot)
    private static final int[] CRAFT_GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int[] SMITH_SLOTS = {19, 20, 21}; // sablon, taban, ek
    private static final int[] SMITH_LABELS = {10, 11, 12};
    private static final int STATION_SLOT = 14;
    private static final int ARROW_SLOT = 23;
    private static final int RESULT_LABEL_SLOT = 16;
    private static final int RESULT_SLOT = 25;
    private static final int INFO_SLOT = 4;
    private static final int PREV_SLOT = 48;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    private enum Type { MAIN, CRAFT, SMITH }

    private static final class Menu implements InventoryHolder {
        final Type type;
        final int page;
        Inventory inv;

        Menu(Type type, int page) {
            this.type = type;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;

    public RecipeBook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // "bgps" on eki ile kaydedilir -> /bgps:recipe_book ve /recipe_book
        plugin.getServer().getCommandMap().register("bgps", new Command("recipe_book") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (sender instanceof Player p) {
                    openMain(p);
                    sound(p, Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.2f);
                } else {
                    sender.sendMessage("Bu komut sadece oyuncular icindir.");
                }
                return true;
            }
        });
    }

    // ------------------------------------------------------------------ menuler

    private void openMain(Player p) {
        Menu menu = new Menu(Type.MAIN, 0);
        Inventory inv = Bukkit.createInventory(menu, MAIN_SIZE,
                Component.text("✦ " + small("BGP's Vanilla+ Addon") + " ✦"));
        menu.inv = inv;
        frame(inv, Material.PURPLE_STAINED_GLASS_PANE);

        inv.setItem(MAIN_TITLE_SLOT, named(Material.NETHER_STAR, "✦ BGP's Vanilla+ Addon ✦", C_PURPLE,
                lore("Özel eşyaların tarif kitabı", C_GRAY)));

        int craftCount = collect(false).size();
        int smithCount = collect(true).size();

        inv.setItem(MAIN_CRAFT_SLOT, named(Material.CRAFTING_TABLE, "⚒ Craft Tarifleri", C_GOLD,
                lore("Çalışma masasında yapılan", C_GRAY),
                lore("özel eşyaların tarifleri", C_GRAY),
                lore(" ", C_GRAY),
                lore("★ Toplam tarif: " + craftCount, C_YELLOW),
                lore(" ", C_GRAY),
                lore("➤ Açmak için tıkla", C_GREEN)));

        inv.setItem(MAIN_SMITH_SLOT, named(Material.SMITHING_TABLE, "⚔ Demirci Masası Tarifleri", C_CYAN,
                lore("Demirci masasında birleştirilen", C_GRAY),
                lore("özel eşyaların tarifleri", C_GRAY),
                lore(" ", C_GRAY),
                lore("★ Toplam tarif: " + smithCount, C_YELLOW),
                lore(" ", C_GRAY),
                lore("➤ Açmak için tıkla", C_GREEN)));

        inv.setItem(MAIN_HINT_SLOT, named(Material.PAPER, "Bir kategori seç", C_WHITE));
        p.openInventory(inv);
    }

    private void openRecipePage(Player p, Type type, int page) {
        boolean smith = type == Type.SMITH;
        List<Recipe> list = collect(smith);
        int accent = smith ? C_CYAN : C_GOLD;
        Material border = smith ? Material.CYAN_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
        String title = smith ? "Demirci Masası Tarifleri" : "Craft Tarifleri";
        int total = list.size();
        page = Math.max(0, Math.min(page, Math.max(0, total - 1)));

        Menu menu = new Menu(type, page);
        Inventory inv = Bukkit.createInventory(menu, 54,
                Component.text((smith ? "⚔ " : "⚒ ") + small(title)));
        menu.inv = inv;
        frame(inv, border);

        inv.setItem(BACK_SLOT, named(Material.BARRIER, "✘ Geri", C_RED,
                lore("Ana menüye dön", C_GRAY)));

        if (list.isEmpty()) {
            inv.setItem(22, named(Material.BARRIER, "Tarif bulunamadı", C_RED));
            p.openInventory(inv);
            return;
        }

        inv.setItem(INFO_SLOT, named(Material.PAPER, "★ Tarif " + (page + 1) + " / " + total, C_YELLOW));
        if (page > 0) {
            inv.setItem(PREV_SLOT, named(Material.ARROW, "◀ Önceki Tarif", C_GREEN,
                    lore("Tarif " + page + " / " + total, C_GRAY)));
        }
        if (page < total - 1) {
            inv.setItem(NEXT_SLOT, named(Material.ARROW, "Sonraki Tarif ▶", C_GREEN,
                    lore("Tarif " + (page + 2) + " / " + total, C_GRAY)));
        }

        inv.setItem(STATION_SLOT, smith
                ? named(Material.SMITHING_TABLE, "Demirci Masası", C_CYAN)
                : named(Material.CRAFTING_TABLE, "Çalışma Masası", C_GOLD));
        inv.setItem(RESULT_LABEL_SLOT, pane(Material.LIME_STAINED_GLASS_PANE, "✔ Sonuç", C_GREEN));
        inv.setItem(ARROW_SLOT, named(Material.SPECTRAL_ARROW, "➤", C_WHITE));

        Recipe r = list.get(page);
        if (smith) {
            drawSmith(inv, (SmithingTransformRecipe) r);
        } else {
            drawCraft(inv, r);
        }
        p.openInventory(inv);
    }

    private void drawCraft(Inventory inv, Recipe r) {
        for (int s : CRAFT_GRID) inv.setItem(s, null);

        if (r instanceof ShapedRecipe sr) {
            String[] shape = sr.getShape();
            Map<Character, RecipeChoice> map = sr.getChoiceMap();
            for (int row = 0; row < shape.length && row < 3; row++) {
                String line = shape[row];
                for (int col = 0; col < line.length() && col < 3; col++) {
                    char ch = line.charAt(col);
                    if (ch == ' ') continue;
                    inv.setItem(CRAFT_GRID[row * 3 + col], choiceItem(map.get(ch), null));
                }
            }
        } else if (r instanceof ShapelessRecipe sl) {
            List<RecipeChoice> choices = sl.getChoiceList();
            for (int i = 0; i < choices.size() && i < 9; i++) {
                inv.setItem(CRAFT_GRID[i], choiceItem(choices.get(i), null));
            }
        }
        inv.setItem(RESULT_SLOT, r.getResult().clone());
    }

    private void drawSmith(Inventory inv, SmithingTransformRecipe r) {
        for (int s : SMITH_SLOTS) inv.setItem(s, null);
        ItemStack rainbow = recipeResult(TEMPLATE_RECIPE);
        ItemStack book = recipeResult(BOOK_RECIPE);

        inv.setItem(SMITH_LABELS[0], pane(Material.MAGENTA_STAINED_GLASS_PANE, "Şablon", C_PURPLE));
        inv.setItem(SMITH_LABELS[1], pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "Taban Eşya", C_CYAN));
        inv.setItem(SMITH_LABELS[2], pane(Material.YELLOW_STAINED_GLASS_PANE, "Ek Malzeme", C_YELLOW));

        inv.setItem(SMITH_SLOTS[0], choiceItem(r.getTemplate(), rainbow));
        inv.setItem(SMITH_SLOTS[1], choiceItem(r.getBase(), null));
        inv.setItem(SMITH_SLOTS[2], choiceItem(r.getAddition(), book));
        inv.setItem(RESULT_SLOT, r.getResult().clone());
    }

    // ------------------------------------------------------------------ tarif toplama

    private List<Recipe> collect(boolean smithing) {
        List<Recipe> out = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (!(r instanceof Keyed k)) continue;
            if (!NAMESPACES.contains(k.getKey().getNamespace())) continue;
            boolean isSmith = r instanceof SmithingTransformRecipe;
            boolean isCraft = r instanceof ShapedRecipe || r instanceof ShapelessRecipe;
            if (smithing ? isSmith : isCraft) out.add(r);
        }
        out.sort(Comparator.comparing((Recipe r) -> ((Keyed) r).getKey().toString()));
        return out;
    }

    private ItemStack recipeResult(NamespacedKey key) {
        Recipe r = Bukkit.getRecipe(key);
        return r == null ? null : r.getResult().clone();
    }

    /**
     * Tarifteki malzemeyi gosterilecek esyaya cevirir.
     * Data pack tarifleri sablon/kitap icin duz "knowledge_book" yazar; menude yaniltmamasi icin
     * knowledgeSub verilmisse onun yerine gercek ozel esya gosterilir.
     */
    private ItemStack choiceItem(RecipeChoice c, ItemStack knowledgeSub) {
        if (c == null) return null;
        ItemStack s = null;
        if (c instanceof RecipeChoice.MaterialChoice m) {
            List<Material> l = m.getChoices();
            if (!l.isEmpty()) s = new ItemStack(l.get(0));
        } else if (c instanceof RecipeChoice.ExactChoice e) {
            List<ItemStack> l = e.getChoices();
            if (!l.isEmpty()) s = l.get(0).clone();
        }
        if (s != null && s.getType() == Material.KNOWLEDGE_BOOK && knowledgeSub != null) {
            return knowledgeSub.clone();
        }
        return s;
    }

    // ------------------------------------------------------------------ olaylar

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true); // hicbir esya alinamaz / yerlestirilemez
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int slot = e.getRawSlot();
        switch (menu.type) {
            case MAIN -> {
                if (slot == MAIN_CRAFT_SLOT) {
                    sound(p, Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    openRecipePage(p, Type.CRAFT, 0);
                } else if (slot == MAIN_SMITH_SLOT) {
                    sound(p, Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    openRecipePage(p, Type.SMITH, 0);
                }
            }
            case CRAFT, SMITH -> {
                if (slot == BACK_SLOT) {
                    sound(p, Sound.UI_BUTTON_CLICK, 0.8f, 0.9f);
                    openMain(p);
                } else if (slot == PREV_SLOT && menu.page > 0) {
                    sound(p, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
                    openRecipePage(p, menu.type, menu.page - 1);
                } else if (slot == NEXT_SLOT) {
                    int total = collect(menu.type == Type.SMITH).size();
                    if (menu.page < total - 1) {
                        sound(p, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
                        openRecipePage(p, menu.type, menu.page + 1);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ yardimcilar

    private static void sound(Player p, Sound s, float vol, float pitch) {
        p.playSound(p.getLocation(), s, vol, pitch);
    }

    /** Ic kismi siyah, kenarlari (ust/alt satir + sol/sag sutun) renkli cam panel ile doldurur. */
    private static void frame(Inventory inv, Material accent) {
        int rows = inv.getSize() / 9;
        ItemStack bg = pane(Material.BLACK_STAINED_GLASS_PANE, " ", C_WHITE);
        ItemStack edge = pane(accent, " ", C_WHITE);
        for (int i = 0; i < inv.getSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            boolean isEdge = row == 0 || row == rows - 1 || col == 0 || col == 8;
            inv.setItem(i, (isEdge ? edge : bg).clone());
        }
    }

    private static ItemStack pane(Material mat, String name, int rgb) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, rgb));
        item.setItemMeta(meta);
        return item;
    }

    private static Component text(String s, int rgb) {
        return Component.text(small(s), TextColor.color(rgb)).decoration(TextDecoration.ITALIC, false);
    }

    private static Component lore(String s, int rgb) {
        return text(s, rgb);
    }

    private static ItemStack named(Material mat, String name, int rgb, Component... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, rgb));
        if (lore.length > 0) meta.lore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Turkce kucuk buyuk harf (small caps) cevirici.
     * - Once Turkce kurallarina gore kucuk harfe cevrilir (I -> ı, İ -> i).
     * - ı (noktasiz) -> "ɪ", i (noktali) -> "i" kalir; boylece iki harf birbirine karismaz.
     * - s, x ve ç ö ü ş ğ harfleri mevcut eklentideki yazimla ayni sekilde oldugu gibi birakilir.
     */
    static String small(String s) {
        String l = s.toLowerCase(Locale.forLanguageTag("tr"));
        StringBuilder b = new StringBuilder(l.length());
        for (char c : l.toCharArray()) {
            b.append(switch (c) {
                case 'a' -> 'ᴀ';
                case 'b' -> 'ʙ';
                case 'c' -> 'ᴄ';
                case 'd' -> 'ᴅ';
                case 'e' -> 'ᴇ';
                case 'f' -> 'ꜰ';
                case 'g' -> 'ɢ';
                case 'h' -> 'ʜ';
                case 'ı' -> 'ɪ';
                case 'j' -> 'ᴊ';
                case 'k' -> 'ᴋ';
                case 'l' -> 'ʟ';
                case 'm' -> 'ᴍ';
                case 'n' -> 'ɴ';
                case 'o' -> 'ᴏ';
                case 'p' -> 'ᴘ';
                case 'q' -> 'ǫ';
                case 'r' -> 'ʀ';
                case 't' -> 'ᴛ';
                case 'u' -> 'ᴜ';
                case 'v' -> 'ᴠ';
                case 'w' -> 'ᴡ';
                case 'y' -> 'ʏ';
                case 'z' -> 'ᴢ';
                default -> c;
            });
        }
        return b.toString();
    }
}
