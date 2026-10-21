package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Temporary two-way portals between the Overworld and the Sift. */
public final class RiftManager {

    private static final class Rift {
        final Location base;
        final long expires;
        Rift partner;

        Rift(Location base, long expires) {
            this.base = base;
            this.expires = expires;
        }
    }

    private final SmpWeaponsPlugin plugin;
    private final List<Rift> rifts = new ArrayList<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public RiftManager(SmpWeaponsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    /** Opens a rift at the given spot plus a matching exit in the other world. */
    public boolean open(Player player, Location at) {
        World sift = SiftWorld.get(plugin);
        if (sift == null) {
            player.sendMessage(Component.text("The Sift world is turned off in the config.", NamedTextColor.RED));
            return false;
        }
        World overworld = Bukkit.getWorlds().get(0);
        World source = at.getWorld();
        World dest = source.equals(sift) ? overworld : sift;

        Location a = at.clone();
        Location b = new Location(dest, at.getX(), 0, at.getZ());
        b.setY(dest.getHighestBlockYAt(b) + 1);

        long expires = System.currentTimeMillis() + plugin.getConfig().getLong("sift.rift-seconds", 300) * 1000L;
        Rift r1 = new Rift(a, expires);
        Rift r2 = new Rift(b, expires);
        r1.partner = r2;
        r2.partner = r1;
        rifts.add(r1);
        rifts.add(r2);

        source.playSound(a, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.6f);
        player.sendMessage(Component.text("A Rift opened. Walk into it to cross over. It closes in "
                + plugin.getConfig().getLong("sift.rift-seconds", 300) / 60.0 + " minutes.", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        cooldown.values().removeIf(t -> t < now);
        Iterator<Rift> it = rifts.iterator();
        while (it.hasNext()) {
            Rift rift = it.next();
            World world = rift.base.getWorld();
            if (now > rift.expires || world == null) {
                it.remove();
                continue;
            }
            Location center = rift.base.clone().add(0, 1, 0);
            world.spawnParticle(Particle.REVERSE_PORTAL, center, 14, 0.5, 0.8, 0.5, 0.03);
            world.spawnParticle(Particle.SCULK_SOUL, center, 2, 0.4, 0.7, 0.4, 0.01);

            for (LivingEntity entity : world.getNearbyLivingEntities(center, 1.3)) {
                if (entity.isDead()) continue;
                if (entity instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
                if (cooldown.containsKey(entity.getUniqueId())) continue;
                Location target = arrival(rift.partner, entity);
                entity.teleport(target);
                cooldown.put(entity.getUniqueId(), now + 3000L);
                target.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
            }
        }
    }

    /** A spot just in front of the exit rift, on the ground. */
    private Location arrival(Rift exit, LivingEntity entity) {
        Vector dir = entity.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        dir.normalize();
        Location t = exit.base.clone().add(dir.clone().multiply(2.5));
        t.setY(t.getWorld().getHighestBlockYAt(t) + 1);
        if (!t.getBlock().isPassable() || !t.clone().add(0, 1, 0).getBlock().isPassable()) {
            t = exit.base.clone();
        }
        t.setDirection(dir);
        return t;
    }
}
