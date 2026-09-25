package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.SpeakerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client world access for Minecraft 1.20 - 1.21.x.
 */
public final class MinecraftWorldAccess implements WorldAccess {

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
        return BlockAcoustics.traceRay(Minecraft.getInstance().level, from, to, AudioDistancePlugin.config());
    }

    @Override
    public void reset() {
        BlockAcoustics.clearCache();
    }
}
