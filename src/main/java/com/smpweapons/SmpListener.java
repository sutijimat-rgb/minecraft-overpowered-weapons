package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** The spear-and-mace SMP rules: tiers from kills, lifesteal, hardcore, PvP always on. */
public final class SmpListener implements Listener {

    private final SmpWeaponsPlugin plugin;
    private final WeaponItems items;
    private final SiftItems sift;
    private final TierItems tiers;
    private final HeartManager hearts;
    private final WeaponRegistry registry;

    public SmpListener(SmpWeaponsPlugin plugin, WeaponItems items, SiftItems sift, TierItems tiers,
                       HeartManager hearts, WeaponRegistry registry) {
        this.plugin = plugin;
        this.items = items;
        this.sift = sift;
        this.tiers = tiers;
        this.hearts = hearts;
        this.registry = registry;
    }

    public void enablePvpEverywhere() {
        for (World world : Bukkit.getWorlds()) world.setPVP(true);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().setPVP(true);
    }

    /** The starter spear and mace everyone gets. */
    public void giveStarter(Player player) {
        for (String type : new String[]{WeaponItems.MACE, WeaponItems.SPEAR}) {
            ItemStack item = tiers.create(type, 0);
            if (item != null) {
                player.getInventory().addItem(item).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
        }
    }

    // ---------------- join / respawn ----------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        hearts.apply(player);
        if (hearts.get(player) <= 0) {
            player.setGameMode(GameMode.SPECTATOR);
        }
        if (!player.hasPlayedBefore() && plugin.getConfig().getBoolean("smp.give-on-first-join", true)) {
            giveStarter(player);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> hearts.apply(player), 2L);
    }

    // ---------------- kills: hearts, tiers, elimination ----------------

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        boolean pvp = killer != null && !killer.equals(victim);

        // Lifesteal.
        boolean loseOnAny = plugin.getConfig().getBoolean("smp.lose-heart-on-any-death", true);
        if (pvp || loseOnAny) {
            hearts.set(victim, hearts.get(victim) - 1);
        }
        if (pvp) {
            if (hearts.get(killer) >= hearts.maxHearts()) {
                killer.getInventory().addItem(hearts.heartItem(1)).values()
                        .forEach(rest -> killer.getWorld().dropItemNaturally(killer.getLocation(), rest));
            } else {
                hearts.set(killer, hearts.get(killer) + 1);
            }
            addKill(killer);
        }

        // Hardcore: out of hearts means out of the game.
        if (hearts.get(victim) <= 0) {
            UUID id = victim.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> eliminate(id), 20L);
        }
    }

    private void addKill(Player killer) {
        ItemStack held = killer.getInventory().getItemInMainHand();
        String type = tiers.typeOf(held);
        if (type == null) return;

        int kills = tiers.killsOf(held) + 1;
        String name = WeaponItems.MACE.equals(type) ? "Mace" : "Spear";

        // Enough kills: become the God weapon, if nobody holds it yet.
        if (kills >= tiers.godKills() && registry.validId(type) == null) {
            ItemStack god = WeaponItems.MACE.equals(type) ? items.createMace() : items.createSpear();
            if (god != null) {
                String id = UUID.randomUUID().toString();
                god = items.withId(god, id);
                registry.setValidId(type, id);
                killer.getInventory().setItemInMainHand(god);
                Bukkit.broadcast(Component.text(killer.getName() + " has earned the God " + name + "!",
                        NamedTextColor.GOLD));
                return;
            }
        }

        int oldTier = TierItems.tierFor(kills - 1);
        ItemStack upgraded = tiers.withOneMoreKill(held);
        if (upgraded == null) return;
        killer.getInventory().setItemInMainHand(upgraded);
        int newTier = TierItems.tierFor(kills);
        if (newTier > oldTier) {
            killer.sendMessage(Component.text("Your " + name + " reached tier " + newTier + "!", NamedTextColor.GREEN));
        }
    }

    private void eliminate(UUID id) {
        Player player = Bukkit.getPlayer(id);
        String mode = plugin.getConfig().getString("smp.elimination", "spectator");
        Component msg = Component.text(
                (player != null ? player.getName() : "A player") + " ran out of hearts and is out of the SMP!",
                NamedTextColor.DARK_RED);
        Bukkit.broadcast(msg);
        if (player == null) return;
        if ("ban".equalsIgnoreCase(mode)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + player.getName() + " Out of hearts");
        } else {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    // ---------------- hearts as items ----------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!hearts.isHeartItem(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (hearts.get(player) >= hearts.maxHearts()) {
            player.sendActionBar(Component.text("You are already at the max hearts.", NamedTextColor.RED));
            return;
        }
        hearts.set(player, hearts.get(player) + 1);
        ItemStack held = player.getInventory().getItemInMainHand();
        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);
    }

    // ---------------- spear and mace only ----------------

    @EventHandler
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("smp.only-spear-mace", true)) return;
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player)) return;
        if (allowedWeapon(attacker.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        attacker.sendActionBar(Component.text("Only spears and maces work in PvP here.", NamedTextColor.RED));
    }

    private boolean allowedWeapon(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        if (m == Material.MACE || m.name().endsWith("_SPEAR")) return true;
        return items.typeOf(item) != null || sift.kindOf(item) != null;
    }
}
