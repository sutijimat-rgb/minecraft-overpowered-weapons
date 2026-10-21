package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Ichor pools, the Rift Key, and Siftite items coming back after death. */
public final class SiftListener implements Listener {

    private final SmpWeaponsPlugin plugin;
    private final SiftItems items;
    private final RiftManager rifts;
    private final NamespacedKey shotKey;
    private final File pendingFile;
    private final YamlConfiguration pending;
    private final Random random = new Random();

    public SiftListener(SmpWeaponsPlugin plugin, SiftItems items, RiftManager rifts) {
        this.plugin = plugin;
        this.items = items;
        this.rifts = rifts;
        this.shotKey = new NamespacedKey(plugin, "ichor_shot");
        plugin.getDataFolder().mkdirs();
        this.pendingFile = new File(plugin.getDataFolder(), "siftite-pending.yml");
        this.pending = YamlConfiguration.loadConfiguration(pendingFile);
    }

    // ---------------- Ichor ----------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        String kind = items.kindOf(event.getItem());
        Player player = event.getPlayer();

        if (SiftItems.ICHOR_BUCKET.equals(kind)) {
            event.setCancelled(true);
            Location at = event.getClickedBlock() != null
                    ? event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5)
                    : player.getLocation();
            pool(at, 30, 3.5);
            consumeOne(player, new ItemStack(Material.BUCKET));
        } else if (SiftItems.RIFT_KEY.equals(kind)) {
            event.setCancelled(true);
            Vector dir = player.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            Location at = player.getLocation().add(dir.normalize().multiply(3));
            if (rifts.open(player, at)) consumeOne(player, null);
        }
    }

    private void consumeOne(Player player, ItemStack replacement) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(replacement);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(held);
            if (replacement != null) {
                player.getInventory().addItem(replacement).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
        }
    }

    /** A healing pool: Regeneration II for anything standing in it. */
    private void pool(Location center, int seconds, double radius) {
        World world = center.getWorld();
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 190, 60), 1.4f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= seconds * 2) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 24; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = Math.sqrt(random.nextDouble()) * radius;
                    world.spawnParticle(Particle.DUST, center.getX() + Math.cos(angle) * dist,
                            center.getY() + 0.1, center.getZ() + Math.sin(angle) * dist, 1, 0, 0, 0, 0, gold);
                }
                for (LivingEntity entity : world.getNearbyLivingEntities(center, radius, 2, radius)) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1, true, true, true));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball ball) || !(ball.getShooter() instanceof Player shooter)) return;
        if (SiftItems.ICHOR_SNOWBALL.equals(items.kindOf(shooter.getInventory().getItemInMainHand()))
                || SiftItems.ICHOR_SNOWBALL.equals(items.kindOf(shooter.getInventory().getItemInOffHand()))) {
            ball.getPersistentDataContainer().set(shotKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(shotKey, PersistentDataType.BYTE)) return;
        pool(event.getEntity().getLocation(), 12, 2.5);
    }

    // ---------------- Siftite comes back after death (Mini Rift) ----------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> keep = new ArrayList<>();
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (SiftItems.SIFTITE.equals(items.kindOf(stack))) {
                keep.add(stack.clone());
                it.remove();
            }
        }
        if (keep.isEmpty()) return;
        pending.set(event.getEntity().getUniqueId().toString(), keep);
        savePending();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> giveBack(player), 5L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isDead()) giveBack(player);
        }, 20L);
    }

    private void giveBack(Player player) {
        String id = player.getUniqueId().toString();
        List<?> saved = pending.getList(id);
        if (saved == null || saved.isEmpty()) return;
        pending.set(id, null);
        savePending();

        for (Object obj : saved) {
            if (!(obj instanceof ItemStack stack)) continue;
            EquipmentSlot slot = stack.getType().getEquipmentSlot();
            if (slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND && slot.isArmor()
                    && (player.getInventory().getItem(slot) == null
                    || player.getInventory().getItem(slot).getType().isAir())) {
                player.getInventory().setItem(slot, stack);
                continue;
            }
            player.getInventory().addItem(stack).values().forEach(rest -> {
                Item drop = player.getWorld().dropItem(player.getLocation(), rest);
                drop.setInvulnerable(true);
            });
        }
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.1);
        player.sendMessage(Component.text("A Mini Rift returned your Siftite gear.", NamedTextColor.LIGHT_PURPLE));
    }

    private void savePending() {
        try {
            pending.save(pendingFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save siftite-pending.yml: " + e.getMessage());
        }
    }
}
