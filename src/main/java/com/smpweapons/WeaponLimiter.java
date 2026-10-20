package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Enforces: only one mace and one spear may exist on the server. */
public final class WeaponLimiter implements Listener {

    private final SmpWeaponsPlugin plugin;
    private final WeaponItems items;
    private final WeaponRegistry registry;

    public WeaponLimiter(SmpWeaponsPlugin plugin, WeaponItems items, WeaponRegistry registry) {
        this.plugin = plugin;
        this.items = items;
        this.registry = registry;
    }

    /** Checks all online players once a second. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::scanNow, 40L, 20L);
    }

    public void scanNow() {
        Set<String> seen = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            enforce(player, player.getInventory(), seen);
            enforce(player, player.getEnderChest(), seen);
        }
    }

    private void enforce(Player player, Inventory inventory, Set<String> seen) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            String type = items.typeOf(stack);
            if (type == null || !limited(type)) continue;

            String id = items.idOf(stack);
            if (id == null && registry.validId(type) == null) {
                // A weapon from before the limit existed: adopt it as THE one.
                id = UUID.randomUUID().toString();
                registry.setValidId(type, id);
                inventory.setItem(slot, items.withId(stack, id));
            }

            // Keep it only if it is the valid one and the first copy found.
            boolean keep = id != null && id.equals(registry.validId(type)) && seen.add(type);
            if (!keep) {
                inventory.setItem(slot, null);
                player.sendMessage(Component.text(
                        "An extra " + type + " was removed. Only one " + type + " can exist on this server.",
                        NamedTextColor.RED));
            }
        }
    }

    /** Deletes stale copies out of any chest or container that gets opened. */
    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isStale(contents[slot])) {
                inventory.setItem(slot, null);
            }
        }
    }

    /** Stops players picking up stale copies off the ground. */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (isStale(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    /** The sword is not limited; only the mace and spear are one-per-server. */
    private static boolean limited(String type) {
        return WeaponItems.MACE.equals(type) || WeaponItems.SPEAR.equals(type);
    }

    private boolean isStale(ItemStack stack) {
        String type = items.typeOf(stack);
        if (type == null || !limited(type)) return false;
        String id = items.idOf(stack);
        String valid = registry.validId(type);
        return id == null ? valid != null : !id.equals(valid);
    }
}
