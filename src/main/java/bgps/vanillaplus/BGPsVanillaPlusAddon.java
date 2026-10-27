package bgps.vanillaplus;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Demirci masasi denetcisi.
 * Data pack'teki smithing tarifleri sadece esya TURUNE bakar; bu eklenti
 * esyanin gercekte ne oldugunu (item_model, hasar, lore) kontrol edip
 * gecersiz birlestirmeleri iptal eder.
 */
public final class BGPsVanillaPlusAddon extends JavaPlugin implements Listener {

    // ---- Ayarlar: yeni ozel esya eklerken buraya satir ekle ----
    private static final String TEMPLATE_MODEL = "mace_drill:rainbow_template"; // Gelistirilmis Yukseltme Sablonu
    private static final String BOOK_MODEL = "minecraft:enchanted_book";        // Ekskavator buyu kitabi
    private static final String DRILL_LORE_MARK = "ᴇᴋsᴋᴀᴠᴀᴛöʀ";                  // ekskavatorlu gurzun lore'unda gecer

    private static final String MSG_DAMAGED = "ʜᴀsᴀʀʟɪ ᴇʟiᴛʀᴀ ᴋᴀʙᴜʟ ᴇᴅiʟᴍᴇᴢ! ᴇʟiᴛʀᴀ ᴛᴀᴍ ᴄᴀɴʟɪ ᴏʟᴍᴀʟɪ.";
    private static final String MSG_WRONG = "ʟüᴛꜰᴇɴ ᴇşʏᴀʟᴀʀɪ ᴅᴏğʀᴜ sɪʀᴀʏʟᴀ ʏᴇʀʟᴇşᴛiʀ.";
    private static final String ELYTRA_LORE_MARK = "ᴇʟiᴛʀᴀ ɢüçʟᴇɴᴅiʀᴍᴇsi";                // zirhli elitra gogusluk lore'unda gecer
    private static final String MSG_ELYTRA_HAS = "ʙᴜ ɢöğüsʟüᴋᴛᴇ ᴢᴀᴛᴇɴ ᴇʟiᴛʀᴀ ɢüçʟᴇɴᴅiʀᴍᴇsi ᴠᴀʀ!";
    private static final String MSG_WRONG_TEMPLATE = "ʟüᴛꜰᴇɴ ᴅᴏğʀᴜ ᴇşʏᴀʟᴀʀɪ ᴋᴜʟʟᴀɴᴅɪğɪɴᴅᴀɴ ᴇᴍiɴ ᴏʟ.";
    private static final String FULL_HP_MARK = "3500 / 3500";                     // dolu can (lore)
    private static final String MSG_NOT_DRILL = "sᴀᴅᴇᴄᴇ ᴇᴋsᴋᴀᴠᴀᴛöʀ ɢüʀᴢüɴ ᴄᴀɴɪ ʏᴇɴiʟᴇɴᴇʙiʟiʀ!";
    private static final String MSG_FULL = "ᴇᴋsᴋᴀᴠᴀᴛöʀ ᴄᴀɴɪ ᴢᴀᴛᴇɴ ᴅᴏʟᴜ!";
    private static final String MSG_ALREADY = "ʙᴜ ɢüʀᴢᴅᴇ ᴢᴀᴛᴇɴ ᴇᴋsᴋᴀᴠᴀᴛöʀ ᴠᴀʀ!";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        new RecipeBook(this).register();
        getLogger().info("BGP's Vanilla+ Addon aktif.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (event.getResult() == null) return;
        SmithingInventory inv = event.getInventory();
        ItemStack template = inv.getInputTemplate();
        ItemStack base = inv.getInputEquipment();
        ItemStack addition = inv.getInputMineral();
        if (template == null || base == null || addition == null) return;

        // 1) Zirhli elitra: sablon (gokkusagi) + gogusluk + elitra
        if (template.getType() == Material.KNOWLEDGE_BOOK
                && addition.getType() == Material.ELYTRA
                && base.getType().name().endsWith("_CHESTPLATE")) {
            if (!hasModel(template, TEMPLATE_MODEL)) {
                reject(event, MSG_WRONG_TEMPLATE);
                return;
            }
            ItemMeta meta = addition.getItemMeta();
            if (meta instanceof Damageable d && d.getDamage() > 0) {
                reject(event, MSG_DAMAGED);
                return;
            }
            if (hasLoreMark(base, ELYTRA_LORE_MARK)) {
                reject(event, MSG_ELYTRA_HAS);
            }
            return;
        }

        // 2) Ekskavator can yenileme: vanilla netherite sablonu + ekskavator gurz + ghast gozyasi
        if (base.getType() == Material.MACE
                && template.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                && addition.getType() == Material.GHAST_TEAR) {
            if (!hasLoreMark(base, DRILL_LORE_MARK)) {
                reject(event, MSG_NOT_DRILL);
                return;
            }
            if (hasLoreMark(base, FULL_HP_MARK)) {
                reject(event, MSG_FULL);
            }
            return;
        }

        // 3) Ekskavator: sablon + gurz + buyu kitabi (ikisi de knowledge_book)
        if (base.getType() == Material.MACE
                && template.getType() == Material.KNOWLEDGE_BOOK
                && addition.getType() == Material.KNOWLEDGE_BOOK) {
            if (!hasModel(template, TEMPLATE_MODEL) || !hasModel(addition, BOOK_MODEL)) {
                reject(event, MSG_WRONG);
                return;
            }
            if (hasLoreMark(base, DRILL_LORE_MARK)) {
                reject(event, MSG_ALREADY);
            }
        }
    }

    private static boolean hasModel(ItemStack item, String model) {
        Key key = item.getData(DataComponentTypes.ITEM_MODEL);
        return key != null && key.asString().equals(model);
    }

    private static boolean hasLoreMark(ItemStack item, String mark) {
        List<Component> lore = item.lore();
        if (lore == null) return false;
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        for (Component line : lore) {
            if (plain.serialize(line).contains(mark)) return true;
        }
        return false;
    }

    private static void reject(PrepareSmithingEvent event, String message) {
        event.setResult(null);
        for (HumanEntity viewer : event.getViewers()) {
            if (viewer instanceof Player p) {
                p.sendActionBar(Component.text(message, NamedTextColor.RED));
            }
        }
    }
}
