package com.tonywww.elder_bosses.platforms.compat;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.tonywww.elder_bosses.compat.curios.CurioProfile;
import com.tonywww.elder_bosses.compat.curios.CurioProfile.Stat;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.config.PlatformEquipmentConfig;
import com.tonywww.elder_bosses.platforms.registry.ModAttributes;
import com.tonywww.elder_bosses.platforms.registry.ModItems;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
//? if neoforge {
/*import net.minecraft.core.Holder;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Loaded only after the optional Curios mod has been detected. */
public final class PlatformCurioItem implements ICurioItem {
    private final CurioProfile profile;

    public PlatformCurioItem(CurioProfile profile) {
        this.profile = profile;
    }

    static void registerItems() {
        for (CurioProfile profile : CurioProfile.values()) {
            Item item = switch (profile) {
                case GOLDEN_NEEDLE -> ModItems.GOLDEN_NEEDLE.get();
                case CONSECRATED_PROSTHETIC_BLADE -> ModItems.CONSECRATED_PROSTHETIC_BLADE.get();
                case YOUNG_LION_GREATSWORD -> ModItems.YOUNG_LION_GREATSWORD.get();
                case CIRCLET_OF_FADING_LIGHT -> ModItems.CIRCLET_OF_FADING_LIGHT.get();
            };
            CuriosApi.registerCurio(item, new PlatformCurioItem(profile));
        }
    }

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        if (context.cosmetic() || context.entity() == null) {
            return true;
        }
        return CuriosApi.getCuriosInventory(context.entity()).map(inventory ->
            inventory.findCurios(stack.getItem()).stream().noneMatch(result -> {
                SlotContext other = result.slotContext();
                return !other.cosmetic() && !(other.identifier().equals(context.identifier())
                    && other.index() == context.index());
            })
        ).orElse(true);
    }

    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        // Preserve held-use actions; equipping is done through the Curios inventory.
        return false;
    }

    //? if forge {
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext context, UUID slotId, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> result = HashMultimap.create();
    //?} else {
    /*@Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext context, ResourceLocation slotId, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> result = HashMultimap.create();
    *///?}
        if (context.cosmetic()) {
            return result;
        }
        for (CurioProfile.Bonus bonus : profile.bonuses(PlatformEquipmentConfig.current())) {
            if (bonus.amount() == 0.0) continue;
            ResourceLocation id = PlatformResourceLocation.id("curio/" + profile.itemId() + "/"
                + bonus.stat().name().toLowerCase(java.util.Locale.ROOT));
            // Stable per-item identities also prevent forced duplicate stacks multiplying bonuses.
            //? if forge {
            AttributeModifier modifier = new AttributeModifier(
                UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8)),
                id.toString(), bonus.amount(), bonus.percent()
                    ? AttributeModifier.Operation.MULTIPLY_BASE : AttributeModifier.Operation.ADDITION);
            //?} else {
            /*AttributeModifier modifier = new AttributeModifier(id, bonus.amount(), bonus.percent()
                ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE);
            *///?}
            result.put(attribute(bonus.stat()), modifier);
        }
        return result;
    }

    //? if forge {
    private static Attribute attribute(Stat stat) {
    //?} else {
    /*private static Holder<Attribute> attribute(Stat stat) {
    *///?}
        return switch (stat) {
            //? if forge {
            case ROT_CAPACITY -> ModAttributes.SCARLET_ROT_CAPACITY.get();
            //?} else {
            /*case ROT_CAPACITY -> ModAttributes.SCARLET_ROT_CAPACITY;
            *///?}
            case ATTACK_SPEED -> Attributes.ATTACK_SPEED;
            case ATTACK_DAMAGE -> Attributes.ATTACK_DAMAGE;
            case ARMOR -> Attributes.ARMOR;
            case KNOCKBACK_RESISTANCE -> Attributes.KNOCKBACK_RESISTANCE;
            case LUCK -> Attributes.LUCK;
        };
    }

    //? if forge {
    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, ItemStack stack) {
    //?} else {
    /*@Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, Item.TooltipContext context, ItemStack stack) {
    *///?}
        tooltips.add(Component.translatable("tooltip.elder_bosses.curio.unique").withStyle(ChatFormatting.GRAY));
        return tooltips;
    }
}
