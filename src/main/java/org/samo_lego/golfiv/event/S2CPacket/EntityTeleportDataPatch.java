package org.samo_lego.golfiv.event.S2CPacket;

import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.samo_lego.golfiv.mixin.accessors.EntityPositionS2CPacketAccessor;

import static org.samo_lego.golfiv.GolfIV.golfConfig;

public class EntityTeleportDataPatch implements S2CPacketCallback {
    public EntityTeleportDataPatch() {
    }

    /**
     * If player teleports out of render distance, we modify the coordinates of the
     * packet, in order to hide player's original TP coordinates.
     *
     * @param packet packet being sent
     * @param player player getting the packet
     * @param server Minecraft Server
     */

    @Override
    public void preSendPacket(Packet<?> packet, ServerPlayerEntity player, MinecraftServer server) {
        if (golfConfig.packet.removeTeleportData && packet instanceof EntityPositionS2CPacket) {
            EntityPositionS2CPacketAccessor accessor = (EntityPositionS2CPacketAccessor) packet;
            PlayerPosition change = accessor.getPlayerPosition();
            Vec3d position = change.position();

            int maxPlayerDistance = server.getPlayerManager().getViewDistance() * 16;
            double deltaX = player.getX() - position.x;
            double deltaZ = player.getZ() - position.z;

            double actualPlayerDistance = deltaX * deltaX + deltaZ * deltaZ;
            int maxDistanceSquared = maxPlayerDistance * maxPlayerDistance;

            if (actualPlayerDistance > maxDistanceSquared) {
                Vec3d disguisedPos = new Vec3d(player.getX() + maxPlayerDistance, position.y, player.getZ() + maxPlayerDistance);
                accessor.setPlayerPosition(new PlayerPosition(disguisedPos, change.deltaMovement(), change.yaw(), change.pitch()));
            }
        }
    }
}
