package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class WeaponItems {

    public static final String MACE = "mace";
    public static final String SPEAR = "spear";

    private final SmpWeaponsPlugin plugin;
    private final NamespacedKey typeKey;

    public WeaponItems(SmpWeaponsPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "weapon_type");
    }

    /** Returns "mace", "spear" or null if the item is not one of our weapons. */
    public String typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public ItemStack createMace() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Infinite Mace", NamedTextColor.RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Infinite reach: left-click anything in sight."),
                line("Resistance while you are healthy.")));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, MACE);
        item.setItemMeta(meta);
        return item;
    }

    /** Returns null if the configured spear material does not exist on this server. */
    public ItemStack createSpear() {
        String name = plugin.getConfig().getString("spear.material", "NETHERITE_SPEAR");
        Material material = Material.matchMaterial(name);
        if (material == null) return null;

        var cfg = plugin.getConfig();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Orbital Spear", NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Right-click: lunge dash (no cooldown)."),
                line("Sneak + right-click: TNT orbital cannon."),
                line("Light-speed attacks.")));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        // Setting any attribute modifier replaces the item's default ones,
        // so damage and attack speed are added explicitly here.
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                new NamespacedKey(plugin, "spear_damage"),
                cfg.getDouble("spear.attack-damage-bonus", 12.0),
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                new NamespacedKey(plugin, "spear_attack_speed"),
                cfg.getDouble("spear.attack-speed-bonus", 2.0),
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, new AttributeModifier(
                new NamespacedKey(plugin, "spear_move_speed"),
                cfg.getDouble("spear.movement-speed-scalar", 0.25),
                AttributeModifier.Operation.ADD_SCALAR,
                EquipmentSlotGroup.MAINHAND));

        int lungeLevel = cfg.getInt("spear.lunge-level", 3);
        if (lungeLevel > 0) {
            Enchantment lunge = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("lunge"));
            if (lunge != null) meta.addEnchant(lunge, lungeLevel, true);
        }

        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, SPEAR);
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
