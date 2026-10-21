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

/** The kill-upgraded spear and mace (tier 1 to 5). Tier 5 + enough kills can become the God weapon. */
public final class TierItems {

    /** Kills needed to reach tier 1..5. */
    private static final int[] NEEDED = {0, 3, 7, 12, 18};
    private static final NamedTextColor[] COLORS = {
            NamedTextColor.GRAY, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    private final SmpWeaponsPlugin plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey killsKey;

    public TierItems(SmpWeaponsPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "tier_type");
        this.killsKey = new NamespacedKey(plugin, "tier_kills");
    }

    public int godKills() {
        return plugin.getConfig().getInt("smp.god-kills", 25);
    }

    /** "mace", "spear", or null if this is not a tiered weapon. */
    public String typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public int killsOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(killsKey, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static int tierFor(int kills) {
        int tier = 1;
        for (int i = 0; i < NEEDED.length; i++) {
            if (kills >= NEEDED[i]) tier = i + 1;
        }
        return tier;
    }

    /** Returns a new item with one more kill (and the matching tier), or null if the spear material is missing. */
    public ItemStack withOneMoreKill(ItemStack item) {
        return create(typeOf(item), killsOf(item) + 1);
    }

    public ItemStack create(String type, int kills) {
        int tier = tierFor(kills);
        boolean mace = WeaponItems.MACE.equals(type);
        Material material = mace ? Material.MACE
                : Material.matchMaterial(plugin.getConfig().getString("spear.material", "NETHERITE_SPEAR"));
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String base = mace ? "Mace" : "Spear";
        meta.displayName(Component.text("Tier " + ROMAN[tier - 1] + " " + base, COLORS[tier - 1], TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        String next = tier >= NEEDED.length
                ? "Reach " + godKills() + " kills for the God " + base + "."
                : "Next tier at " + NEEDED[tier] + " kills.";
        meta.lore(List.of(
                Component.text("Kills: " + kills, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(next, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));

        if (mace) {
            enchant(meta, "density", tier);
            enchant(meta, "breach", Math.min(tier, 4));
            enchant(meta, "wind_burst", tier >= 4 ? 2 : (tier >= 2 ? 1 : 0));
        } else {
            enchant(meta, "sharpness", tier);
            enchant(meta, "lunge", Math.min(tier, 3));
            // Explicit modifiers replace the item's defaults, so damage and speed are added here.
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, "tier_damage"), 3.0 + 2.0 * tier,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, "tier_speed"), 1.0 + 0.2 * tier,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        if (tier >= NEEDED.length) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, mace ? WeaponItems.MACE : WeaponItems.SPEAR);
        meta.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, kills);
        item.setItemMeta(meta);
        return item;
    }

    private static void enchant(ItemMeta meta, String name, int level) {
        if (level <= 0) return;
        Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));
        if (e != null) meta.addEnchant(e, level, true);
    }
}
