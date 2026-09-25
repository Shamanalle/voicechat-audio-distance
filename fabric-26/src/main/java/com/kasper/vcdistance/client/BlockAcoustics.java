package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.DistanceConfig;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acoustic ray casting through blocks for Minecraft 26.x, used by the client and the server.
 * <p>
 * Every voxel the ray passes through counts. Blocks that do not occlude light (glass, leaves, doors,
 * fences) are included explicitly through their material class.
 */
public final class BlockAcoustics {

    /** Marker for blocks that do not stop sound (ConcurrentHashMap cannot store null). */
    private static final Object NONE = new Object();
    private static final Map<BlockState, Object> MATERIALS = new ConcurrentHashMap<>();

    private BlockAcoustics() {
    }

    /** Acoustic thickness (in stone blocks) along one straight ray, using {@code weights} per material. */
    public static double traceRay(BlockGetter level, Vec3 from, Vec3 to, DistanceConfig weights) {
        if (level == null) {
            return 0.0;
        }
        double[] thickness = {0.0};
        BlockGetter.traverseBlocks(from, to, thickness, (acc, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!level.getFluidState(pos).isEmpty()) {
                acc[0] += weights.getMaterialWeight(AcousticMaterial.LIQUID);
            }
            if (!state.isAir()) {
                Object material = MATERIALS.computeIfAbsent(state, s -> {
                    AcousticMaterial m = classify(s);
                    return m == null ? NONE : m;
                });
                if (material instanceof AcousticMaterial m) {
                    acc[0] += weights.getMaterialWeight(m);
                }
            }
            return acc[0] >= WorldAccess.MAX_RAY_THICKNESS ? Boolean.TRUE : null;
        }, acc -> null);
        return thickness[0];
    }

    /** Block tags can differ between servers, so the material cache is dropped on world change. */
    public static void clearCache() {
        MATERIALS.clear();
    }

    /**
     * @return the material, or {@code null} for blocks that do not stop sound (plants, carpets, rails...)
     */
    static AcousticMaterial classify(BlockState state) {
        Block block = state.getBlock();
        if (state.is(BlockTags.WOOL)) {
            return AcousticMaterial.WOOL;
        }
        if (state.is(BlockTags.LEAVES)) {
            return AcousticMaterial.LEAVES;
        }
        if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)) {
            return AcousticMaterial.DOOR;
        }
        if (block instanceof TransparentBlock || block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock) {
            return AcousticMaterial.GLASS;
        }
        if (block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof IronBarsBlock) {
            return AcousticMaterial.THIN;
        }
        if (!state.canOcclude()) {
            return null;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
            return AcousticMaterial.WOOD;
        }
        return AcousticMaterial.STONE;
    }
}
