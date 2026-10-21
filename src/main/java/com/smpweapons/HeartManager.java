package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Lifesteal hearts, stored on the player and applied as max health. */
public final class HeartManager {

    private final SmpWeaponsPlugin plugin;
    private final NamespacedKey heartsKey;
    private final NamespacedKey itemKey;

    public HeartManager(SmpWeaponsPlugin plugin) {
        this.plugin = plugin;
        this.heartsKey = new NamespacedKey(plugin, "hearts");
        this.itemKey = new NamespacedKey(plugin, "heart_item");
    }

    public int maxHearts() {
        return plugin.getConfig().getInt("smp.max-hearts", 20);
    }

    public int get(Player player) {
        Integer v = player.getPersistentDataContainer().get(heartsKey, PersistentDataType.INTEGER);
        return v == null ? plugin.getConfig().getInt("smp.starting-hearts", 10) : v;
    }

    public void set(Player player, int hearts) {
        hearts = Math.max(0, Math.min(maxHearts(), hearts));
        player.getPersistentDataContainer().set(heartsKey, PersistentDataType.INTEGER, hearts);
        apply(player);
    }

    /** Writes the heart count into the player's max health. */
    public void apply(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) return;
        max.setBaseValue(Math.max(2, get(player) * 2));
        if (player.getHealth() > max.getValue()) player.setHealth(max.getValue());
    }

    public ItemStack heartItem(int amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Heart", NamedTextColor.RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click to gain a heart.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isHeartItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }
}
