package com.smpweapons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sniffer;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Sift creatures, faked with renamed vanilla mobs. */
public final class SiftMobs implements Listener {

    public static final String BLUB = "blub";
    public static final String SIFTER = "sifter";
    public static final String SNIFFER = "sniffer";
    public static final String DARK_SNIFFER = "dark_sniffer";
    public static final String GOLEM = "echo_golem";
    public static final String SINGER = "singer";
    public static final String WARDEN = "twisted_warden";

    private final SmpWeaponsPlugin plugin;
    private final SiftItems items;
    private final NamespacedKey kindKey;
    private final NamespacedKey ownerKey;
    private final Random random = new Random();

    private final Set<UUID> tracked = new HashSet<>();
    private final Map<UUID, Long> mining = new HashMap<>();
    private final Map<UUID, Integer> sculkTicks = new HashMap<>();
    private final Map<UUID, Long> sonicTime = new HashMap<>();
    private final Map<UUID, Long> biteTime = new HashMap<>();
    private final Map<UUID, UUID> singers = new HashMap<>();

    public SiftMobs(SmpWeaponsPlugin plugin, SiftItems items) {
        this.plugin = plugin;
        this.items = items;
        this.kindKey = new NamespacedKey(plugin, "sift_mob");
        this.ownerKey = new NamespacedKey(plugin, "sift_owner");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnTick, 200L, 600L);
    }

    // ---------------- helpers ----------------

    private String kindOf(Entity e) {
        return e.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
    }

    private Player ownerOf(Entity e) {
        String id = e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return id == null ? null : Bukkit.getPlayer(UUID.fromString(id));
    }

    private boolean hasOwner(Entity e) {
        return e.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    private void tag(Entity e, String kind, String name, NamedTextColor color) {
        e.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind);
        e.customName(Component.text(name, color));
        e.setCustomNameVisible(true);
        tracked.add(e.getUniqueId());
    }

    private void setAttr(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance a = e.getAttribute(attribute);
        if (a != null) a.setBaseValue(value);
    }

    private void consume(Player p, ItemStack held) {
        held.setAmount(held.getAmount() - 1);
        p.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);
    }

    private static boolean isSoulBlock(Material m) {
        return m == Material.SOUL_SOIL || m == Material.SOUL_SAND;
    }

    private int count(World world, String kind) {
        int n = 0;
        for (UUID id : tracked) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.getWorld().equals(world) && kind.equals(kindOf(e)) && !e.isDead()) n++;
        }
        return n;
    }

    // ---------------- spawning ----------------

    /** Spawns one of the creatures (also used by /smpweapons spawn). Returns false for an unknown name. */
    public boolean spawn(String kind, Location at) {
        switch (kind) {
            case BLUB -> at.getWorld().spawn(at, Rabbit.class, r -> {
                tag(r, BLUB, "Blub", NamedTextColor.LIGHT_PURPLE);
                r.setRabbitType(Rabbit.Type.WHITE);
            });
            case SIFTER -> at.getWorld().spawn(at, Wolf.class, w -> {
                tag(w, SIFTER, "Sifter", NamedTextColor.RED);
                setAttr(w, Attribute.MOVEMENT_SPEED, 0.5);
                setAttr(w, Attribute.ATTACK_DAMAGE, 5.0);
                setAttr(w, Attribute.MAX_HEALTH, 24.0);
                w.setHealth(24.0);
                w.setAngry(true);
            });
            case SNIFFER -> at.getWorld().spawn(at, Sniffer.class, s -> tag(s, SNIFFER, "Sniffer", NamedTextColor.GREEN));
            case GOLEM -> at.getWorld().spawn(at, IronGolem.class, g -> tag(g, GOLEM, "Echo Golem", NamedTextColor.AQUA));
            case WARDEN -> at.getWorld().spawn(at, Warden.class, w -> {
                tag(w, WARDEN, "Twisted Warden", NamedTextColor.DARK_PURPLE);
                setAttr(w, Attribute.MAX_HEALTH, 500.0);
                w.setHealth(500.0);
                w.setPersistent(true);
            });
            default -> {
                return false;
            }
        }
        return true;
    }

    private Location spawnPoint(Player p) {
        World world = p.getWorld();
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 20 + random.nextDouble() * 12;
        Location loc = new Location(world, p.getLocation().getX() + Math.cos(angle) * dist, 0,
                p.getLocation().getZ() + Math.sin(angle) * dist);
        loc.setY(world.getHighestBlockYAt(loc) + 1);
        if (loc.clone().subtract(0, 1, 0).getBlock().getType() == Material.WATER) return null;
        return loc;
    }

    private void spawnTick() {
        World world = SiftWorld.get(plugin);
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            Biome biome = p.getLocation().getBlock().getBiome();
            Location at = spawnPoint(p);
            if (at == null) continue;

            if (biome.equals(Biome.CHERRY_GROVE)) {
                if (count(world, BLUB) < 12 && random.nextDouble() < 0.6) {
                    for (int i = 0; i < 2 + random.nextInt(2); i++) spawn(BLUB, at);
                }
            } else if (biome.equals(Biome.PALE_GARDEN)) {
                if (count(world, SIFTER) < 15 && random.nextDouble() < 0.4) {
                    for (int i = 0; i < 1 + random.nextInt(5); i++) spawn(SIFTER, at);
                }
            } else if (biome.equals(Biome.DEEP_DARK)) {
                if (count(world, SNIFFER) + count(world, DARK_SNIFFER) < 4 && random.nextDouble() < 0.3) spawn(SNIFFER, at);
                else if (count(world, GOLEM) < 3 && random.nextDouble() < 0.15) spawn(GOLEM, at);
                else if (count(world, WARDEN) < 1 && random.nextDouble() < 0.1) {
                    spawn(WARDEN, at);
                    for (Player near : world.getNearbyPlayers(at, 120)) {
                        near.sendMessage(Component.text("The Twisted Warden awakens...", NamedTextColor.DARK_PURPLE));
                    }
                }
            }
        }
    }

    // ---------------- interactions ----------------

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        String kind = kindOf(entity);
        if (kind == null) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Material m = held.getType();
        Location head = entity.getLocation().add(0, 1, 0);

        switch (kind) {
            case BLUB -> {
                if (!isSoulBlock(m) || hasOwner(entity)) return;
                event.setCancelled(true);
                consume(player, held);
                if (random.nextInt(3) == 0) {
                    entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
                    ((Mob) entity).setPersistent(true);
                    entity.customName(Component.text("Blub (" + player.getName() + ")", NamedTextColor.LIGHT_PURPLE));
                    entity.getWorld().spawnParticle(Particle.HEART, head, 8, 0.4, 0.4, 0.4);
                    player.sendMessage(Component.text("The Blub is tamed!", NamedTextColor.GREEN));
                } else {
                    entity.getWorld().spawnParticle(Particle.SMOKE, head, 8, 0.3, 0.3, 0.3, 0.02);
                }
            }
            case GOLEM -> {
                if (m != Material.ECHO_SHARD) return;
                event.setCancelled(true);
                consume(player, held);
                entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
                ((Mob) entity).setPersistent(true);
                entity.customName(Component.text("Echo Golem (" + player.getName() + ")", NamedTextColor.AQUA));
                entity.getWorld().spawnParticle(Particle.HEART, head, 8, 0.5, 0.5, 0.5);
                player.sendMessage(Component.text("The Echo Golem is bonded to you. Blow a Goat Horn to send it mining.", NamedTextColor.GREEN));
            }
            case SINGER -> {
                event.setCancelled(true);
                if (!isSoulBlock(m)) return;
                consume(player, held);
                entity.getWorld().spawnParticle(Particle.HEART, head, 10, 0.4, 0.4, 0.4);
                entity.getWorld().spawnParticle(Particle.NOTE, head, 12, 0.5, 0.5, 0.5);
                if (random.nextDouble() < 0.25) {
                    player.getInventory().addItem(items.riftKey()).values()
                            .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                    player.sendMessage(Component.text("The Singer gave you a Rift Key!", NamedTextColor.LIGHT_PURPLE));
                }
            }
            default -> { }
        }
    }

    /** Goat Horn: sends your bonded Echo Golems mining. */
    @EventHandler
    public void onHorn(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.GOAT_HORN) return;
        Player player = event.getPlayer();

        // Echo Golems.
        long end = System.currentTimeMillis() + 60_000L;
        int sent = 0;
        for (UUID id : tracked) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !GOLEM.equals(kindOf(e))) continue;
            String owner = e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (!player.getUniqueId().toString().equals(owner)) continue;
            if (!e.getWorld().equals(player.getWorld()) || e.getLocation().distance(player.getLocation()) > 64) continue;
            mining.put(id, end);
            sent++;
        }
        if (sent > 0) {
            player.sendMessage(Component.text(sent + " Echo Golem(s) start mining Soul Blocks for you.", NamedTextColor.AQUA));
        }
    }

    // ---------------- per-tick behavior ----------------

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<UUID> it = tracked.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = Bukkit.getEntity(id);
            if (e == null) continue;
            if (e.isDead() || !e.isValid()) {
                it.remove();
                mining.remove(id);
                sculkTicks.remove(id);
                continue;
            }
            String kind = kindOf(e);
            if (kind == null || !(e instanceof Mob mob)) continue;
            switch (kind) {
                case BLUB -> tickBlub(mob);
                case GOLEM -> tickGolem(mob, now);
                case SIFTER -> tickSifter(mob);
                case SNIFFER -> tickSniffer(mob);
                case DARK_SNIFFER -> tickDark(mob, now);
                default -> { }
            }
        }
    }

    private Player nearestPlayer(Entity e, double range) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : e.getWorld().getNearbyPlayers(e.getLocation(), range)) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            double d = p.getLocation().distanceSquared(e.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private void tickBlub(Mob blub) {
        Player owner = ownerOf(blub);
        if (owner == null) return;
        if (!owner.getWorld().equals(blub.getWorld()) || blub.getLocation().distance(owner.getLocation()) > 25) {
            blub.teleport(owner.getLocation());
        } else if (blub.getLocation().distance(owner.getLocation()) > 6) {
            blub.getPathfinder().moveTo(owner.getLocation(), 1.3);
        }
        // Defend the owner.
        for (Entity near : blub.getNearbyEntities(6, 3, 6)) {
            if (near instanceof Monster monster) {
                monster.damage(2.0, blub);
                blub.getWorld().spawnParticle(Particle.CRIT, monster.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.1);
                break;
            }
        }
    }

    private void tickGolem(Mob golem, long now) {
        Long end = mining.get(golem.getUniqueId());
        if (end == null) return;
        Player owner = ownerOf(golem);
        if (owner == null || now > end) {
            mining.remove(golem.getUniqueId());
            return;
        }
        Block target = null;
        double best = Double.MAX_VALUE;
        Location base = golem.getLocation();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -10; dz <= 10; dz++) {
                    Block b = base.getWorld().getBlockAt(base.getBlockX() + dx, base.getBlockY() + dy, base.getBlockZ() + dz);
                    if (!isSoulBlock(b.getType())) continue;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < best) {
                        best = d;
                        target = b;
                    }
                }
            }
        }
        if (target == null) {
            mining.remove(golem.getUniqueId());
            owner.sendMessage(Component.text("Your Echo Golem found no more Soul Blocks nearby.", NamedTextColor.GRAY));
            return;
        }
        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        if (base.distanceSquared(center) <= 12) {
            Material type = target.getType();
            target.setType(Material.AIR);
            owner.getInventory().addItem(new ItemStack(type)).values()
                    .forEach(rest -> owner.getWorld().dropItemNaturally(owner.getLocation(), rest));
            golem.getWorld().spawnParticle(Particle.SCULK_SOUL, center, 8, 0.3, 0.3, 0.3, 0.02);
        } else {
            golem.getPathfinder().moveTo(center, 1.2);
        }
    }

    private void tickSifter(Mob sifter) {
        Player target = nearestPlayer(sifter, 25);
        if (target != null) {
            if (sifter instanceof Wolf wolf) wolf.setAngry(true);
            sifter.setTarget(target);
        }
    }

    private void tickSniffer(Mob sniffer) {
        UUID id = sniffer.getUniqueId();
        Block below = sniffer.getLocation().clone().subtract(0, 0.5, 0).getBlock();
        if (below.getType() == Material.SCULK) {
            int ticks = sculkTicks.getOrDefault(id, 0) + 10;
            sculkTicks.put(id, ticks);
            if (ticks >= 600) mutate(sniffer);
        } else {
            sculkTicks.remove(id);
        }
    }

    /** A Sniffer that digs on Sculk for 30 seconds turns into a hostile Dark Sniffer. */
    private void mutate(Mob sniffer) {
        sculkTicks.remove(sniffer.getUniqueId());
        sniffer.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, DARK_SNIFFER);
        sniffer.customName(Component.text("Dark Sniffer", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        setAttr(sniffer, Attribute.MAX_HEALTH, 80.0);
        sniffer.setHealth(80.0);
        setAttr(sniffer, Attribute.MOVEMENT_SPEED, 0.3);
        Location at = sniffer.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_WARDEN_ROAR, 1.5f, 1.2f);
        at.getWorld().spawnParticle(Particle.SCULK_SOUL, at.clone().add(0, 1, 0), 40, 0.8, 0.8, 0.8, 0.05);
    }

    private void tickDark(Mob dark, long now) {
        Player target = nearestPlayer(dark, 28);
        if (target == null) return;
        dark.setTarget(target);
        double dist = target.getLocation().distance(dark.getLocation());
        dark.getPathfinder().moveTo(target.getLocation(), 1.5);

        if (dist < 3.5 && now - biteTime.getOrDefault(dark.getUniqueId(), 0L) > 1000) {
            biteTime.put(dark.getUniqueId(), now);
            target.damage(6.0, dark);
        }
        // Sonic Sniff: drag the target toward the Dark Sniffer.
        if (dist > 6 && dist < 25 && now - sonicTime.getOrDefault(dark.getUniqueId(), 0L) > 8000) {
            sonicTime.put(dark.getUniqueId(), now);
            Vector pull = dark.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.2).setY(0.3);
            target.setVelocity(pull);
            dark.getWorld().playSound(dark.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 1.0f);
            dark.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation().add(0, 1, 0), 1);
        }
    }

    // ---------------- drops ----------------

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        String kind = kindOf(event.getEntity());
        if (kind == null) return;
        tracked.remove(event.getEntity().getUniqueId());
        List<ItemStack> drops = event.getDrops();
        switch (kind) {
            case SIFTER -> drops.add(named(Material.COOKED_BEEF, 1 + random.nextInt(3),
                    "Raw Sifter Meat", NamedTextColor.RED, "Restores a lot of hunger."));
            case DARK_SNIFFER -> drops.add(named(Material.BEETROOT_SEEDS, 1 + random.nextInt(3),
                    "Sculkflower Seeds", NamedTextColor.DARK_AQUA, "Rare seeds from a Dark Sniffer."));
            case WARDEN -> {
                List<ItemStack> set = items.siftiteSet();
                drops.add(set.get(random.nextInt(set.size())));
                drops.add(set.get(random.nextInt(set.size())));
                drops.add(new ItemStack(Material.TOTEM_OF_UNDYING));
                Bukkit.broadcast(Component.text("The Twisted Warden has been defeated!", NamedTextColor.GOLD));
            }
            default -> { }
        }
    }

    private ItemStack named(Material material, int amount, String name, NamedTextColor color, String lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (kindOf(e) != null) tracked.add(e.getUniqueId());
        }
    }
}
