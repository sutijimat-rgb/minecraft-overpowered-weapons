package com.smpweapons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.List;
import java.util.Random;

/** The Sift: a dark sculk-covered world built from normal blocks. */
public final class SiftWorld {

    private SiftWorld() {}

    private static String nameOf(SmpWeaponsPlugin plugin) {
        return plugin.getConfig().getString("sift.world-name", "sift");
    }

    /** Loads (or creates) the Sift world. Returns null if it is turned off in the config. */
    public static World create(SmpWeaponsPlugin plugin) {
        if (!plugin.getConfig().getBoolean("sift.enabled", true)) return null;
        String name = nameOf(plugin);
        World world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .generator(new Generator())
                    .biomeProvider(new Biomes())
                    .generateStructures(false)
                    .createWorld();
        }
        if (world != null) {
            World sift = world;
            // Permanent night, like the mod's deep blue sky.
            Bukkit.getScheduler().runTaskTimer(plugin, () -> sift.setTime(18000L), 20L, 200L);
        }
        return world;
    }

    public static World get(SmpWeaponsPlugin plugin) {
        return Bukkit.getWorld(nameOf(plugin));
    }

    private static final class Biomes extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return Biome.DEEP_DARK;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(Biome.DEEP_DARK);
        }
    }

    private static final class Generator extends ChunkGenerator {
        private SimplexOctaveGenerator hills;
        private SimplexOctaveGenerator patches;

        private synchronized void init(long seed) {
            if (hills != null) return;
            hills = new SimplexOctaveGenerator(new Random(seed), 6);
            hills.setScale(1.0 / 110.0);
            patches = new SimplexOctaveGenerator(new Random(seed + 7), 3);
            patches.setScale(1.0 / 55.0);
        }

        private int heightAt(int wx, int wz) {
            return (int) (72 + hills.noise(wx, wz, 0.5, 0.5, true) * 26);
        }

        @Override
        public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
            init(info.getSeed());
            int min = info.getMinHeight();
            int[][] heights = new int[16][16];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wx = chunkX * 16 + x;
                    int wz = chunkZ * 16 + z;
                    int height = heightAt(wx, wz);
                    heights[x][z] = height;

                    data.setBlock(x, min, z, Material.BEDROCK);
                    data.setRegion(x, min + 1, z, x + 1, height - 3, z + 1, Material.DEEPSLATE);
                    data.setRegion(x, height - 3, z, x + 1, height, z + 1, Material.TUFF);

                    double p = patches.noise(wx, wz, 0.5, 0.5, true);
                    Material top = p > 0.35 ? Material.SOUL_SOIL : (p < -0.35 ? Material.MOSS_BLOCK : Material.SCULK);
                    data.setBlock(x, height, z, top);

                    // Lakes in the low spots.
                    if (height < 62) {
                        data.setRegion(x, height + 1, z, x + 1, 63, z + 1, Material.WATER);
                    }
                }
            }

            // An occasional monolith pillar.
            Random r = new Random(info.getSeed() * 31 + chunkX * 341873128712L + chunkZ * 132897987541L);
            if (r.nextInt(7) == 0) {
                int px = 2 + r.nextInt(12);
                int pz = 2 + r.nextInt(12);
                int base = Math.max(heights[px][pz], 62);
                int tall = 8 + r.nextInt(10);
                data.setRegion(px - 1, base + 1, pz - 1, px + 2, base + tall, pz + 2, Material.DEEPSLATE);
                data.setBlock(px, base + tall, pz, Material.SCULK_CATALYST);
            }
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            init(world.getSeed());
            return new Location(world, 0.5, Math.max(heightAt(0, 0), 62) + 1, 0.5);
        }
    }
}
