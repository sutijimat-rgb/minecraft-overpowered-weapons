package com.smpweapons;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.List;
import java.util.Random;

/**
 * The Sift, approximated with normal blocks: pink meadows, white-leaved forests with black trunks,
 * soul canyons with sculk and monoliths, and ichor lakes that heal you.
 */
public final class SiftWorld {

    private SiftWorld() {}

    private static final int MEADOW = 0;
    private static final int FOREST = 1;
    private static final int CANYON = 2;
    private static final int LAKE = 3;

    private static String nameOf(SmpWeaponsPlugin plugin) {
        return plugin.getConfig().getString("sift.world-name", "sift");
    }

    /** Loads (or creates) the Sift world. Returns null if it is turned off in the config. */
    public static World create(SmpWeaponsPlugin plugin) {
        if (!plugin.getConfig().getBoolean("sift.enabled", true)) return null;
        String name = nameOf(plugin);
        World world = Bukkit.getWorld(name);
        if (world == null) {
            Generator generator = new Generator();
            world = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .generator(generator)
                    .biomeProvider(new Biomes(generator))
                    .generateStructures(false)
                    .createWorld();
        }
        if (world != null) {
            World sift = world;
            Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 190, 60), 1.2f);
            // Ichor lakes: standing in the water heals you.
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player player : sift.getPlayers()) {
                    if (player.isInWater()) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1, true, true, true));
                        sift.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 4, 0.4, 0.5, 0.4, 0, gold);
                    }
                }
            }, 20L, 10L);
        }
        return world;
    }

    public static World get(SmpWeaponsPlugin plugin) {
        return Bukkit.getWorld(nameOf(plugin));
    }

    private static final class Biomes extends BiomeProvider {
        private final Generator generator;

        Biomes(Generator generator) {
            this.generator = generator;
        }

        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return switch (generator.regionAt(info.getSeed(), x, z)) {
                case FOREST -> Biome.PALE_GARDEN;
                case CANYON -> Biome.DEEP_DARK;
                case LAKE -> Biome.WARM_OCEAN;
                default -> Biome.CHERRY_GROVE;
            };
        }

        @Override
        public List<Biome> getBiomes(WorldInfo info) {
            return List.of(Biome.CHERRY_GROVE, Biome.PALE_GARDEN, Biome.DEEP_DARK, Biome.WARM_OCEAN);
        }
    }

    private static final class Generator extends ChunkGenerator {
        private SimplexOctaveGenerator hills;
        private SimplexOctaveGenerator patches;

        private synchronized void init(long seed) {
            if (hills != null) return;
            patches = new SimplexOctaveGenerator(new Random(seed + 7), 3);
            patches.setScale(1.0 / 70.0);
            SimplexOctaveGenerator h = new SimplexOctaveGenerator(new Random(seed), 6);
            h.setScale(1.0 / 110.0);
            hills = h;
        }

        private int heightAt(int wx, int wz) {
            return (int) (72 + hills.noise(wx, wz, 0.5, 0.5, true) * 26);
        }

        int regionAt(long seed, int wx, int wz) {
            init(seed);
            if (heightAt(wx, wz) < 62) return LAKE;
            double p = patches.noise(wx, wz, 0.5, 0.5, true);
            if (p > 0.35) return CANYON;
            if (p < -0.35) return FOREST;
            return MEADOW;
        }

        @Override
        public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
            long seed = info.getSeed();
            init(seed);
            int min = info.getMinHeight();
            int[][] heights = new int[16][16];
            int[][] regions = new int[16][16];
            Random r = new Random(seed * 31 + chunkX * 341873128712L + chunkZ * 132897987541L);

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wx = chunkX * 16 + x;
                    int wz = chunkZ * 16 + z;
                    int height = heightAt(wx, wz);
                    int region = regionAt(seed, wx, wz);
                    heights[x][z] = height;
                    regions[x][z] = region;

                    // Siftslate (deepslate) stone with a tuff layer under the surface.
                    data.setBlock(x, min, z, Material.BEDROCK);
                    data.setRegion(x, min + 1, z, x + 1, height - 3, z + 1, Material.DEEPSLATE);
                    data.setRegion(x, height - 3, z, x + 1, height, z + 1, Material.TUFF);

                    switch (region) {
                        case FOREST -> data.setBlock(x, height, z, Material.PALE_MOSS_BLOCK);
                        case CANYON -> data.setBlock(x, height, z, r.nextInt(3) == 0 ? Material.SCULK : Material.SOUL_SOIL);
                        case LAKE -> {
                            data.setBlock(x, height, z, Material.CALCITE);
                            // Ichor lake: water that heals you (see SiftWorld.create).
                            data.setRegion(x, height + 1, z, x + 1, 63, z + 1, Material.WATER);
                        }
                        default -> {
                            data.setBlock(x, height, z, Material.GRASS_BLOCK);
                            // Pink meadow: pink petals and flowers.
                            int roll = r.nextInt(9);
                            if (roll == 0) data.setBlock(x, height + 1, z, Material.PINK_PETALS);
                            else if (roll == 1) data.setBlock(x, height + 1, z, Material.PINK_TULIP);
                            else if (roll == 2) data.setBlock(x, height + 1, z, Material.ALLIUM);
                        }
                    }
                }
            }

            // Forest: black-trunked trees with white leaves, and dripstone at their feet.
            for (int attempt = 0; attempt < 4; attempt++) {
                int tx = 3 + r.nextInt(10);
                int tz = 3 + r.nextInt(10);
                if (regions[tx][tz] != FOREST) continue;
                int base = heights[tx][tz];
                int tall = 6 + r.nextInt(5);
                data.setRegion(tx, base + 1, tz, tx + 1, base + tall + 1, tz + 1, Material.BLACKSTONE);
                for (int dy = tall - 2; dy <= tall + 2; dy++) {
                    int radius = dy >= tall + 1 ? 1 : 2;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 2) continue;
                            if (dx == 0 && dz == 0 && dy <= tall) continue;
                            data.setBlock(tx + dx, base + dy, tz + dz, Material.PALE_OAK_LEAVES);
                        }
                    }
                }
                int ox = tx + (r.nextBoolean() ? 2 : -2);
                if (regions[ox][tz] == FOREST) {
                    data.setBlock(ox, heights[ox][tz] + 1, tz, Material.POINTED_DRIPSTONE);
                }
            }

            // Canyon monoliths.
            if (r.nextInt(4) == 0) {
                int px = 2 + r.nextInt(12);
                int pz = 2 + r.nextInt(12);
                if (regions[px][pz] == CANYON) {
                    int base = heights[px][pz];
                    int tall = 8 + r.nextInt(10);
                    data.setRegion(px - 1, base + 1, pz - 1, px + 2, base + tall, pz + 2, Material.DEEPSLATE);
                    data.setBlock(px, base + tall, pz, Material.SCULK_CATALYST);
                }
            }
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            init(world.getSeed());
            return new Location(world, 0.5, Math.max(heightAt(0, 0), 62) + 1, 0.5);
        }
    }
}
