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
import java.util.Locale;

/**
 * Orbital strike cannon, made of real TNT explosions (so blast resistance and damage
 * behave like vanilla). Two modes:
 *  - stab: a column of TNT from the sky down to the bottom of the world, going off almost at once.
 *  - nuke: rings of TNT falling from the sky onto the target.
 */
public final class OrbitalCannon {

    private final SmpWeaponsPlugin plugin;
    private final NamespacedKey tntKey;

    public OrbitalCannon(SmpWeaponsPlugin plugin) {
        this.plugin = plugin;
        this.tntKey = new NamespacedKey(plugin, "orbital_tnt");
    }

    /** Tag used on every TNT the cannon spawns (the listener uses it for block and caster protection). */
    public NamespacedKey tntKey() {
        return tntKey;
    }

    /** stab = column of TNT to the bottom of the world; otherwise nuke = rings of TNT from the sky. */
    public void fire(Player caster, Location target, boolean stab) {
        var cfg = plugin.getConfig();
        // New key names so an old saved config can't override these.
        int chargeTicks = stab
                ? cfg.getInt("spear.orbital-cannon.stab-charge-ticks", 60)
                : cfg.getInt("spear.orbital-cannon.nuke-charge-ticks", 20);
        float power = (float) cfg.getDouble("spear.orbital-cannon.tnt-power", 6.0);
        int spacing = Math.max(1, cfg.getInt("spear.orbital-cannon.stab-spacing", 3));
        // How many TNT spots are spawned per tick (each spot holds tnt-per-spot TNT).
        int tntPerTick = Math.max(1, cfg.getInt("spear.orbital-cannon.spots-per-tick", 4));
        double nukeRadius = Math.max(4.0, cfg.getDouble("spear.orbital-cannon.nuke-radius", 20.0));

        World world = target.getWorld();
        int minY = world.getMinHeight();
        double topY = world.getMaxHeight() - 2;
        double x = target.getBlockX() + 0.5;
        double z = target.getBlockZ() + 0.5;
        int startY = target.getBlockY() + 6;

        world.playSound(target, Sound.BLOCK_BEACON_ACTIVATE, 8.0f, 0.5f);
        caster.sendActionBar(Component.text("ORBITAL STRIKE LOCKED ON", NamedTextColor.RED));
        caster.sendMessage(Component.text((stab ? "Stab" : "Nuke") + " strike locked on at "
                + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ(),
                NamedTextColor.RED));
        for (Player nearby : world.getPlayers()) {
            if (!nearby.equals(caster) && nearby.getLocation().distanceSquared(target) < 160 * 160) {
                nearby.sendActionBar(Component.text("ORBITAL STRIKE INCOMING - GET AWAY", NamedTextColor.RED));
            }
        }

        // Nuke mode: three rings (inner to outer), one every 12 ticks.
        double[] ringFractions = {0.25, 0.6, 1.0};
        int[] ringCounts = {8, 18, 32};
        List<TNTPrimed> falling = new ArrayList<>();

        new BukkitRunnable() {
            int tick = 0;
            int nextY = startY;
            boolean impacted = false;

            @Override
            public void run() {
                // ---- Charge: beam from space, warning ring ----
                if (tick < chargeTicks) {
                    double progress = (double) tick / Math.max(1, chargeTicks);
                    drawBeam(world, x, z, topY, target.getY(), 1.0f + (float) progress * 2.0f);
                    drawRing(world, x, z, target.getY(), (stab ? power * 2 : nukeRadius) * (1.2 - 0.2 * progress));
                    if (tick % 10 == 0) {
                        world.playSound(target, Sound.BLOCK_BEACON_AMBIENT, 6.0f, 0.5f + (float) progress * 1.5f);
                    }
                    tick++;
                    return;
                }

                if (!impacted) {
                    impacted = true;
                    world.strikeLightningEffect(target);
                    world.playSound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 16.0f, 0.6f);
                }
                int sinceImpact = tick - chargeTicks;
                tick++;

                if (stab) {
                    // A column of TNT from just above the target down to the bottom of the world.
                    for (int i = 0; i < tntPerTick && nextY > minY; i++, nextY -= spacing) {
                        spawnTnt(world, x, nextY, z, caster, power, 1, null);
                    }
                    drawBeam(world, x, z, topY, Math.max(nextY, minY), 3.0f);
                    if (nextY <= minY) {
                        cancel();
                    }
                    return;
                }

                // ---- Nuke: rings of TNT falling from the sky ----
                int ring = sinceImpact / 12;
                if (sinceImpact % 12 == 0 && ring < ringFractions.length) {
                    double radius = nukeRadius * ringFractions[ring];
                    int count = ringCounts[ring];
                    for (int k = 0; k < count; k++) {
                        double angle = 2 * Math.PI * k / count;
                        spawnTnt(world, x + Math.cos(angle) * radius, topY, z + Math.sin(angle) * radius,
                                caster, power, 400, falling);
                    }
                }
                drawBeam(world, x, z, topY, target.getY(), 2.0f);

                // Blow each TNT up the moment it lands so it explodes at the target, not in mid-air.
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
                if (ring >= ringFractions.length && falling.isEmpty()) {
                    cancel();
                } else if (sinceImpact > 600) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnTnt(World world, double x, double y, double z, Player caster, float power,
                          int fuse, List<TNTPrimed> track) {
        // Several TNT are packed into the same spot so their explosions stack.
        int packed = Math.max(1, plugin.getConfig().getInt("spear.orbital-cannon.tnt-per-spot", 20));
        for (int n = 0; n < packed; n++) {
            TNTPrimed tnt = world.spawn(new Location(world, x, y, z), TNTPrimed.class, t -> {
                t.setFuseTicks(fuse);
                t.setYield(power);
                t.setSource(caster);
                if (fuse > 1) {
                    t.setVelocity(new Vector(0, -1.5, 0));
                }
                t.getPersistentDataContainer().set(tntKey, PersistentDataType.BYTE, (byte) 1);
            });
            if (track != null) {
                track.add(tnt);
            }
        }
    }

    private void drawBeam(World world, double x, double z, double topY, double bottomY, float size) {
        Particle.DustOptions red = new Particle.DustOptions(Color.RED, size);
        Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, size * 0.7f);
        for (double y = bottomY; y <= topY; y += 3.0) {
            world.spawnParticle(Particle.DUST, x, y, z, 2, 0.25, 0, 0.25, 0, red);
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0.1, 0, 0.1, 0, white);
        }
        world.spawnParticle(Particle.END_ROD, x, bottomY + 1, z, 4, 0.4, 0.8, 0.4, 0.03);
    }

    private void drawRing(World world, double x, double z, double y, double ringRadius) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.ORANGE, 1.5f);
        int points = 48;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            world.spawnParticle(Particle.DUST,
                    x + Math.cos(angle) * ringRadius, y + 0.3, z + Math.sin(angle) * ringRadius,
                    1, 0, 0, 0, 0, dust);
        }
    }
}
