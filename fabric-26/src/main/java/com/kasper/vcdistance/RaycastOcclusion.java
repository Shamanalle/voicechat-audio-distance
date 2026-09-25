package com.kasper.vcdistance;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3D Voxel Raycasting and Acoustic Material Absorption Engine for Minecraft 26.3.
 */
public class RaycastOcclusion {

    public static final double MAX_OCCLUSION = 1.0;
    private static final long CACHE_TTL_MS = 40L;

    private static class CacheEntry {
        final long timestamp;
        final double occlusion;

        CacheEntry(long timestamp, double occlusion) {
            this.timestamp = timestamp;
            this.occlusion = occlusion;
        }
    }

    private static final Map<UUID, CacheEntry> OCCLUSION_CACHE = new ConcurrentHashMap<>();

    public static double getEntityOcclusion(UUID entityId, float maxDistance) {
        if (entityId == null) {
            return 0.0;
        }
        long now = System.currentTimeMillis();
        CacheEntry entry = OCCLUSION_CACHE.get(entityId);
        if (entry != null && (now - entry.timestamp) < CACHE_TTL_MS) {
            return entry.occlusion;
        }

        double occlusion = computeEntityOcclusion(entityId, maxDistance);
        OCCLUSION_CACHE.put(entityId, new CacheEntry(now, occlusion));
        return occlusion;
    }

    public static double getLocationalOcclusion(UUID channelId, double x, double y, double z) {
        long now = System.currentTimeMillis();
        if (channelId != null) {
            CacheEntry entry = OCCLUSION_CACHE.get(channelId);
            if (entry != null && (now - entry.timestamp) < CACHE_TTL_MS) {
                return entry.occlusion;
            }
        }

        double occlusion = computeLocationalOcclusion(new Vec3(x, y, z));
        if (channelId != null) {
            OCCLUSION_CACHE.put(channelId, new CacheEntry(now, occlusion));
        }
        return occlusion;
    }

    private static double computeEntityOcclusion(UUID entityId, float maxDistance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return 0.0;
        }

        try {
            Entity targetEntity = mc.level.getPlayerByUUID(entityId);
            if (targetEntity == null) {
                Vec3 playerPos = mc.player.position();
                double radius = maxDistance > 0 ? maxDistance + 2.0 : 48.0;
                AABB searchBox = new AABB(
                        playerPos.x - radius, playerPos.y - radius, playerPos.z - radius,
                        playerPos.x + radius, playerPos.y + radius, playerPos.z + radius
                );
                targetEntity = mc.level.getEntities((Entity) null, searchBox, e -> e.getUUID().equals(entityId))
                        .stream().findAny().orElse(null);
            }

            if (targetEntity == null || targetEntity == mc.player) {
                return 0.0;
            }

            Vec3 listenerEye = mc.player.getEyePosition();
            Vec3 speakerEye = targetEntity.getEyePosition();

            return traceOcclusionBetween(mc.level, listenerEye, speakerEye);
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Error computing entity occlusion for {}: {}", entityId, t.getMessage());
            return 0.0;
        }
    }

    private static double computeLocationalOcclusion(Vec3 soundPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return 0.0;
        }

        try {
            Vec3 listenerEye = mc.player.getEyePosition();
            return traceOcclusionBetween(mc.level, listenerEye, soundPos);
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Error computing locational occlusion for {}: {}", soundPos, t.getMessage());
            return 0.0;
        }
    }

    public static double traceOcclusionBetween(BlockGetter level, Vec3 from, Vec3 to) {
        if (level == null || from == null || to == null) {
            return 0.0;
        }

        double distance = from.distanceTo(to);
        if (distance < 0.1) {
            return 0.0;
        }

        final TraversalState state = new TraversalState();

        BlockGetter.traverseBlocks(
                from,
                to,
                state,
                (st, pos) -> {
                    BlockState blockState = level.getBlockState(pos);
                    if (!blockState.isAir() && blockState.canOcclude()) {
                        double absorption = getBlockAbsorption(blockState);
                        st.accumulatedAbsorption += absorption;
                        st.solidBlockCount++;
                    }

                    FluidState fluidState = level.getFluidState(pos);
                    if (!fluidState.isEmpty()) {
                        st.accumulatedAbsorption += 0.20;
                    }

                    if (st.accumulatedAbsorption >= 1.2 || st.solidBlockCount >= 5) {
                        return true;
                    }
                    return null;
                },
                st -> null
        );

        double total = state.accumulatedAbsorption;
        return Math.max(0.0, Math.min(MAX_OCCLUSION, total));
    }

    private static class TraversalState {
        double accumulatedAbsorption = 0.0;
        int solidBlockCount = 0;
    }

    public static double getBlockAbsorption(BlockState state) {
        if (state.isAir()) {
            return 0.0;
        }

        Block block = state.getBlock();

        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) {
            return 0.45;
        }
        if (block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof IronBarsBlock) {
            return 0.12;
        }
        if (block instanceof TransparentBlock || block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock) {
            return 0.18;
        }
        if (state.is(BlockTags.LEAVES)) {
            return 0.10;
        }
        if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)) {
            return 0.25;
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_BRICKS)) {
            return 0.35;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
            return 0.25;
        }

        return 0.30;
    }
}
