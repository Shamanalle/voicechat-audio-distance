package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.DistanceConfig;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acoustic ray casting through blocks for Minecraft 1.20 - 1.21.x, used by the client and the server.
 * <p>
 * A block only counts when the ray actually crosses its collision shape, so slabs, open doors,
 * fences and carpets are treated by their real geometry rather than as full cubes.
 */
public final class BlockAcoustics {

    private static final Map<BlockState, AcousticMaterial> MATERIALS = new ConcurrentHashMap<>();

    private BlockAcoustics() {
    }

    /** Acoustic thickness (in stone blocks) along one straight ray, using {@code weights} per material. */
    public static double traceRay(Level level, Vec3 from, Vec3 to, DistanceConfig weights) {
        if (level == null) {
            return 0.0;
        }
        double[] thickness = {0.0};
        BlockGetter.traverseBlocks(from, to, thickness, (acc, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                acc[0] += weights.getMaterialWeight(AcousticMaterial.LIQUID);
            }
            if (!state.isAir()) {
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (!shape.isEmpty() && shape.clip(from, to, pos) != null) {
                    acc[0] += weights.getMaterialWeight(MATERIALS.computeIfAbsent(state, BlockAcoustics::classify));
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

    static AcousticMaterial classify(BlockState state) {
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) {
            return AcousticMaterial.WOOL;
        }
        if (state.is(BlockTags.LEAVES)) {
            return AcousticMaterial.LEAVES;
        }
        if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)) {
            return AcousticMaterial.DOOR;
        }
        if (state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)) {
            return AcousticMaterial.THIN;
        }
        // Ice sounds like glass, so it is checked first
        if (state.is(BlockTags.ICE)) {
            return AcousticMaterial.ICE;
        }
        SoundType sound = state.getSoundType();
        if (sound == SoundType.GLASS) {
            return AcousticMaterial.GLASS;
        }
        if (state.getBlock() instanceof IronBarsBlock) {
            return AcousticMaterial.THIN;
        }
        if (sound == SoundType.METAL || sound == SoundType.COPPER || sound == SoundType.NETHERITE_BLOCK
                || sound == SoundType.ANVIL) {
            return AcousticMaterial.METAL;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.MINEABLE_WITH_AXE)
                || sound == SoundType.WOOD || sound == SoundType.NETHER_WOOD
                || sound == SoundType.BAMBOO_WOOD || sound == SoundType.CHERRY_WOOD) {
            return AcousticMaterial.WOOD;
        }
        // Everything else by the tool that mines it
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return AcousticMaterial.SOFT;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return AcousticMaterial.EARTH;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return AcousticMaterial.STONE;
        }
        return AcousticMaterial.OTHER;
    }
}
