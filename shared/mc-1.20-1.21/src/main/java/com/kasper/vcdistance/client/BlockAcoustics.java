package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.RayBundle;
import com.kasper.vcdistance.VoxelRay;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acoustic ray casting through blocks for Minecraft 1.20 - 1.21.x, used by the client and the server.
 * <p>
 * A block only counts when the ray actually crosses its collision shape, so slabs, open doors,
 * fences and carpets are treated by their real geometry rather than as full cubes. A ray that only
 * grazes a block's corner counts it by the short way it runs inside; doors, trapdoors, fences and
 * bars count fully whenever they are crossed, since they are thin by nature.
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
                    AcousticMaterial material = MATERIALS.computeIfAbsent(state, BlockAcoustics::classify);
                    acc[0] += weights.getMaterialWeight(material) * share(material, shape.bounds(), pos, from, to);
                }
            }
            return acc[0] >= WorldAccess.MAX_RAY_THICKNESS ? Boolean.TRUE : null;
        }, acc -> null);
        return thickness[0];
    }

    /** How much of the block's weight the ray takes: grazing a corner counts less than crossing it. */
    static double share(AcousticMaterial material, AABB box, BlockPos pos, Vec3 from, Vec3 to) {
        if (material == AcousticMaterial.DOOR || material == AcousticMaterial.THIN) {
            return 1.0;
        }
        return RayBundle.chordWeight(VoxelRay.chord(from.x, from.y, from.z, to.x, to.y, to.z,
                pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ,
                pos.getX() + box.maxX, pos.getY() + box.maxY, pos.getZ() + box.maxZ));
    }

    /** {@code true} when sound passes this block freely: air, water, open doors and gates, fences, bars. */
    public static boolean isOpenForSound(BlockGetter level, int x, int y, int z) {
        if (level == null) {
            return true;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
            return true;
        }
        return MATERIALS.computeIfAbsent(state, BlockAcoustics::classify) == AcousticMaterial.THIN;
    }

    /** The material of a surface an echo bounces off. */
    public static AcousticMaterial echoMaterial(BlockState state) {
        return MATERIALS.computeIfAbsent(state, BlockAcoustics::classify);
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
