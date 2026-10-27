package tr.gurz.guard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

    // Slotlar
    private static final int[] CRAFT_GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int[] SMITH_SLOTS = {19, 20, 21}; // sablon, taban, ek
    private static final int ARROW_SLOT = 23;
    private static final int RESULT_SLOT = 25;
    private static final int INFO_SLOT = 4;
    private static final int PREV_SLOT = 48;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int MAIN_CRAFT_SLOT = 11;
    private static final int MAIN_SMITH_SLOT = 15;

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
        Inventory inv = Bukkit.createInventory(menu, 27, Component.text("BGP's Tarif Kitabı"));
        menu.inv = inv;
        fill(inv);
        inv.setItem(MAIN_CRAFT_SLOT, named(Material.CRAFTING_TABLE, "Craft Tarifleri", NamedTextColor.GOLD,
                "Çalışma masasında yapılan eşyalar"));
        inv.setItem(MAIN_SMITH_SLOT, named(Material.SMITHING_TABLE, "Demirci Masası Tarifleri", NamedTextColor.AQUA,
                "Demirci masasında birleştirilen eşyalar"));
        p.openInventory(inv);
    }

    private void openRecipePage(Player p, Type type, int page) {
        List<Recipe> list = collect(type == Type.SMITH);
        String title = type == Type.SMITH ? "Demirci Masası Tarifleri" : "Craft Tarifleri";
        int total = list.size();
        page = Math.max(0, Math.min(page, Math.max(0, total - 1)));

        Menu menu = new Menu(type, page);
        Inventory inv = Bukkit.createInventory(menu, 54, Component.text(title));
        menu.inv = inv;
        fill(inv);
        inv.setItem(BACK_SLOT, named(Material.BARRIER, "Geri", NamedTextColor.RED, "Ana menüye dön"));

        if (list.isEmpty()) {
            inv.setItem(22, named(Material.BARRIER, "Tarif bulunamadı", NamedTextColor.RED));
            p.openInventory(inv);
            return;
        }

        inv.setItem(INFO_SLOT, named(Material.PAPER, "Tarif " + (page + 1) + " / " + total, NamedTextColor.YELLOW));
        if (page > 0) {
            inv.setItem(PREV_SLOT, named(Material.ARROW, "◀ Önceki Tarif", NamedTextColor.GREEN));
        }
        if (page < total - 1) {
            inv.setItem(NEXT_SLOT, named(Material.ARROW, "Sonraki Tarif ▶", NamedTextColor.GREEN));
        }

        Recipe r = list.get(page);
        if (type == Type.CRAFT) {
            drawCraft(inv, r);
        } else {
            drawSmith(inv, (SmithingTransformRecipe) r);
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
        inv.setItem(ARROW_SLOT, named(Material.SPECTRAL_ARROW, "→", NamedTextColor.WHITE));
        inv.setItem(RESULT_SLOT, r.getResult().clone());
    }

    private void drawSmith(Inventory inv, SmithingTransformRecipe r) {
        for (int s : SMITH_SLOTS) inv.setItem(s, null);
        ItemStack rainbow = recipeResult(TEMPLATE_RECIPE);
        ItemStack book = recipeResult(BOOK_RECIPE);
        inv.setItem(SMITH_SLOTS[0], choiceItem(r.getTemplate(), rainbow));
        inv.setItem(SMITH_SLOTS[1], choiceItem(r.getBase(), null));
        inv.setItem(SMITH_SLOTS[2], choiceItem(r.getAddition(), book));
        inv.setItem(ARROW_SLOT, named(Material.SPECTRAL_ARROW, "→", NamedTextColor.WHITE));
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
                if (slot == MAIN_CRAFT_SLOT) openRecipePage(p, Type.CRAFT, 0);
                else if (slot == MAIN_SMITH_SLOT) openRecipePage(p, Type.SMITH, 0);
            }
            case CRAFT, SMITH -> {
                if (slot == BACK_SLOT) {
                    openMain(p);
                } else if (slot == PREV_SLOT && menu.page > 0) {
                    openRecipePage(p, menu.type, menu.page - 1);
                } else if (slot == NEXT_SLOT) {
                    int total = collect(menu.type == Type.SMITH).size();
                    if (menu.page < total - 1) openRecipePage(p, menu.type, menu.page + 1);
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

    private static void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane.clone());
    }

    private static ItemStack named(Material mat, String name, NamedTextColor color, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(l);
        }
        item.setItemMeta(meta);
        return item;
    }
}
