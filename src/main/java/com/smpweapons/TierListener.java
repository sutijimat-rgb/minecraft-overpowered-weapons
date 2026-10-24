package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Passive effects, on-hit effects and abilities of the tiered mace, spear and sword. */
public final class TierListener implements Listener {

    private final SmpWeaponsPlugin plugin;
    private final TierItems tiers;

    private final Map<UUID, Long> moveCooldown = new HashMap<>();
    private final Map<UUID, Long> slamCooldown = new HashMap<>();
    private final Map<UUID, Long> blinkCooldown = new HashMap<>();
    private final Set<UUID> noFall = new HashSet<>();

    public TierListener(SmpWeaponsPlugin plugin, TierItems tiers) {
        this.plugin = plugin;
        this.tiers = tiers;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::passive, 10L, 10L);
    }

    // ---------------- passive effects while held ----------------

    private void give(Player p, PotionEffectType type, int amplifier) {
        p.addPotionEffect(new PotionEffect(type, 30, amplifier, true, false, true));
    }

    private void passive() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            int tier = tiers.tierOf(p.getInventory().getItemInMainHand());
            if (tier <= 0) continue;
            if (tier >= 2) give(p, PotionEffectType.SPEED, tier >= 6 ? 1 : 0);
            if (tier >= 4) give(p, PotionEffectType.JUMP_BOOST, 0);
            if (tier >= 7) give(p, PotionEffectType.RESISTANCE, tier >= 11 ? 1 : 0);
            if (tier >= 8) give(p, PotionEffectType.FIRE_RESISTANCE, 0);
            if (tier >= 9) give(p, PotionEffectType.STRENGTH, tier >= 13 ? 1 : 0);
            if (tier >= 11) give(p, PotionEffectType.REGENERATION, 0);
            if (tier >= 13) give(p, PotionEffectType.HEALTH_BOOST, 1);
        }
    }

    // ---------------- on-hit effects ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) return;
        int tier = tiers.tierOf(attacker.getInventory().getItemInMainHand());
        if (tier <= 0) return;

        double damage = event.getDamage() + tier * 0.5;
        if (tier >= 13) damage += 4.0;
        event.setDamage(damage);

        if (tier >= 3) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
        if (tier >= 6) target.setFireTicks(80);
        if (tier >= 8) target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
        if (tier >= 10) target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0));
        if (tier >= 13) target.getWorld().strikeLightningEffect(target.getLocation());

        if (tier >= 5) {
            double fraction = tier >= 12 ? 0.20 : 0.10;
            AttributeInstance max = attacker.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                attacker.setHealth(Math.min(max.getValue(), attacker.getHealth() + damage * fraction));
            }
        }
    }

    // ---------------- abilities ----------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        int tier = tiers.tierOf(held);
        if (tier <= 0) return;
        String type = tiers.typeOf(held);

        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;

        if (right && !player.isSneaking() && tier >= 4) {
            if (!ready(moveCooldown, player, Math.max(1000L, 4000L - tier * 150L), "Ability")) return;
            if (WeaponItems.MACE.equals(type)) leap(player, tier); else dash(player, tier);
        } else if (right && player.isSneaking() && tier >= 8) {
            if (!ready(slamCooldown, player, 8000L, "Shockwave")) return;
            shockwave(player, tier);
        } else if (left && player.isSneaking() && tier >= 12) {
            event.setCancelled(true);
            if (!ready(blinkCooldown, player, 6000L, "Blink")) return;
            blink(player);
        }
    }

    private boolean ready(Map<UUID, Long> map, Player player, long cooldownMs, String name) {
        long now = System.currentTimeMillis();
        Long last = map.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            long left = (cooldownMs - (now - last)) / 1000 + 1;
            player.sendActionBar(Component.text(name + " ready in " + left + "s", NamedTextColor.YELLOW));
            return false;
        }
        map.put(player.getUniqueId(), now);
        return true;
    }

    private void protectFromFall(Player player) {
        noFall.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> noFall.remove(player.getUniqueId()), 120L);
    }

    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player p
                && noFall.remove(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Mace ability: leap into the air. */
    private void leap(Player player, int tier) {
        Vector v = player.getLocation().getDirection().setY(0);
        if (v.lengthSquared() > 0.01) v.normalize().multiply(0.5);
        v.setY(0.9 + 0.04 * tier);
        player.setVelocity(v);
        protectFromFall(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.2f, 1.0f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.3, 0.1, 0.3, 0.05);
    }

    /** Spear and sword ability: dash forward, hitting whatever you pass through. */
    private void dash(Player player, int tier) {
        Vector v = player.getLocation().getDirection().normalize().multiply(1.4 + 0.1 * tier);
        if (v.getY() < 0.15) v.setY(0.15);
        player.setVelocity(v);
        protectFromFall(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.4f);
        double damage = 4.0 + tier;
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || elapsed >= 6) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.02);
                for (Entity near : player.getNearbyEntities(1.8, 1.5, 1.8)) {
                    if (near instanceof LivingEntity living && !near.equals(player) && hit.add(near.getUniqueId())) {
                        living.damage(damage, player);
                    }
                }
                elapsed++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Ground slam around you: damage and launch everything nearby. */
    private void shockwave(Player player, int tier) {
        double radius = 4.0 + tier * 0.3;
        double damage = 4.0 + tier;
        World world = player.getWorld();
        Location at = player.getLocation();
        for (Entity near : player.getNearbyEntities(radius, 3, radius)) {
            if (!(near instanceof LivingEntity living)) continue;
            living.damage(damage, player);
            living.setVelocity(living.getVelocity().add(new Vector(0, 0.6, 0)));
            if (tier >= 12) living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3));
        }
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION, at, 3, radius / 3, 0.2, radius / 3, 0);
        world.spawnParticle(Particle.CLOUD, at, 40, radius / 2, 0.2, radius / 2, 0.1);
    }

    /** Teleport up to 15 blocks forward. */
    private void blink(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        double distance = 15;
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, dir, distance, FluidCollisionMode.NEVER, true);
        if (hit != null) distance = Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 1.0);
        Location target = player.getLocation().add(dir.clone().multiply(distance));
        target.setDirection(dir);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
        player.teleport(target);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
        player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        protectFromFall(player);
    }
}
