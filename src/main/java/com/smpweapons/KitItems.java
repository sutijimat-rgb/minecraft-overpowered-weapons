package com.smpweapons;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/** Maxed-out diamond gear and essentials, given by /smpweapons kit. */
public final class KitItems {

    private KitItems() {}

    /** Builds an unbreakable item. Each enchant is written as "name:level". */
    private static ItemStack gear(Material material, String... enchants) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        for (String entry : enchants) {
            String[] parts = entry.split(":");
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(parts[0]));
            if (enchantment != null) {
                meta.addEnchant(enchantment, Integer.parseInt(parts[1]), true);
            }
        }
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public static void give(Player player) {
        // Armor: worn straight away if the slot is empty, otherwise goes in the inventory.
        equipOrGive(player, EquipmentSlot.HEAD, gear(Material.DIAMOND_HELMET,
                "protection:4", "unbreaking:3", "mending:1", "respiration:3", "aqua_affinity:1"));
        equipOrGive(player, EquipmentSlot.CHEST, gear(Material.DIAMOND_CHESTPLATE,
                "protection:4", "unbreaking:3", "mending:1", "thorns:3"));
        equipOrGive(player, EquipmentSlot.LEGS, gear(Material.DIAMOND_LEGGINGS,
                "protection:4", "unbreaking:3", "mending:1", "swift_sneak:3"));
        equipOrGive(player, EquipmentSlot.FEET, gear(Material.DIAMOND_BOOTS,
                "protection:4", "unbreaking:3", "mending:1", "feather_falling:4", "depth_strider:3"));

        // Weapons and tools.
        add(player, gear(Material.DIAMOND_SWORD,
                "sharpness:5", "looting:3", "fire_aspect:2", "sweeping_edge:3", "knockback:2",
                "unbreaking:3", "mending:1"));
        add(player, gear(Material.DIAMOND_AXE,
                "sharpness:5", "efficiency:5", "unbreaking:3", "mending:1"));
        add(player, gear(Material.DIAMOND_PICKAXE,
                "efficiency:5", "fortune:3", "unbreaking:3", "mending:1"));
        add(player, gear(Material.DIAMOND_SHOVEL,
                "efficiency:5", "fortune:3", "unbreaking:3", "mending:1"));
        add(player, gear(Material.DIAMOND_HOE,
                "efficiency:5", "fortune:3", "unbreaking:3", "mending:1"));
        add(player, gear(Material.BOW,
                "power:5", "punch:2", "flame:1", "infinity:1", "unbreaking:3", "mending:1"));
        add(player, gear(Material.SHIELD, "unbreaking:3", "mending:1"));

        // Potions: one of each drinkable, plus splash healing for fights.
        add(player, potion(Material.POTION, PotionType.STRONG_STRENGTH));
        add(player, potion(Material.POTION, PotionType.STRONG_SWIFTNESS));
        add(player, potion(Material.POTION, PotionType.STRONG_REGENERATION));
        add(player, potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE));
        add(player, potion(Material.POTION, PotionType.NIGHT_VISION));
        add(player, potion(Material.POTION, PotionType.WATER_BREATHING));
        for (int i = 0; i < 8; i++) {
            add(player, potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING));
        }

        // Essentials.
        add(player, new ItemStack(Material.ARROW, 1));
        add(player, new ItemStack(Material.COOKED_BEEF, 64));
        add(player, new ItemStack(Material.GOLDEN_APPLE, 32));
        add(player, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8));
        add(player, new ItemStack(Material.ENDER_PEARL, 16));
        add(player, new ItemStack(Material.WATER_BUCKET, 1));
        add(player, new ItemStack(Material.TOTEM_OF_UNDYING, 1));
    }

    private static ItemStack potion(Material material, PotionType type) {
        ItemStack item = new ItemStack(material);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    private static void equipOrGive(Player player, EquipmentSlot slot, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir()) {
            inventory.setItem(slot, item);
        } else {
            add(player, item);
        }
    }

    private static void add(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }
}
