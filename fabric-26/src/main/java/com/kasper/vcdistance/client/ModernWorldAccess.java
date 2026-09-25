package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.SpeakerRegistry;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * World access for Minecraft 26.x.
 * <p>
 * Every voxel the ray passes through counts. Blocks that do not occlude light (glass, leaves, doors,
 * fences) are included explicitly through their material class.
 */
public final class ModernWorldAccess implements WorldAccess {

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

    @Override
    public Vec3 listenerPosition() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getEyePosition() : null;
    }

    @Override
    public Vec3 entitySpeakerPosition(SpeakerRegistry.Speaker speaker, long nowNanos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || speaker.getEntityId() == null) {
            return null;
        }
        Entity entity = mc.level.getPlayerByUUID(speaker.getEntityId());
        if (entity == null && nowNanos - speaker.getLastEntitySearchNanos() > ENTITY_SEARCH_INTERVAL) {
            speaker.setLastEntitySearchNanos(nowNanos);
            double r = Math.max(16.0, speaker.getMaxDistance()) + 8.0;
            Vec3 p = mc.player.getEyePosition();
            List<Entity> found = mc.level.getEntities((Entity) null, new AABB(p.x - r, p.y - r, p.z - r, p.x + r, p.y + r, p.z + r),
                    e -> speaker.getEntityId().equals(e.getUUID()));
            entity = found.isEmpty() ? null : found.get(0);
        }
        if (entity == null) {
            return null;
        }
        if (speaker.getDisplayName() == null) {
            speaker.setDisplayName(entity.getName().getString());
        }
        return entity.getEyePosition();
    }

    @Override
    public double traceRay(Vec3 from, Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        BlockGetter level = mc.level;
        if (level == null) {
            return 0.0;
        }
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        double[] thickness = {0.0};
        BlockGetter.traverseBlocks(from, to, thickness, (acc, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!level.getFluidState(pos).isEmpty()) {
                acc[0] += config.getMaterialWeight(AcousticMaterial.LIQUID);
            }
            if (!state.isAir()) {
                AcousticMaterial material = materials.computeIfAbsent(state, ModernWorldAccess::classify);
                if (material != null) {
                    acc[0] += config.getMaterialWeight(material);
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

    /**
     * @return the material, or {@code null} for blocks that do not stop sound (plants, carpets, rails...)
     */
    private static AcousticMaterial classify(BlockState state) {
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
