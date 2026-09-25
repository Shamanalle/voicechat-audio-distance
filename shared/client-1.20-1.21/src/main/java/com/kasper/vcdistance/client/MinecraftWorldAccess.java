package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.SpeakerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * World access for Minecraft 1.20 - 1.21.x.
 * <p>
 * A block only counts when the ray actually crosses its collision shape, so slabs, open doors,
 * fences and carpets are treated by their real geometry rather than as full cubes.
 */
public final class MinecraftWorldAccess implements WorldAccess {

    private static final long ENTITY_SEARCH_INTERVAL = TimeUnit.SECONDS.toNanos(1);

    private final Map<BlockState, AcousticMaterial> materials = new ConcurrentHashMap<>();

    @Override
    public boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null;
    }

    @Override
    public Object worldIdentity() {
        return Minecraft.getInstance().level;
    }

    /**
     * The player's eyes. {@code Camera.getPosition()} and {@code Entity.position()} are not available
     * on every 1.21.x release (removed in 1.21.11 and 1.21.9), {@code getEyePosition()} is.
     */
    @Override
    public Vec3 listenerPosition() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getEyePosition() : null;
    }

    @Override
    public Vec3 entitySpeakerPosition(SpeakerRegistry.Speaker speaker, long nowNanos) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || speaker.getEntityId() == null) {
            return null;
        }
        Entity entity = null;
        int cachedId = speaker.getCachedEntityNetworkId();
        if (cachedId != Integer.MIN_VALUE) {
            Entity candidate = level.getEntity(cachedId);
            if (candidate != null && speaker.getEntityId().equals(candidate.getUUID())) {
                entity = candidate;
            }
        }
        if (entity == null) {
            entity = level.getPlayerByUUID(speaker.getEntityId());
        }
        if (entity == null && mc.player != null && nowNanos - speaker.getLastEntitySearchNanos() > ENTITY_SEARCH_INTERVAL) {
            speaker.setLastEntitySearchNanos(nowNanos);
            double r = Math.max(16.0, speaker.getMaxDistance()) + 8.0;
            Vec3 p = mc.player.getEyePosition();
            List<Entity> found = level.getEntities((Entity) null, new AABB(p.x - r, p.y - r, p.z - r, p.x + r, p.y + r, p.z + r),
                    e -> speaker.getEntityId().equals(e.getUUID()));
            entity = found.isEmpty() ? null : found.get(0);
        }
        if (entity == null) {
            return null;
        }
        speaker.setCachedEntityNetworkId(entity.getId());
        if (speaker.getDisplayName() == null) {
            speaker.setDisplayName(entity.getName().getString());
        }
        return entity.getEyePosition();
    }

    @Override
    public double traceRay(Vec3 from, Vec3 to) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return 0.0;
        }
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        double[] thickness = {0.0};
        BlockGetter.traverseBlocks(from, to, thickness, (acc, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                acc[0] += config.getMaterialWeight(AcousticMaterial.LIQUID);
            }
            if (!state.isAir()) {
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (!shape.isEmpty() && shape.clip(from, to, pos) != null) {
                    acc[0] += config.getMaterialWeight(materials.computeIfAbsent(state, MinecraftWorldAccess::classify));
                }
            }
            return acc[0] >= MAX_RAY_THICKNESS ? Boolean.TRUE : null;
        }, acc -> null);
        return thickness[0];
    }

    @Override
    public void reset() {
        materials.clear();
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
        SoundType sound = state.getSoundType();
        if (sound == SoundType.GLASS) {
            return AcousticMaterial.GLASS;
        }
        if (state.getBlock() instanceof IronBarsBlock) {
            return AcousticMaterial.THIN;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)
                || sound == SoundType.WOOD || sound == SoundType.NETHER_WOOD
                || sound == SoundType.BAMBOO_WOOD || sound == SoundType.CHERRY_WOOD) {
            return AcousticMaterial.WOOD;
        }
        return AcousticMaterial.STONE;
    }
}
