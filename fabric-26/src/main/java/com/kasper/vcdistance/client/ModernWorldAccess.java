package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.Bearing;
import com.kasper.vcdistance.EnvironmentEffects;
import com.kasper.vcdistance.NearbyPlayers;
import com.kasper.vcdistance.SpeakerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client world access for Minecraft 26.x.
 */
public final class ModernWorldAccess implements WorldAccess {

    private static final long ENTITY_SEARCH_INTERVAL = TimeUnit.SECONDS.toNanos(1);

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
    public double listenerYaw() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getYRot() : 0.0;
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
    public List<NearbyPlayers.Player> nearbyPlayers(Vec3 listener, double range) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (mc.level == null || self == null || listener == null) {
            return List.of();
        }
        List<NearbyPlayers.Player> list = new ArrayList<>();
        double yaw = self.getYRot();
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == self || (p.isSpectator() && !self.isSpectator()) || p.isInvisibleTo(self)) {
                continue;
            }
            Vec3 eye = p.getEyePosition();
            double d = listener.distanceTo(eye);
            if (d <= range) {
                list.add(new NearbyPlayers.Player(p.getUUID(), p.getName().getString(), d,
                        Bearing.relative(eye.x - listener.x, eye.z - listener.z, yaw)));
            }
        }
        return list;
    }

    @Override
    public double rayDistance(Vec3 from, double dx, double dy, double dz, double maxDistance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return -1.0;
        }
        Vec3 to = from.add(dx * maxDistance, dy * maxDistance, dz * maxDistance);
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS ? -1.0 : hit.getLocation().distanceTo(from);
    }

    @Override
    public boolean isOpenForSound(int x, int y, int z) {
        return BlockAcoustics.isOpenForSound(Minecraft.getInstance().level, x, y, z);
    }

    @Override
    public boolean isUnderWater(Vec3 point) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getFluidState(BlockPos.containing(point)).is(FluidTags.WATER);
    }

    @Override
    public EnvironmentEffects.Weather weatherAt(Vec3 point) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.isRainingAt(BlockPos.containing(point))) {
            return EnvironmentEffects.Weather.CLEAR;
        }
        return mc.level.isThundering() ? EnvironmentEffects.Weather.THUNDER : EnvironmentEffects.Weather.RAIN;
    }

    @Override
    public double traceRay(Vec3 from, Vec3 to) {
        return BlockAcoustics.traceRay(Minecraft.getInstance().level, from, to, AudioDistancePlugin.config());
    }

    @Override
    public void reset() {
        BlockAcoustics.clearCache();
    }
}
