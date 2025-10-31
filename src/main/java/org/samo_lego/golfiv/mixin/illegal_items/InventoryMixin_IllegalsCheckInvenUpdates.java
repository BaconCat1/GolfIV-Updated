package org.samo_lego.golfiv.mixin.illegal_items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.StackWithSlot;
import net.minecraft.storage.ReadView;
import org.samo_lego.golfiv.casts.ItemStackChecker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


import static org.samo_lego.golfiv.GolfIV.golfConfig;

/**
 * Legalizes the entire inventory after certain large inventory updates
 */
@Mixin(PlayerInventory.class)
abstract class InventoryMixin_IllegalsCheckInvenUpdates {
    @Shadow @Final public PlayerEntity player;

    /**
     * Legalizes the inventory after cloning
     *
     * @param other the inventory that this inventory is cloned from
     * @param ci callback info
     */
    @Inject(method = "clone", at = @At("TAIL"))
    private void onInventoryCopy(PlayerInventory other, CallbackInfo ci) {
        legaliseInventory();
    }

    /**
     * Legalizes the inventory after deserialization
     *
     * @param tag the tag it is deserializing
     * @param ci callback info
     */
    @Inject(method = "readData", at = @At("TAIL"))
    private void onDeserialize(ReadView.TypedListReadView<StackWithSlot> readView, CallbackInfo ci) {
        legaliseInventory();
    }

    /**
     * Legalizes the entire inventory
     */
    private void legaliseInventory() {
        PlayerInventory inventory = (PlayerInventory) (Object) this;
        boolean survival = !this.player.isCreative();

        if ((golfConfig.items.survival.legaliseWholeInventory && survival) ||
                (golfConfig.items.creative.legaliseWholeInventory && !survival)) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStack(slot);
                ((ItemStackChecker) (Object) stack).makeLegal(survival);
            }
        }
    }
}
