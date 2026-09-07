package com.tonywww.elder_bosses.platforms;

import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.platforms.registry.ModAttributes;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
//? if forge {
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
//?} else {
/*import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
*///?}

public final class ModEntityEvents {
    private ModEntityEvents() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.MALENIA.get(), MaleniaEntity.createAttributes().build());
    }

    public static void addLivingEntityAttributes(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> entityType : event.getTypes()) {
            //? if forge {
            if (!event.has(entityType, ModAttributes.SCARLET_ROT_CAPACITY.get())) {
                event.add(entityType, ModAttributes.SCARLET_ROT_CAPACITY.get());
            }
            //?} else {
            /*if (!event.has(entityType, ModAttributes.SCARLET_ROT_CAPACITY)) {
                event.add(entityType, ModAttributes.SCARLET_ROT_CAPACITY);
            }
            *///?}
        }
    }
}