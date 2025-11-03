package org.samo_lego.golfiv.mixin.accessors;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the connection backing {@link ServerCommonNetworkHandler} so the packet
 * size limit can be inspected without relying on subclass visibility.
 */
@Mixin(ServerCommonNetworkHandler.class)
public interface ServerCommonNetworkHandlerAccessor {

    /**
     * {@return the Netty backed {@link ClientConnection} for this handler}
     */
    @Accessor("connection")
    ClientConnection golfiv$getConnection();
}
