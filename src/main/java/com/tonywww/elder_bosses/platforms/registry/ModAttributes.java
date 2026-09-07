package com.tonywww.elder_bosses.platforms.registry;

import com.tonywww.elder_bosses.ElderBosses;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
//?} else {
/*import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}

public final class ModAttributes {
    //? if forge {
    private static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ElderBosses.MOD_ID);

    public static final RegistryObject<Attribute> SCARLET_ROT_CAPACITY = ATTRIBUTES.register(
            "scarlet_rot_capacity",
            ModAttributes::createScarletRotCapacity
    );
    //?} else {
    /*private static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, ElderBosses.MOD_ID);

    public static final DeferredHolder<Attribute, Attribute> SCARLET_ROT_CAPACITY = ATTRIBUTES.register(
            "scarlet_rot_capacity",
            ModAttributes::createScarletRotCapacity
    );
    *///?}

    private ModAttributes() {
    }

    private static Attribute createScarletRotCapacity() {
        return new RangedAttribute(
                "attribute.name.elder_bosses.scarlet_rot_capacity",
                100.0,
                0.01,
                1_000_000.0
        ).setSyncable(true);
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
    }

        public static AttributeSupplier.Builder addScarletRotCapacity(
                        AttributeSupplier.Builder builder,
                        double capacity
        ) {
                //? if forge {
                return builder.add(SCARLET_ROT_CAPACITY.get(), capacity);
                //?} else {
                /*return builder.add(SCARLET_ROT_CAPACITY, capacity);
                *///?}
        }

        public static double scarletRotCapacity(LivingEntity entity) {
                //? if forge {
                return entity.getAttributeValue(SCARLET_ROT_CAPACITY.get());
                //?} else {
                /*return entity.getAttributeValue(SCARLET_ROT_CAPACITY);
                *///?}
        }
}