package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orbital strike: a beam charges from the sky, then a huge blast kills everything
 * near the target and a shaft is drilled straight down toward bedrock.
 */
public final class OrbitalCannon {

    /** Blocks the strike never removes. */
    private static final Set<Material> UNBREAKABLE = EnumSet.of(
            Material.BEDROCK, Material.BARRIER, Material.END_PORTAL_FRAME, Material.END_PORTAL,
            Material.END_GATEWAY, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.JIGSAW,
            Material.REINFORCED_DEEPSLATE);

    private final SmpWeaponsPlugin plugin;
    private final NamespacedKey tntKey;

    public OrbitalCannon(SmpWeaponsPlugin plugin) {
        this.plugin = plugin;
        this.tntKey = new NamespacedKey(plugin, "orbital_tnt");
    }

    /** Kept so the listener's old TNT handling still compiles; the strike no longer spawns TNT. */
    public NamespacedKey tntKey() {
        return tntKey;
    }

    public void fire(Player caster, Location target) {
        var cfg = plugin.getConfig();
        int chargeTicks = cfg.getInt("spear.orbital-cannon.charge-ticks", 80);
        double shaftRadius = Math.max(2.0, cfg.getDouble("spear.orbital-cannon.shaft-radius", 12.0));
        double damageRadius = Math.max(shaftRadius, cfg.getDouble("spear.orbital-cannon.damage-radius", 24.0));
        double strikeDamage = cfg.getDouble("spear.orbital-cannon.strike-damage", 1000.0);
        int layersPerTick = Math.max(1, cfg.getInt("spear.orbital-cannon.layers-per-tick", 6));
        boolean breakBlocks = cfg.getBoolean("spear.orbital-cannon.break-blocks", true);
        boolean protectCaster = cfg.getBoolean("spear.orbital-cannon.protect-caster", true);

        World world = target.getWorld();
        int minY = world.getMinHeight();
        double topY = world.getMaxHeight() - 2;
        int startY = target.getBlockY() + 8;

        List<int[]> offsets = shaftOffsets(shaftRadius);

        world.playSound(target, Sound.BLOCK_BEACON_ACTIVATE, 8.0f, 0.5f);
        caster.sendActionBar(Component.text("ORBITAL STRIKE LOCKED ON", NamedTextColor.RED));
        caster.sendMessage(Component.text("Orbital strike locked on at "
                + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ(),
                NamedTextColor.RED));
        for (Player nearby : world.getPlayers()) {
            if (!nearby.equals(caster) && nearby.getLocation().distanceSquared(target) < 160 * 160) {
                nearby.sendActionBar(Component.text("ORBITAL STRIKE INCOMING - GET AWAY", NamedTextColor.RED));
            }
        }

        new BukkitRunnable() {
            int tick = 0;
            int nextLayerY = startY;
            int sinceImpact = 0;
            boolean impacted = false;

            @Override
            public void run() {
                // ---- Charge: beam from space, ring closing in ----
                if (tick < chargeTicks) {
                    double progress = (double) tick / Math.max(1, chargeTicks);
                    drawBeam(world, target, topY, target.getY(), 1.0f + (float) progress * 2.0f);
                    drawRing(world, target, shaftRadius + (damageRadius - shaftRadius) * (1.0 - progress));
                    if (tick % 10 == 0) {
                        world.playSound(target, Sound.BLOCK_BEACON_AMBIENT, 6.0f, 0.5f + (float) progress * 1.5f);
                    }
                    tick++;
                    return;
                }

                // ---- Impact: one huge blast ----
                if (!impacted) {
                    impacted = true;
                    impact(caster, target, world, shaftRadius, damageRadius, strikeDamage, protectCaster);
                }
                sinceImpact++;

                // ---- Shockwave ring spreading outward ----
                if (sinceImpact <= 20) {
                    drawShockwave(world, target, damageRadius * sinceImpact / 20.0);
                }

                // ---- Drill the shaft downward, a few layers per tick ----
                boolean drilling = breakBlocks && nextLayerY >= minY;
                if (drilling) {
                    for (int i = 0; i < layersPerTick && nextLayerY >= minY; i++, nextLayerY--) {
                        digLayer(world, target, nextLayerY, offsets);
                    }
                    drawBeam(world, target, topY, Math.max(nextLayerY, minY), 3.0f);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER,
                            target.getX(), Math.max(nextLayerY, minY), target.getZ(), 2, shaftRadius / 3, 1, shaftRadius / 3, 0);
                    if (sinceImpact % 4 == 0) {
                        world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 10.0f, 0.6f);
                    }
                }

                // Finished when the shaft reaches the bottom (or, with block breaking off, after the shockwave).
                boolean finished = breakBlocks ? nextLayerY < minY : sinceImpact > 20;
                if (finished) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void impact(Player caster, Location target, World world, double shaftRadius,
                        double damageRadius, double strikeDamage, boolean protectCaster) {
        world.strikeLightningEffect(target);
        world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 16.0f, 0.5f);
        world.playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 16.0f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, target, 20, shaftRadius / 2, 2, shaftRadius / 2, 0);

