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

import java.util.ArrayList;
import java.util.List;

/**
 * The kill-upgraded mace, spear and sword: tiers 1 to 13. The 14th level is the God weapon
 * (God Mace / God Spear), earned at the last kill count if nobody holds it yet.
 *
 * Effects by tier (all stack as you level up):
 *  passive (while held): T2 Speed I, T4 Jump Boost, T6 Speed II, T7 Resistance I, T8 Fire Resistance,
 *                        T9 Strength I, T11 Resistance II + Regeneration, T13 Strength II + Health Boost
 *  on hit:   bonus damage every tier, T3 Slowness, T5 lifesteal, T6 sets on fire, T8 Weakness,
 *            T10 Wither, T12 stronger lifesteal, T13 lightning
 *  abilities: T4 Right-click Leap (mace) / Dash (spear, sword), T8 Shift+right-click Shockwave,
 *             T12 Shift+left-click Blink
 */
public final class TierItems {

    public static final int MAX_TIER = 13;

    /** Kills needed to reach tier 1..13; the 14th entry is the God weapon. */
    private static final int[] NEEDED = {0, 2, 4, 6, 9, 12, 16, 20, 25, 30, 36, 43, 50, 60};
    private static final NamedTextColor[] COLORS = {
            NamedTextColor.GRAY, NamedTextColor.WHITE, NamedTextColor.GREEN, NamedTextColor.DARK_GREEN,
            NamedTextColor.AQUA, NamedTextColor.DARK_AQUA, NamedTextColor.BLUE, NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.DARK_PURPLE, NamedTextColor.YELLOW, NamedTextColor.GOLD, NamedTextColor.RED,
            NamedTextColor.DARK_RED};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII"};

    private final SmpWeaponsPlugin plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey killsKey;

    public TierItems(SmpWeaponsPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "tier_type");
        this.killsKey = new NamespacedKey(plugin, "tier_kills");
    }

    /** Kills needed for the 14th level, the God weapon. */
    public int godKills() {
        return plugin.getConfig().getInt("smp.god-tier-kills", NEEDED[NEEDED.length - 1]);
    }

    /** "mace", "spear", "sword", or null if this is not a tiered weapon. */
    public String typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public int killsOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(killsKey, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** The tier (1 to 13) of a tiered weapon, or 0 if the item is not one. */
    public int tierOf(ItemStack item) {
        return typeOf(item) == null ? 0 : tierFor(killsOf(item));
    }

    public static int tierFor(int kills) {
        int tier = 1;
        for (int i = 0; i < MAX_TIER; i++) {
            if (kills >= NEEDED[i]) tier = i + 1;
        }
        return tier;
    }

    /** Returns a new item with one more kill (and the matching tier). */
    public ItemStack withOneMoreKill(ItemStack item) {
        return create(typeOf(item), killsOf(item) + 1);
    }

    public ItemStack create(String type, int kills) {
        int tier = tierFor(kills);
        boolean mace = WeaponItems.MACE.equals(type);
        boolean sword = WeaponItems.SWORD.equals(type);
        Material material = mace ? Material.MACE
                : sword ? Material.NETHERITE_SWORD
                : Material.matchMaterial(plugin.getConfig().getString("spear.material", "NETHERITE_SPEAR"));
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String base = mace ? "Mace" : (sword ? "Sword" : "Spear");
        meta.displayName(Component.text("Tier " + ROMAN[tier - 1] + " " + base, COLORS[tier - 1], TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore(base, mace, kills, tier));

        // Enchants grow with the tier.
        enchant(meta, "unbreaking", Math.min(3, tier / 4));
        enchant(meta, "mending", tier >= 8 ? 1 : 0);
        if (mace) {
            enchant(meta, "density", tier / 2 + 1);
            enchant(meta, "breach", Math.min(5, 1 + tier / 3));
            enchant(meta, "wind_burst", tier >= 11 ? 3 : (tier >= 7 ? 2 : (tier >= 3 ? 1 : 0)));
            enchant(meta, "fire_aspect", tier >= 10 ? 2 : (tier >= 6 ? 1 : 0));
        } else {
            enchant(meta, "sharpness", tier);
            enchant(meta, "fire_aspect", tier >= 10 ? 2 : (tier >= 6 ? 1 : 0));
            enchant(meta, "looting", tier >= 13 ? 3 : (tier >= 9 ? 2 : (tier >= 5 ? 1 : 0)));
            if (sword) {
                enchant(meta, "sweeping_edge", Math.min(3, 1 + tier / 4));
                enchant(meta, "knockback", tier >= 7 ? 1 : 0);
            } else {
                enchant(meta, "lunge", tier >= 9 ? 3 : (tier >= 6 ? 2 : (tier >= 3 ? 1 : 0)));
                // Explicit modifiers replace the item's defaults, so damage and speed are added here.
                meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                        new NamespacedKey(plugin, "tier_damage"), 3.0 + 1.5 * tier,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                        new NamespacedKey(plugin, "tier_speed"), 1.0 + 0.1 * tier,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            }
        }
        if (tier >= 10) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING,
                mace ? WeaponItems.MACE : (sword ? WeaponItems.SWORD : WeaponItems.SPEAR));
        meta.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, kills);
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> lore(String base, boolean mace, int kills, int tier) {
        List<Component> lines = new ArrayList<>();
        lines.add(gray("Kills: " + kills));
        lines.add(dark(tier >= MAX_TIER
                ? "Reach " + godKills() + " kills for the God " + base + "."
                : "Next tier at " + NEEDED[tier] + " kills."));
        lines.add(Component.empty());

        // Passive effects while held.
        List<String> passive = new ArrayList<>();
        if (tier >= 2) passive.add(tier >= 6 ? "Speed II" : "Speed I");
        if (tier >= 4) passive.add("Jump Boost");
        if (tier >= 7) passive.add(tier >= 11 ? "Resistance II" : "Resistance I");
        if (tier >= 8) passive.add("Fire Resistance");
        if (tier >= 9) passive.add(tier >= 13 ? "Strength II" : "Strength I");
        if (tier >= 11) passive.add("Regeneration");
        if (tier >= 13) passive.add("Health Boost");
        if (!passive.isEmpty()) lines.add(gray("Held: " + String.join(", ", passive)));

        // On-hit effects.
        List<String> hit = new ArrayList<>();
        hit.add("+" + (tier * 0.5) + " damage");
        if (tier >= 3) hit.add("Slowness");
        if (tier >= 5) hit.add(tier >= 12 ? "20% lifesteal" : "10% lifesteal");
        if (tier >= 6) hit.add("Fire");
        if (tier >= 8) hit.add("Weakness");
        if (tier >= 10) hit.add("Wither");
        if (tier >= 13) hit.add("Lightning");
        lines.add(gray("On hit: " + String.join(", ", hit)));

        // Abilities.
        if (tier >= 4) lines.add(gray("Right-click: " + (mace ? "Leap" : "Dash")));
        if (tier >= 8) lines.add(gray("Shift + right-click: Shockwave"));
        if (tier >= 12) lines.add(gray("Shift + left-click: Blink"));
        return lines;
    }

    private static Component gray(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static Component dark(String text) {
        return Component.text(text, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static void enchant(ItemMeta meta, String name, int level) {
        if (level <= 0) return;
        Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));
        if (e != null) meta.addEnchant(e, level, true);
    }
}
