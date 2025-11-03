package org.samo_lego.golfiv.mixin.illegal_items;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.samo_lego.golfiv.casts.ItemStackChecker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.samo_lego.golfiv.GolfIV.golfConfig;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin_CreativeItemsCheck {
    @Unique
    private final Int2ObjectMap<ComponentChanges> golfiv$componentCache = new Int2ObjectOpenHashMap<>();
    @Unique
    private boolean golfiv$componentCachePopulated;

    @Shadow
    public ServerPlayerEntity player;

    /**
     * Recovers stack NBT and clears illegal tags from creative items while still allowing pick block function.
     * <p>
     * If {@link GolfConfig.Packet#patchItemKickExploit Patch Item Kick Exploit} is enabled and the tag {@code GolfIV}
     * is present, the hash is looked up in the cached component map to recover the original NBT of the item,
     * allowing bypass of {@link GolfConfig.IllegalItems.Creative#removeCreativeNBTTags Remove Creative NBT Tags}.
     * <p>
     * If {@link GolfConfig.IllegalItems.Creative#removeCreativeNBTTags Remove Creative NBT Tags} is enabled,
     * and the tag {@code GolfIV} was not found or invalid, then the items are sanitised in accordance to the
     * set {@link GolfConfig.IllegalItems.Creative#whitelistedNBT Whitelisted NBT} in the config.
     *
     * @param itemStack The stack to either recover the NBT by hash from, or to be sanitized.
     * @return Recovered stack if GolfIV hash tag is present and valid, "sanitized" stack otherwise.
     * @author samo_lego
     * @author Ampflower
     * @see ItemStackChecker#fakeStack(ItemStack, boolean)
     * @see org.samo_lego.golfiv.event.S2CPacket.ItemInventoryKickPatch
     * @see GolfConfig.Packet#patchItemKickExploit
     * @see GolfConfig.IllegalItems.Creative#removeCreativeNBTTags
     * @see GolfConfig.IllegalItems.Creative#whitelistedNBT
     */

    @ModifyVariable(
            method = "onCreativeInventoryAction(Lnet/minecraft/network/packet/c2s/play/CreativeInventoryActionC2SPacket;)V",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private ItemStack golfiv$sanitizeCreativeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }

        if (golfConfig.packet.patchItemKickExploit && golfiv$restoreTaggedStack(stack)) {
            return stack;
        }

        if (golfConfig.items.creative.removeCreativeNBTTags) {
            ItemStack sanitized = ItemStackChecker.inventoryStack(stack);
            ((ItemStackChecker) (Object) sanitized).makeLegal(false);
            return sanitized;
        }

        return stack;
    }
    
    /**
     * Keeps {@link #golfiv$componentCache} aligned with the player's legitimate inventory component data.
     * <p>
     * When the slot content changes, the previous stack's component changes are cached under their hash so the
     * data can later be restored during creative interactions (for example after pick-block).
     * <p>
     * The new stack is written to the slot afterwards; if it carries a GolfIV tag the cached components can be
     * looked up and re-applied in {@link #golfiv$restoreTaggedStack(ItemStack)}.
     *
     * @param slot     The slot being updated.
     * @param newStack The stack about to replace the current slot contents.
     * @implNote Component caching runs only when {@link GolfConfig.Packet#patchItemKickExploit Patch Item Kick Exploit} is enabled.
     * @author Ampflower
     * @see GolfConfig.Packet#patchItemKickExploit
     * @see #golfiv$restoreTaggedStack(ItemStack)
     */

    @Redirect(
            method = "onCreativeInventoryAction",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;setStack(Lnet/minecraft/item/ItemStack;)V")
    )
    private void golfiv$cacheComponentsBeforeSet(Slot slot, ItemStack newStack) {
        if (golfConfig.packet.patchItemKickExploit) {
            ItemStack oldStack = slot.getStack();
            if (!oldStack.isEmpty()) {
                ComponentChanges changes = oldStack.getComponentChanges();
                if (!changes.isEmpty()) {
                    golfiv$componentCache.put(changes.hashCode(), changes);
                }
            }
        }

        slot.setStack(newStack);
    }

    @Inject(method = "onCloseHandledScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
            shift = At.Shift.AFTER))
    private void golfiv$clearComponentCache(CloseHandledScreenC2SPacket packet, CallbackInfo ci) {
        golfiv$componentCache.clear();
        golfiv$componentCachePopulated = false;
    }

    @Unique
    private boolean golfiv$restoreTaggedStack(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || !customData.contains("GolfIV")) {
            return false;
        }

        NbtCompound data = customData.copyNbt();
        int hash = data.getInt("GolfIV", 0);

        if (!golfiv$componentCachePopulated && !golfiv$componentCache.containsKey(hash)) {
            golfiv$populateComponentCache();
        }

        ComponentChanges stored = golfiv$componentCache.get(hash);
        if (stored == null) {
            data.remove("GolfIV");
            if (data.isEmpty()) {
                stack.remove(DataComponentTypes.CUSTOM_DATA);
            } else {
                stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
            }
            return false;
        }

        stack.applyUnvalidatedChanges(stored);
        stack.remove(DataComponentTypes.CUSTOM_DATA);
        return true;
    }

    @Unique
    private void golfiv$populateComponentCache() {
        PlayerInventory inventory = this.player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            golfiv$storeComponents(inventory.getStack(slot));
        }
        golfiv$componentCachePopulated = true;
    }

    @Unique
    private void golfiv$storeComponents(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ComponentChanges changes = stack.getComponentChanges();
        if (changes.isEmpty()) {
            return;
        }
        golfiv$componentCache.put(changes.hashCode(), changes);
    }
}