        for (Entity entity : world.getNearbyEntities(target, damageRadius, 48, damageRadius)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (protectCaster && entity.equals(caster)) continue;

            Vector away = living.getLocation().toVector().subtract(target.toVector());
            away.setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            living.damage(strikeDamage, caster);
            living.setVelocity(away.normalize().multiply(1.8).setY(0.8));
        }
    }

    /** Removes one horizontal slice of the shaft. No physics, no drops, so it stays fast. */
    private void digLayer(World world, Location target, int y, List<int[]> offsets) {
        int cx = target.getBlockX();
        int cz = target.getBlockZ();
        for (int[] o : offsets) {
            Block block = world.getBlockAt(cx + o[0], y, cz + o[1]);
            Material type = block.getType();
            if (type.isAir() || UNBREAKABLE.contains(type)) continue;
            block.setType(Material.AIR, false);
        }
    }

    /** Circle of block offsets with a slightly ragged edge so the shaft looks natural. */
    private List<int[]> shaftOffsets(double radius) {
        List<int[]> list = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) continue;
                if (dist > radius - 1.5 && rng.nextDouble() < 0.3) continue;
                list.add(new int[]{dx, dz});
            }
        }
        return list;
    }

    private void drawBeam(World world, Location target, double topY, double bottomY, float size) {
        Particle.DustOptions red = new Particle.DustOptions(Color.RED, size);
        Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, size * 0.7f);
        for (double y = bottomY; y <= topY; y += 3.0) {
            world.spawnParticle(Particle.DUST, target.getX(), y, target.getZ(), 2, 0.25, 0, 0.25, 0, red);
            world.spawnParticle(Particle.DUST, target.getX(), y, target.getZ(), 1, 0.1, 0, 0.1, 0, white);
        }
        world.spawnParticle(Particle.END_ROD, target.getX(), bottomY + 1, target.getZ(), 4, 0.4, 0.8, 0.4, 0.03);
    }

    private void drawRing(World world, Location target, double ringRadius) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.ORANGE, 1.5f);
        int points = 48;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            world.spawnParticle(Particle.DUST,
                    target.getX() + Math.cos(angle) * ringRadius,
                    target.getY() + 0.3,
                    target.getZ() + Math.sin(angle) * ringRadius,
                    1, 0, 0, 0, 0, dust);
        }
    }

    private void drawShockwave(World world, Location target, double radius) {
        int points = 64;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            world.spawnParticle(Particle.CLOUD,
                    target.getX() + Math.cos(angle) * radius,
                    target.getY() + 1.0,
                    target.getZ() + Math.sin(angle) * radius,
                    1, 0.3, 0.5, 0.3, 0.02);
        }
    }
}
