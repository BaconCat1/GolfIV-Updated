package org.samo_lego.golfiv.mixin.illegal_items;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.entry.RegistryEntry;
import org.samo_lego.golfiv.casts.ItemStackChecker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashSet;
import java.util.Set;

import static org.samo_lego.golfiv.GolfIV.golfConfig;

/**
 * Additional methods for checking ItemStack's legality.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixinCast_ItemStackChecker implements ItemStackChecker {

    @Shadow
    public abstract int getMaxCount();

    @Shadow
    public abstract void setCount(int count);

    private ItemStack self() {
        return (ItemStack) (Object) this;
    }

    /**
     * Sets the appropriate ItemStack size and removes disallowed enchantments.
     *
     * @param survival whether survival-mode limits should be applied
     */
    @Override
    public void makeLegal(boolean survival) {
        ItemStack stack = self();

        if ((survival ? golfConfig.items.survival.checkEnchants : golfConfig.items.creative.checkEnchants)) {
            sanitizeEnchantments(stack);
        }

        if ((survival ? golfConfig.items.survival.checkPotionLevels : golfConfig.items.creative.checkPotionLevels)
                && ItemStackChecker.isPotionItem(stack.getItem())) {
            sanitizePotionEffects(stack);
        }

        if (survival && shouldClearForBannedItem(stack)) {
            this.setCount(0);
            return;
        }

        if ((survival ? golfConfig.items.survival.checkItemCount : golfConfig.items.creative.checkItemCount)
                && stack.getCount() > this.getMaxCount()) {
            this.setCount(this.getMaxCount());
        }
    }

    private void sanitizeEnchantments(ItemStack stack) {
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
        if (enchantments.isEmpty()) {
            return;
        }

        for (var entry : enchantments.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> enchantmentEntry = entry.getKey();
            Enchantment enchantment = enchantmentEntry.value();
            int level = entry.getIntValue();

            Set<RegistryEntry<Enchantment>> others = new HashSet<>(enchantments.getEnchantments());
            others.remove(enchantmentEntry);

            if (!enchantment.isAcceptableItem(stack)
                    || !EnchantmentHelper.isCompatible(others, enchantmentEntry)
                    || level > enchantment.getMaxLevel()) {
                stack.remove(DataComponentTypes.ENCHANTMENTS);
                break;
            }
        }
    }

    private void sanitizePotionEffects(ItemStack stack) {
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents != null && ItemStackChecker.shouldRemovePotionEffects(contents)) {
            stack.remove(DataComponentTypes.POTION_CONTENTS);
        }
    }

    private boolean shouldClearForBannedItem(ItemStack stack) {
        Item item = stack.getItem();

        if (golfConfig.items.survival.banSpawnEggs && item instanceof SpawnEggItem) {
            return true;
        }

        String descriptor = golfConfig.items.survival.bannedItems.get(item);
        if (descriptor == null) {
            return false;
        }

        if (descriptor.isEmpty()) {
            return true;
        }

        return ItemStackChecker.stackDescriptor(stack).map(descriptor::equals).orElse(false);
    }
}
