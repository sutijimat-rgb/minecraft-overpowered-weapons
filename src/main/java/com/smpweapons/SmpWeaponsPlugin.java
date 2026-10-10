package com.smpweapons;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SmpWeaponsPlugin extends JavaPlugin {

    private WeaponItems items;
    private OrbitalCannon cannon;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.items = new WeaponItems(this);
        this.cannon = new OrbitalCannon(this);

        getServer().getPluginManager().registerEvents(new WeaponListener(this, items, cannon), this);

        WeaponCommand command = new WeaponCommand(this, items);
        var pluginCommand = getCommand("smpweapons");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        // Mace passive: Resistance while holding it and healthy.
        getServer().getScheduler().runTaskTimer(this, this::tickMacePassive, 10L, 10L);
    }

    private void tickMacePassive() {
        var cfg = getConfig();
        if (!cfg.getBoolean("mace.resistance.enabled", true)) return;
        int amplifier = cfg.getInt("mace.resistance.amplifier", 1);
        double healthyPercent = cfg.getDouble("mace.resistance.healthy-percent", 0.7);

        for (Player player : getServer().getOnlinePlayers()) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!WeaponItems.MACE.equals(items.typeOf(held))) continue;

            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth == null) continue;
            if (player.getHealth() >= maxHealth.getValue() * healthyPercent) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, 30, amplifier, true, false, true));
            }
        }
    }
}
