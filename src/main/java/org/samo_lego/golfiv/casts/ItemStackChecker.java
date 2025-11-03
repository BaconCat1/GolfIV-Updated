package org.samo_lego.golfiv.casts;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BannerPatternsComponent;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.item.TippedArrowItem;
import net.minecraft.item.WritableBookItem;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * Checks ItemStacks and performs sanitisation routines.
 */
public interface ItemStackChecker {

    int MAX_BANNER_PATTERNS = 12;
    Collection<StatusEffectInstance> UNLUCK = Collections.singleton(new StatusEffectInstance(StatusEffects.UNLUCK, 6000));

    void makeLegal(boolean survival);

    static ItemStack fakeStack(ItemStack original, boolean spoofCount) {
        ItemStack fake = new ItemStack(original.getItem(), spoofCount ? original.getMaxCount() : original.getCount());

        if (original.hasGlint()) {
            fake.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        }

        copyComponent(original, fake, DataComponentTypes.CUSTOM_NAME);
        copyComponent(original, fake, DataComponentTypes.LORE);
        copyComponent(original, fake, DataComponentTypes.DYED_COLOR);

        sanitizePotionContents(original, fake, true);
        sanitizeBookContent(original, fake, true);
        sanitizeProfile(original, fake);
        sanitizeBanner(original, fake);
        sanitizeBlockEntityData(original, fake);
        sanitizeCrossbow(original, fake, false);
        copyComponent(original, fake, DataComponentTypes.MAP_ID);

        return fake;
    }

