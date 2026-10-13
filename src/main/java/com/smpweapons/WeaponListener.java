package com.smpweapons;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WeaponListener implements Listener {

    private final SmpWeaponsPlugin plugin;
    private final WeaponItems items;
    private final OrbitalCannon cannon;

    private final Map<UUID, Long> maceCooldown = new HashMap<>();
    private final Map<UUID, Long> dashCooldown = new HashMap<>();
    private final Map<UUID, Long> cannonCooldown = new HashMap<>();
    private final Map<UUID, Long> nukeCooldown = new HashMap<>();

    public WeaponListener(SmpWeaponsPlugin plugin, WeaponItems items, OrbitalCannon cannon) {
        this.plugin = plugin;
        this.items = items;
        this.cannon = cannon;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String type = items.typeOf(event.getItem());
        if (type == null) return;

        Player player = event.getPlayer();
        Action action = event.getAction();

        if (WeaponItems.MACE.equals(type)) {
            if (action == Action.LEFT_CLICK_AIR) {
                reachAttack(player);
            }
        } else if (WeaponItems.SPEAR.equals(type)) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                // Shift + right-click = stab (column), right-click = nuke (rings).
                fireCannon(player, player.isSneaking());
            }
        }
    }

    // ------------------------------------------------------------------
    // Mace: infinite reach
    // ------------------------------------------------------------------

    private void reachAttack(Player player) {
        var cfg = plugin.getConfig();
        if (onCooldown(maceCooldown, player, cfg.getLong("mace.cooldown-ms", 600))) return;

        double range = cfg.getDouble("mace.reach", 128.0);
        double damage = cfg.getDouble("mace.damage", 20.0);
        boolean throughWalls = cfg.getBoolean("mace.through-walls", false);

        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();

        double maxDist = range;
        if (!throughWalls) {
            RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
            if (blockHit != null) {
                maxDist = blockHit.getHitPosition().distance(eye.toVector());
            }
        }

        RayTraceResult hit = world.rayTraceEntities(eye, dir, maxDist, 0.3, entity ->
                entity instanceof LivingEntity
                        && !entity.equals(player)
                        && !entity.isDead()
                        && !(entity instanceof Player other && other.getGameMode() == GameMode.SPECTATOR));

        player.swingMainHand();
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) return;

        Vector end = hit.getHitPosition();
        drawLine(world, eye.toVector().add(dir.clone().multiply(1.5)), end);

        // A plugin-made hit skips vanilla mace enchant effects, so apply them here.
        ItemStack held = player.getInventory().getItemInMainHand();
        int windBurst = enchantLevel(held, "wind_burst");
        int breach = enchantLevel(held, "breach");
        int density = enchantLevel(held, "density");

        double fallen = player.getFallDistance();
        // true = every reach hit counts as a smash (so Wind Burst works while standing still).
        boolean smash = fallen > 1.5 || cfg.getBoolean("mace.reach-always-smash", true);

        double total = damage;
        if (smash) {
            total += fallen * 0.5 * density;   // Density: +0.5 damage per block fallen, per level
            total += damage * 0.15 * breach;   // Breach: approximates ignoring 15% of armor per level
        }
        target.damage(total, player);

        if (smash && windBurst > 0) {
            // Wind Burst: launch the wielder upward and cancel the fall.
            Vector v = player.getVelocity();
            player.setVelocity(new Vector(v.getX(), 0.8 + 0.4 * windBurst, v.getZ()));
            player.setFallDistance(0);
            world.playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, 1.0f);
            world.spawnParticle(Particle.GUST_EMITTER_SMALL, player.getLocation(), 1);
        }

        target.setVelocity(target.getVelocity().add(dir.clone().multiply(0.8).setY(0.35)));
        world.playSound(target.getLocation(), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 1.0f);
        world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.2);
    }

    private static int enchantLevel(ItemStack item, String name) {
        if (item == null) return 0;
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));
        return enchantment == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    private void drawLine(World world, Vector from, Vector to) {
        Vector diff = to.clone().subtract(from);
        double length = diff.length();
        if (length < 0.5) return;
        Vector step = diff.normalize();
        // Cap the particle count so very long hits stay cheap.
        double spacing = Math.max(1.0, length / 80.0);
        for (double d = 0; d < length; d += spacing) {
            Vector p = from.clone().add(step.clone().multiply(d));
            world.spawnParticle(Particle.CRIT, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------
    // Spear: lunge dash (no cooldown by default)
    // ------------------------------------------------------------------

    private void lungeDash(Player player) {
        var cfg = plugin.getConfig();
        long cooldown = cfg.getLong("spear.dash.cooldown-ms", 0);
        if (cooldown > 0 && onCooldown(dashCooldown, player, cooldown)) return;

        double power = cfg.getDouble("spear.dash.power", 3.2);
        int ticks = cfg.getInt("spear.dash.ticks", 8);
        double damage = cfg.getDouble("spear.dash.damage", 10.0);

        Vector velocity = player.getLocation().getDirection().normalize().multiply(power);
        if (velocity.getY() < 0.15) velocity.setY(0.15);
        player.setVelocity(velocity);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.4f);

        Set<UUID> alreadyHit = new HashSet<>();
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || elapsed >= ticks) {
                    player.setFallDistance(0);
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.02);
                for (Entity nearby : player.getNearbyEntities(1.8, 1.5, 1.8)) {
                    if (nearby instanceof LivingEntity living
                            && !nearby.equals(player)
                            && alreadyHit.add(nearby.getUniqueId())) {
                        living.damage(damage, player);
                    }
                }
                elapsed++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------
    // Spear: TNT orbital cannon
    // ------------------------------------------------------------------

    private void fireCannon(Player player, boolean stab) {
        var cfg = plugin.getConfig();
        // New key names (seconds) so an old saved config can't override these.
        long cooldownMs = stab
                ? cfg.getLong("spear.orbital-cannon.stab-cooldown-seconds", 5) * 1000L
                : cfg.getLong("spear.orbital-cannon.nuke-cooldown-seconds", 0) * 1000L;
        Map<UUID, Long> cooldowns = stab ? cannonCooldown : nukeCooldown;
        double range = cfg.getDouble("spear.orbital-cannon.range", 200.0);

        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();

        Location target = null;
        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double maxDist = range;
        if (blockHit != null) {
            maxDist = blockHit.getHitPosition().distance(eye.toVector());
            target = blockHit.getHitPosition().toLocation(world);
        }
        RayTraceResult entityHit = world.rayTraceEntities(eye, dir, maxDist, 0.3, entity ->
                entity instanceof LivingEntity && !entity.equals(player));
        if (entityHit != null && entityHit.getHitEntity() != null) {
            target = entityHit.getHitEntity().getLocation();
        }

        if (target == null) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Orbital cannon: no target in range.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        if (cooldownMs > 0 && onCooldown(cooldowns, player, cooldownMs)) {
            long left = remaining(cooldowns, player, cooldownMs);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Orbital cannon recharging: " + (left / 1000 + 1) + "s",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            return;
        }

        cannon.fire(player, target, stab);
    }

    // ------------------------------------------------------------------
    // TNT handling (block damage + caster protection)
    // ------------------------------------------------------------------

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        if (!tnt.getPersistentDataContainer().has(cannon.tntKey(), PersistentDataType.BYTE)) return;
        if (!plugin.getConfig().getBoolean("spear.orbital-cannon.break-blocks", true)) {
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onTntDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof TNTPrimed tnt)) return;
        if (!tnt.getPersistentDataContainer().has(cannon.tntKey(), PersistentDataType.BYTE)) return;
        if (plugin.getConfig().getBoolean("spear.orbital-cannon.protect-caster", true)
                && tnt.getSource() != null
                && tnt.getSource().equals(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        maceCooldown.remove(id);
        dashCooldown.remove(id);
        cannonCooldown.remove(id);
        nukeCooldown.remove(id);
    }

    // ------------------------------------------------------------------
    // Cooldown helpers
    // ------------------------------------------------------------------

    /** Returns true if still on cooldown; otherwise starts a new cooldown and returns false. */
    private boolean onCooldown(Map<UUID, Long> map, Player player, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = map.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) return true;
        map.put(player.getUniqueId(), now);
        return false;
    }

    private long remaining(Map<UUID, Long> map, Player player, long cooldownMs) {
        Long last = map.get(player.getUniqueId());
        if (last == null) return 0;
        return Math.max(0, cooldownMs - (System.currentTimeMillis() - last));
    }
}
