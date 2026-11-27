package pl.bracia.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class Items {

    public static final String GRAPLER = "grapler";
    public static final String BRATA = "brata";

    private final BraciaItems plugin;
    private final NamespacedKey key;

    public Items(BraciaItems plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "item_id");
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack grapler() {
        double range = plugin.getConfig().getDouble("grapler.range", 10.0);
        int cd = plugin.getConfig().getInt("grapler.cooldown-seconds", 60);

        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Grapler", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Przyciaga na " + (int) range + " kratek", NamedTextColor.GRAY),
                line("Cooldown: " + cd + "s", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, GRAPLER);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack brata() {
        var cfg = plugin.getConfig();
        Material mat = Material.matchMaterial(cfg.getString("brata.material", "NETHERITE_SWORD"));
        if (mat == null) {
            mat = Material.NETHERITE_SWORD;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Miecz Brata", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        meta.addEnchant(Enchantment.LOOTING, 4, true);

        meta.lore(List.of(
                line("Trafiony swieci przez " + cfg.getInt("brata.glowing-seconds", 30) + "s", NamedTextColor.GRAY),
                line("i dostaje Poison " + cfg.getInt("brata.poison-level", 2)
                        + " na " + cfg.getInt("brata.poison-seconds", 10) + "s", NamedTextColor.GRAY),
                line("Cooldown: " + cfg.getInt("brata.cooldown-seconds", 120) + "s", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, BRATA);
        item.setItemMeta(meta);
        return item;
    }

    /** Zwraca id naszego itemu (grapler/brata) albo null. */
    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public void tag(Entity entity, String id) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
    }

    public String entityId(Entity entity) {
        return entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