    static ItemStack creativeInventoryStack(ItemStack stack) {
        ItemStack fake = inventoryStack(stack);
        ComponentChanges changes = stack.getComponentChanges();
        if (!changes.isEmpty()) {
            NbtCompound marker = new NbtCompound();
            marker.putInt("GolfIV", changes.hashCode());
            fake.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(marker));
        }
        return fake;
    }

    static ItemStack inventoryStack(ItemStack stack) {
        ComponentChanges changes = stack.getComponentChanges();
        if (changes.isEmpty()) {
            return stack;
        }

        ItemStack fake = new ItemStack(stack.getItem(), stack.getCount());

        copyComponent(stack, fake, DataComponentTypes.CUSTOM_NAME);
        copyComponent(stack, fake, DataComponentTypes.LORE);
        copyComponent(stack, fake, DataComponentTypes.DYED_COLOR);
        sanitizeEnchantmentsForInventory(stack, fake);
        sanitizePotionContents(stack, fake, false);
        sanitizeBookContent(stack, fake, false);
        sanitizeProfile(stack, fake);
        sanitizeBanner(stack, fake);
        sanitizeBlockEntityData(stack, fake);
        sanitizeCrossbow(stack, fake, true);
        copyComponent(stack, fake, DataComponentTypes.MAP_ID);
        copyDamage(stack, fake);

        return fake;
    }

    private static void sanitizeEnchantmentsForInventory(ItemStack source, ItemStack target) {
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(source);
        if (enchantments.isEmpty()) {
            return;
        }

        EnchantmentHelper.apply(target, builder -> enchantments.getEnchantmentEntries().forEach(entry -> builder.add(entry.getKey(), entry.getIntValue())));
    }

    private static void sanitizePotionContents(ItemStack source, ItemStack target, boolean preserveGlintOnly) {
        PotionContentsComponent contents = source.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return;
        }

        List<StatusEffectInstance> effects;
        if (preserveGlintOnly) {
            effects = source.hasGlint() ? List.copyOf(UNLUCK) : List.of();
        } else {
            effects = new ArrayList<>(contents.customEffects());
        }

        PotionContentsComponent sanitized = new PotionContentsComponent(
                contents.potion(),
                contents.customColor(),
                effects,
                contents.customName()
        );

        target.set(DataComponentTypes.POTION_CONTENTS, sanitized);
    }

    private static void sanitizeBookContent(ItemStack source, ItemStack target, boolean clearPages) {
        if (source.getItem() instanceof WrittenBookItem) {
            WrittenBookContentComponent content = source.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
            if (content != null) {
                WrittenBookContentComponent sanitized = clearPages
                        ? content.withPages(List.of())
                        : content;
                target.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, sanitized);
            }
        } else if (source.getItem() instanceof WritableBookItem) {
            WritableBookContentComponent content = source.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
            if (content != null) {
                target.set(DataComponentTypes.WRITABLE_BOOK_CONTENT, clearPages ? WritableBookContentComponent.DEFAULT : content);
            }
        }
    }

    private static void sanitizeProfile(ItemStack source, ItemStack target) {
        ProfileComponent profile = source.get(DataComponentTypes.PROFILE);
        if (profile == null) {
            return;
        }

        PropertyMap sanitizedProperties = new PropertyMap();
        profile.properties().get("textures").stream().findFirst().ifPresent(property -> sanitizedProperties.put("textures", property));

        GameProfile gameProfile = new GameProfile(profile.uuid().orElse(null), profile.name().orElse(null));
        sanitizedProperties.get("textures").forEach(property -> gameProfile.getProperties().put("textures", property));

        target.set(DataComponentTypes.PROFILE, new ProfileComponent(profile.name(), profile.uuid(), sanitizedProperties, gameProfile));
    }

    private static void sanitizeBanner(ItemStack source, ItemStack target) {
        BannerPatternsComponent patterns = source.get(DataComponentTypes.BANNER_PATTERNS);
        if (patterns == null) {
            return;
        }

        BannerPatternsComponent.Builder builder = new BannerPatternsComponent.Builder();
        int count = 0;
        for (BannerPatternsComponent.Layer layer : patterns.layers()) {
            if (count++ >= MAX_BANNER_PATTERNS) {
                break;
            }
            builder.add(layer);
        }
        BannerPatternsComponent sanitized = builder.build();
        if (!sanitized.layers().isEmpty()) {
            target.set(DataComponentTypes.BANNER_PATTERNS, sanitized);
        }
    }

    private static void sanitizeBlockEntityData(ItemStack source, ItemStack target) {
        if (!(source.getItem() instanceof BlockItem blockItem)) {
            return;
        }

        Block block = blockItem.getBlock();
        if (!(block instanceof ShulkerBoxBlock)) {
            return;
        }

        NbtComponent data = source.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (data == null) {
            return;
        }

        NbtCompound original = data.copyNbt();
        NbtCompound sanitized = new NbtCompound();

        if (original.contains("LootTable")) {
            sanitized.put("LootTable", original.get("LootTable"));
        }

        original.getList("Items").ifPresent(list -> {
            NbtList fakeItems = new NbtList();
            for (int i = 0; i < list.size(); i++) {
                NbtElement element = list.get(i);
                if (!(element instanceof NbtCompound oldItem)) {
                    continue;
                }
                NbtCompound fakeItem = new NbtCompound();
                if (oldItem.contains("Slot")) {
                    fakeItem.put("Slot", oldItem.get("Slot"));
                }
                if (oldItem.contains("id")) {
                    fakeItem.put("id", oldItem.get("id"));
                }
                if (oldItem.contains("Count")) {
                    fakeItem.put("Count", oldItem.get("Count"));
                }
                fakeItems.add(fakeItem);
            }
            if (!fakeItems.isEmpty()) {
                sanitized.put("Items", fakeItems);
            }
        });

        if (!sanitized.isEmpty()) {
            target.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(sanitized));
        }
    }

    private static void sanitizeCrossbow(ItemStack source, ItemStack target, boolean isInventory) {
        if (!(source.getItem() instanceof CrossbowItem) || !CrossbowItem.isCharged(source)) {
            return;
        }

        ChargedProjectilesComponent component = source.get(DataComponentTypes.CHARGED_PROJECTILES);
        if (component == null || component.isEmpty()) {
            return;
        }

        List<ItemStack> projectiles = component.getProjectiles();
        if (projectiles.isEmpty()) {
            return;
        }

        ItemStack projectile = projectiles.get(0);
        ItemStack sanitized = (isInventory || projectile.isOf(Items.FIREWORK_ROCKET))
                ? inventoryStack(projectile)
                : new ItemStack(Items.ARROW);

        target.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.of(List.of(sanitized)));
    }

    private static void copyDamage(ItemStack source, ItemStack target) {
        if (source.isDamageable()) {
            target.setDamage(source.getDamage());
        }
    }

    private static <T> void copyComponent(ItemStack source, ItemStack target, ComponentType<T> type) {
        T value = source.get(type);
        if (value != null) {
            target.set(type, value);
        }
    }

    static boolean isPotionItem(Item item) {
        return item instanceof PotionItem || item instanceof TippedArrowItem;
    }

    static boolean shouldRemovePotionEffects(PotionContentsComponent contents) {
        for (StatusEffectInstance effect : contents.customEffects()) {
            if (effect.getAmplifier() > 1) {
                return true;
            }
        }
        return false;
    }

    static Optional<String> stackDescriptor(ItemStack stack) {
        ComponentChanges changes = stack.getComponentChanges();
        return changes.isEmpty() ? Optional.empty() : Optional.of(changes.toString());
    }
}
