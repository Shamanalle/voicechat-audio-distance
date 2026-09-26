package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.RayBundle;
import com.kasper.vcdistance.VoxelRay;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundGroup;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Acoustic ray casting through blocks with the Bukkit API. Mirrors the Fabric version: a block only
 * counts when the ray crosses its collision shape, and materials are classified the same way.
 * Main thread only; unloaded chunks are never loaded, they count as open air.
 */
final class BlockAcoustics {

    /** Longest ray walked, in blocks (the tracer never asks for more than 160). */
    private static final int MAX_BLOCKS = 512;

    private static final Map<Material, AcousticMaterial> MATERIALS = new HashMap<>();

    private BlockAcoustics() {
    }

    /** Acoustic thickness (in stone blocks) along one straight ray, using {@code weights} per material. */
    static double traceRay(World world, double fromX, double fromY, double fromZ, double toX, double toY, double toZ,
                           DistanceConfig weights) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        double[] thickness = {0.0};
        VoxelRay.walk(fromX, fromY, fromZ, toX, toY, toZ, MAX_BLOCKS, (x, y, z) -> {
            if (y < minY || y >= maxY || !world.isChunkLoaded(x >> 4, z >> 4)) {
                return false;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (block.isLiquid() || isWaterlogged(block)) {
                thickness[0] += weights.getMaterialWeight(AcousticMaterial.LIQUID);
            }
            if (!type.isAir() && !block.isLiquid() && crosses(block, type, fromX, fromY, fromZ, toX, toY, toZ)) {
                thickness[0] += weights.getMaterialWeight(MATERIALS.computeIfAbsent(type, m -> classify(block)));
            }
            return thickness[0] >= RayBundle.MAX_RAY_THICKNESS;
        });
        return thickness[0];
    }

    /** Block tags can change with data packs, so the material cache is dropped on settings reload. */
    static void clearCache() {
        MATERIALS.clear();
    }

    private static boolean isWaterlogged(Block block) {
        BlockData data = block.getBlockData();
        return data instanceof Waterlogged w && w.isWaterlogged();
    }

    /** Whether the ray crosses the block's collision shape (slabs, open doors and carpets by their real size). */
    private static boolean crosses(Block block, Material type, double fromX, double fromY, double fromZ,
                                   double toX, double toY, double toZ) {
        if (type.isOccluding()) {
            return true; // full opaque cube: the ray is inside the voxel, so it crosses it
        }
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
            // Collision boxes are relative to the block
            if (VoxelRay.intersects(fromX, fromY, fromZ, toX, toY, toZ,
                    bx + box.getMinX(), by + box.getMinY(), bz + box.getMinZ(),
                    bx + box.getMaxX(), by + box.getMaxY(), bz + box.getMaxZ())) {
                return true;
            }
        }
        return false;
    }

    static AcousticMaterial classify(Block block) {
        Material m = block.getType();
        if (Tag.WOOL.isTagged(m) || Tag.WOOL_CARPETS.isTagged(m)) {
            return AcousticMaterial.WOOL;
        }
        if (Tag.LEAVES.isTagged(m)) {
            return AcousticMaterial.LEAVES;
        }
        if (Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m)) {
            return AcousticMaterial.DOOR;
        }
        if (Tag.FENCES.isTagged(m) || Tag.FENCE_GATES.isTagged(m)) {
            return AcousticMaterial.THIN;
        }
        String name = m.name().toLowerCase(Locale.ROOT);
        Sound sound = breakSound(block);
        // Ice sounds like glass, so it is checked first
        if (Tag.ICE.isTagged(m)) {
            return AcousticMaterial.ICE;
        }
        if (name.contains("glass") || isGlass(sound)) {
            return AcousticMaterial.GLASS;
        }
        if (name.endsWith("_bars")) {
            return AcousticMaterial.THIN;
        }
        if (isMetal(sound) || name.contains("copper") || name.equals("iron_block") || name.equals("gold_block")
                || name.equals("netherite_block") || name.endsWith("anvil")) {
            return AcousticMaterial.METAL;
        }
        if (Tag.LOGS.isTagged(m) || Tag.PLANKS.isTagged(m) || Tag.MINEABLE_AXE.isTagged(m) || name.contains("bamboo")
                || isWood(sound)) {
            return AcousticMaterial.WOOD;
        }
        // Everything else by the tool that mines it
        if (Tag.MINEABLE_HOE.isTagged(m)) {
            return AcousticMaterial.SOFT;
        }
        if (Tag.MINEABLE_SHOVEL.isTagged(m)) {
            return AcousticMaterial.EARTH;
        }
        if (Tag.MINEABLE_PICKAXE.isTagged(m)) {
            return AcousticMaterial.STONE;
        }
        return AcousticMaterial.OTHER;
    }

    // Sound was an enum and became a registry interface in 1.21.3, so sounds are compared through
    // Objects.equals (no enum-only bytecode) and every use is guarded: when it fails, the tag and
    // name checks above still classify the block.

    private static Sound breakSound(Block block) {
        try {
            SoundGroup group = block.getBlockData().getSoundGroup();
            return group.getBreakSound();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isGlass(Sound sound) {
        try {
            return sound != null && Objects.equals(sound, Sound.BLOCK_GLASS_BREAK);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isMetal(Sound sound) {
        try {
            return sound != null && (Objects.equals(sound, Sound.BLOCK_METAL_BREAK)
                    || Objects.equals(sound, Sound.BLOCK_COPPER_BREAK)
                    || Objects.equals(sound, Sound.BLOCK_NETHERITE_BLOCK_BREAK)
                    || Objects.equals(sound, Sound.BLOCK_ANVIL_BREAK));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isWood(Sound sound) {
        try {
            return sound != null && (Objects.equals(sound, Sound.BLOCK_WOOD_BREAK)
                    || Objects.equals(sound, Sound.BLOCK_NETHER_WOOD_BREAK)
                    || Objects.equals(sound, Sound.BLOCK_BAMBOO_WOOD_BREAK)
                    || Objects.equals(sound, Sound.BLOCK_CHERRY_WOOD_BREAK));
        } catch (Throwable t) {
            return false;
        }
    }
}
