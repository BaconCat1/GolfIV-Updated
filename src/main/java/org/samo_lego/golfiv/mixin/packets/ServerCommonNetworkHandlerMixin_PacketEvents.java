package org.samo_lego.golfiv.mixin.packets;

import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Provides a base packet hook that subclasses can opt into by overriding
 * {@link #golfiv$preSendPacket(Packet)} when they have additional context
 * available (such as an associated player).
 */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin_PacketEvents {

    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"))
    private void golfiv$onSendPacket(Packet<?> packet, CallbackInfo ci) {
        this.golfiv$preSendPacket(packet);
    }

    /**
     * Default implementation intentionally left blank; subclasses provide
     * any packet event behaviour when needed.
     */
    protected void golfiv$preSendPacket(Packet<?> packet) {
        // no-op by default
    }
}
