package org.samo_lego.golfiv.event.S2CPacket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.samo_lego.golfiv.casts.ItemStackChecker;
import org.samo_lego.golfiv.mixin.accessors.InventoryS2CPacketAccessor;
import org.samo_lego.golfiv.mixin.accessors.ScreenHandlerSlotUpdateS2CPacketAccessor;

import java.util.List;
import java.util.function.Consumer;

import static org.samo_lego.golfiv.GolfIV.golfConfig;
import static org.samo_lego.golfiv.casts.ItemStackChecker.creativeInventoryStack;
import static org.samo_lego.golfiv.casts.ItemStackChecker.inventoryStack;

public class ItemInventoryKickPatch implements S2CPacketCallback {

    private static final int MAX_PACKET_SIZE = 2097140;

    @Override
    public void preSendPacket(Packet<?> packet, ServerPlayerEntity player, MinecraftServer server) {
        if (!golfConfig.packet.patchItemKickExploit) {
            return;
        }

        if (packet instanceof InventoryS2CPacket inventoryPacket) {
            boolean oversized = isOversized(buf -> InventoryS2CPacket.CODEC.encode(buf, inventoryPacket), player.getWorld().getRegistryManager());
            if (player.isCreative() || oversized) {
                List<ItemStack> fakedContents = inventoryPacket.contents().stream()
                        .map(player.isCreative() ? ItemStackChecker::creativeInventoryStack : ItemStackChecker::inventoryStack)
                        .toList();
                ((InventoryS2CPacketAccessor) packet).setContents(fakedContents);
            }
        } else if (packet instanceof ScreenHandlerSlotUpdateS2CPacket) {
            ItemStack stack = ((ScreenHandlerSlotUpdateS2CPacketAccessor) packet).getStack();
            boolean stackHasComponents = !stack.getComponentChanges().isEmpty();
            if (stackHasComponents && (player.isCreative() || isOversized(buf -> ItemStack.PACKET_CODEC.encode(buf, stack), player.getWorld().getRegistryManager()))) {
                ItemStack sanitized = player.isCreative() ? creativeInventoryStack(stack) : inventoryStack(stack);
                ((ScreenHandlerSlotUpdateS2CPacketAccessor) packet).setStack(sanitized);
            }
        }
    }

    private static boolean isOversized(Consumer<RegistryByteBuf> encoder, DynamicRegistryManager registryManager) {
        ByteBuf raw = Unpooled.buffer();
        RegistryByteBuf buffer = new RegistryByteBuf(raw, registryManager);
        try {
            encoder.accept(buffer);
            return raw.readableBytes() > MAX_PACKET_SIZE;
        } finally {
            raw.release();
        }
    }
}
