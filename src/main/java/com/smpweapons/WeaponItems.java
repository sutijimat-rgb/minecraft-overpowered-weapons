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
    public static final String SWORD = "sword";

    private final SmpWeaponsPlugin plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey idKey;

    public WeaponItems(SmpWeaponsPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "weapon_type");
        this.idKey = new NamespacedKey(plugin, "weapon_id");
    }

    /** The unique id stamped on a mace/spear, or null for an item without one. */
    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    /** Returns a copy of the item stamped with the given unique id. */
    public ItemStack withId(ItemStack item, String id) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Returns "mace", "spear" or null if the item is not one of our weapons. */
    public String typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public ItemStack createMace() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Shit Mace", NamedTextColor.RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Infinite reach: left-click anything in sight."),
                line("Resistance IV while it is in your inventory and you are healthy.")));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        // Enchantments come from config (default: Wind Burst II, Breach IV, Density V).
        // The "true" lets us go past the vanilla conflict and level rules.
        var enchants = plugin.getConfig().getConfigurationSection("mace.enchantments");
        java.util.Map<String, Integer> wanted = new java.util.LinkedHashMap<>();
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) wanted.put(key, enchants.getInt(key, 0));
        } else {
            // Old saved config without this section: use the defaults.
            wanted.put("wind_burst", 2);
            wanted.put("breach", 4);
            wanted.put("density", 5);
        }
        for (var entry : wanted.entrySet()) {
            if (entry.getValue() <= 0) continue;
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(entry.getKey().toLowerCase()));
            if (enchantment == null) {
                plugin.getLogger().warning("Unknown enchantment in config: " + entry.getKey());
                continue;
            }
            meta.addEnchant(enchantment, entry.getValue(), true);
        }

        meta.setItemModel(NamespacedKey.minecraft("dirt"));   // looks like a dirt block
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
        meta.displayName(Component.text("Shit Spear", NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Right-click: lunge dash."),
                line("Shift + right-click: orbital NUKE."),
                line("Shift + left-click: orbital STAB."),
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

        meta.setItemModel(NamespacedKey.minecraft("dirt"));   // looks like a dirt block
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, SPEAR);
        item.setItemMeta(meta);
        return item;
    }

    /** The "shit sword": a netherite sword with huge enchants that looks like a dirt block. */
    public ItemStack createSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Shit Sword", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(line("Speed III and Strength III while you hold it.")));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        var enchants = plugin.getConfig().getConfigurationSection("sword.enchants");
        java.util.Map<String, Integer> wanted = new java.util.LinkedHashMap<>();
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) wanted.put(key, enchants.getInt(key, 0));
        } else {
            wanted.put("sharpness", 6);
            wanted.put("fire_aspect", 3);
            wanted.put("looting", 5);
            wanted.put("sweeping_edge", 4);
            wanted.put("mending", 1);
        }
        for (var entry : wanted.entrySet()) {
            if (entry.getValue() <= 0) continue;
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(entry.getKey().toLowerCase()));
            if (enchantment == null) {
                plugin.getLogger().warning("Unknown enchantment in config: " + entry.getKey());
                continue;
            }
            meta.addEnchant(enchantment, entry.getValue(), true);
        }

        meta.setItemModel(NamespacedKey.minecraft("dirt"));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, SWORD);
        item.setItemMeta(meta);
        return item;
    }

    /** Name shown in death messages, or null if the item is not one of our weapons. */
    public static String deathName(String type) {
        if (MACE.equals(type)) return "Shit Mace";
        if (SPEAR.equals(type)) return "Shit Spear";
        if (SWORD.equals(type)) return "Shit Sword";
        return null;
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
