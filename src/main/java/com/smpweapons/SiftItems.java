package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/** Siftite gear, Ichor items and the Rift Key. */
public final class SiftItems {

    public static final String SIFTITE = "siftite";
    public static final String ICHOR_BOTTLE = "ichor_bottle";
    public static final String ICHOR_BUCKET = "ichor_bucket";
    public static final String ICHOR_SNOWBALL = "ichor_snowball";
    public static final String RIFT_KEY = "rift_key";

    private final NamespacedKey key;

    public SiftItems(SmpWeaponsPlugin plugin) {
        this.key = new NamespacedKey(plugin, "sift_item");
    }

    /** Which Sift item this is (see the constants), or null. */
    public String kindOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private ItemStack build(Material material, int amount, String name, NamedTextColor color, String kind,
                            boolean unbreakable, String lore, String... enchants) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        if (lore != null) {
            meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        }
        for (String entry : enchants) {
            String[] parts = entry.split(":");
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(parts[0]));
            if (enchantment != null) meta.addEnchant(enchantment, Integer.parseInt(parts[1]), true);
        }
        if (unbreakable) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, kind);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack siftite(Material material, String name, String... enchants) {
        return build(material, 1, name, NamedTextColor.DARK_AQUA, SIFTITE, true,
                "Unbreakable. Returns to you after death.", enchants);
    }

    /** The full Siftite set: six tools/weapons and four armor pieces. */
    public List<ItemStack> siftiteSet() {
        List<ItemStack> set = new ArrayList<>();
        set.add(siftite(Material.NETHERITE_SWORD, "Siftite Sword",
                "sharpness:6", "sweeping_edge:4", "looting:4", "fire_aspect:2"));
        set.add(siftite(Material.NETHERITE_PICKAXE, "Siftite Pickaxe", "efficiency:7", "fortune:4"));
        set.add(siftite(Material.NETHERITE_AXE, "Siftite Axe", "efficiency:7", "sharpness:6"));
        set.add(siftite(Material.NETHERITE_SHOVEL, "Siftite Shovel", "efficiency:7"));
        set.add(siftite(Material.NETHERITE_HOE, "Siftite Hoe", "efficiency:7", "fortune:4"));
        Material spearMaterial = Material.matchMaterial("NETHERITE_SPEAR");
        if (spearMaterial != null) {
            set.add(siftite(spearMaterial, "Siftite Spear", "lunge:3", "sharpness:6"));
        }
        set.add(siftite(Material.NETHERITE_HELMET, "Siftite Helmet",
                "protection:5", "respiration:3", "aqua_affinity:1"));
        set.add(siftite(Material.NETHERITE_CHESTPLATE, "Siftite Chestplate", "protection:5"));
        set.add(siftite(Material.NETHERITE_LEGGINGS, "Siftite Leggings", "protection:5", "swift_sneak:3"));
        set.add(siftite(Material.NETHERITE_BOOTS, "Siftite Boots",
                "protection:5", "feather_falling:5", "depth_strider:3"));
        return set;
    }

    public ItemStack ichorBottle(int amount) {
        ItemStack item = build(Material.POTION, amount, "Ichor Bottle", NamedTextColor.GOLD, ICHOR_BOTTLE, false,
                "Drink to heal: Regeneration II.");
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setColor(Color.fromRGB(255, 190, 60));
        meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 20, 1), true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack ichorBucket() {
        return build(Material.WATER_BUCKET, 1, "Ichor Bucket", NamedTextColor.GOLD, ICHOR_BUCKET, false,
                "Right-click to pour a healing pool.");
    }

    public ItemStack ichorSnowball(int amount) {
        return build(Material.SNOWBALL, amount, "Ichor Snowball", NamedTextColor.GOLD, ICHOR_SNOWBALL, false,
                "Throw it to make a small healing pool.");
    }

    public ItemStack riftKey() {
        return build(Material.ECHO_SHARD, 1, "Rift Key", NamedTextColor.LIGHT_PURPLE, RIFT_KEY, false,
                "Right-click to open a Rift for 5 minutes.");
    }
}
