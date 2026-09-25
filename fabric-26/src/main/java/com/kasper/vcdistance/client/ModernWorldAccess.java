package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.NearbyPlayers;
import com.kasper.vcdistance.SpeakerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
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
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == self || (p.isSpectator() && !self.isSpectator()) || p.isInvisibleTo(self)) {
                continue;
            }
            double d = listener.distanceTo(p.getEyePosition());
            if (d <= range) {
                list.add(new NearbyPlayers.Player(p.getUUID(), p.getName().getString(), d));
            }
        }
        return list;
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
