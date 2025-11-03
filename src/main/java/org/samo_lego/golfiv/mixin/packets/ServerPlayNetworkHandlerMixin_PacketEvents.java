package org.samo_lego.golfiv.mixin.packets;

import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.samo_lego.golfiv.event.S2CPacket.S2CPacketCallback;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin_PacketEvents extends ServerCommonNetworkHandlerMixin_PacketEvents {

    @Shadow @Final protected MinecraftServer server;
    @Shadow public ServerPlayerEntity player;

    @Override
    protected void golfiv$preSendPacket(Packet<?> packet) {
        S2CPacketCallback.EVENT.invoker().preSendPacket(packet, player, server);
    }
}
