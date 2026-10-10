package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class OrbitalCannon {

    private final SmpWeaponsPlugin plugin;
    private final NamespacedKey tntKey;

    public OrbitalCannon(SmpWeaponsPlugin plugin) {
        this.plugin = plugin;
        this.tntKey = new NamespacedKey(plugin, "orbital_tnt");
    }

    public NamespacedKey tntKey() {
        return tntKey;
    }

    public void fire(Player caster, Location target) {
        var cfg = plugin.getConfig();
        int chargeTicks = cfg.getInt("spear.orbital-cannon.charge-ticks", 40);
        int durationTicks = cfg.getInt("spear.orbital-cannon.duration-ticks", 60);
        int tntPerTick = cfg.getInt("spear.orbital-cannon.tnt-per-tick", 2);
        double radius = cfg.getDouble("spear.orbital-cannon.radius", 8.0);
        double skyHeight = cfg.getDouble("spear.orbital-cannon.sky-height", 60.0);
        int safetyFuse = cfg.getInt("spear.orbital-cannon.safety-fuse-ticks", 200);
        float power = (float) cfg.getDouble("spear.orbital-cannon.explosion-power", 4.0);

        World world = target.getWorld();
        double topY = Math.min(target.getY() + skyHeight, world.getMaxHeight() - 2);

        world.playSound(target, Sound.BLOCK_BEACON_ACTIVATE, 4.0f, 0.6f);
        caster.playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.5f);
        caster.sendActionBar(Component.text("ORBITAL CANNON FIRING", NamedTextColor.RED));
        caster.sendMessage(Component.text("Orbital cannon locked on at "
                + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ(),
                NamedTextColor.RED));

        List<TNTPrimed> falling = new ArrayList<>();

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                int endTick = chargeTicks + durationTicks;

                if (tick < chargeTicks) {
                    drawBeam(world, target, topY);
                    // Warning ring on the ground that tightens as it charges.
                    double ringRadius = radius * (1.0 - (double) tick / Math.max(1, chargeTicks)) + 1.0;
                    drawRing(world, target, ringRadius);
                } else if (tick < endTick) {
                    drawBeam(world, target, topY);
                    ThreadLocalRandom rng = ThreadLocalRandom.current();
                    for (int i = 0; i < tntPerTick; i++) {
                        double angle = rng.nextDouble(Math.PI * 2);
                        double dist = Math.sqrt(rng.nextDouble()) * radius;
                        Location spawn = new Location(world,
                                target.getX() + Math.cos(angle) * dist,
                                topY,
                                target.getZ() + Math.sin(angle) * dist);
                        TNTPrimed tnt = world.spawn(spawn, TNTPrimed.class, t -> {
                            t.setFuseTicks(safetyFuse);
                            t.setYield(power);
                            t.setSource(caster);
                            t.setVelocity(new Vector(0, -1.5, 0));
                            t.getPersistentDataContainer().set(tntKey, PersistentDataType.BYTE, (byte) 1);
                        });
                        falling.add(tnt);
                    }
                }

                // Blow each TNT up the moment it touches down, so it explodes
                // at the target instead of in mid-air.
                Iterator<TNTPrimed> it = falling.iterator();
                while (it.hasNext()) {
                    TNTPrimed tnt = it.next();
                    if (!tnt.isValid()) {
                        it.remove();
                    } else if (tnt.isOnGround() || tnt.getLocation().getY() <= target.getY() + 0.5) {
                        tnt.setFuseTicks(1);
                        it.remove();
                    }
                }

                tick++;
                if (tick >= endTick && falling.isEmpty()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawBeam(World world, Location target, double topY) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 2.0f);
        for (double y = target.getY(); y <= topY; y += 2.0) {
            world.spawnParticle(Particle.DUST, target.getX(), y, target.getZ(), 1, 0.15, 0, 0.15, 0, dust);
        }
        world.spawnParticle(Particle.END_ROD, target.getX(), target.getY() + 1, target.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
    }

    private void drawRing(World world, Location target, double ringRadius) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.ORANGE, 1.5f);
        int points = 36;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            world.spawnParticle(Particle.DUST,
                    target.getX() + Math.cos(angle) * ringRadius,
                    target.getY() + 0.2,
                    target.getZ() + Math.sin(angle) * ringRadius,
                    1, 0, 0, 0, 0, dust);
        }
    }
}
