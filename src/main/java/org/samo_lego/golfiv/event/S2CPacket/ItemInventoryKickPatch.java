package org.samo_lego.golfiv.event.S2CPacket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.handler.PacketInflater;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.samo_lego.golfiv.casts.ItemStackChecker;
import org.samo_lego.golfiv.mixin.accessors.InventoryS2CPacketAccessor;
import org.samo_lego.golfiv.mixin.accessors.ScreenHandlerSlotUpdateS2CPacketAccessor;
import org.samo_lego.golfiv.mixin.accessors.ServerCommonNetworkHandlerAccessor;

import java.util.List;
import java.util.function.Consumer;

import static org.samo_lego.golfiv.GolfIV.golfConfig;
import static org.samo_lego.golfiv.casts.ItemStackChecker.creativeInventoryStack;
import static org.samo_lego.golfiv.casts.ItemStackChecker.inventoryStack;

public class ItemInventoryKickPatch implements S2CPacketCallback {

    private static final int UNCOMPRESSED_PACKET_LIMIT = 2 * 1024 * 1024;

    @Override
    public void preSendPacket(Packet<?> packet, ServerPlayerEntity player, MinecraftServer server) {
        if (!golfConfig.packet.patchItemKickExploit) {
            return;
        }

        ClientConnection connection = ((ServerCommonNetworkHandlerAccessor) player.networkHandler).golfiv$getConnection();
        if (connection == null) {
            return;
        }

        int packetLimit = determinePacketLimit(server);

        if (packet instanceof InventoryS2CPacket inventoryPacket) {
            boolean oversized = isOversized(
                    buf -> InventoryS2CPacket.CODEC.encode(buf, inventoryPacket),
                    player.getWorld().getRegistryManager(),
                    packetLimit
            );
            if (player.isCreative() || oversized) {
                List<ItemStack> fakedContents = inventoryPacket.contents().stream()
                        .map(player.isCreative() ? ItemStackChecker::creativeInventoryStack : ItemStackChecker::inventoryStack)
                        .toList();
                ((InventoryS2CPacketAccessor) packet).setContents(fakedContents);
            }
        } else if (packet instanceof ScreenHandlerSlotUpdateS2CPacket) {
            ItemStack stack = ((ScreenHandlerSlotUpdateS2CPacketAccessor) packet).getStack();
            boolean stackHasComponents = !stack.getComponentChanges().isEmpty();
            if (stackHasComponents && (player.isCreative() || isOversized(
                    buf -> ItemStack.PACKET_CODEC.encode(buf, stack),
                    player.getWorld().getRegistryManager(),
                    packetLimit
            ))) {
                ItemStack sanitized = player.isCreative() ? creativeInventoryStack(stack) : inventoryStack(stack);
                ((ScreenHandlerSlotUpdateS2CPacketAccessor) packet).setStack(sanitized);
            }
        }
    }

    /**
     * Determines the maximum allowed packet size in bytes based on whether the
     * server currently enforces network compression.
     *
     * <p>When compression is enabled the decompressed payload may reach {@value PacketInflater#MAXIMUM_PACKET_SIZE}
     * bytes; otherwise packets are capped at {@value #UNCOMPRESSED_PACKET_LIMIT}, matching vanilla's limit for
     * uncompressed play-phase packets.
     *
     * @param server the active server
     * @return the permitted packet size in bytes
     */
    private static int determinePacketLimit(MinecraftServer server) {
        return server.getNetworkCompressionThreshold() >= 0
                ? PacketInflater.MAXIMUM_PACKET_SIZE
                : UNCOMPRESSED_PACKET_LIMIT;
    }

    private static boolean isOversized(Consumer<RegistryByteBuf> encoder, DynamicRegistryManager registryManager, int packetLimit) {
        ByteBuf raw = Unpooled.buffer();
        RegistryByteBuf buffer = new RegistryByteBuf(raw, registryManager);
        try {
            encoder.accept(buffer);
            return raw.readableBytes() > packetLimit;
        } finally {
            raw.release();
        }
    }
}
