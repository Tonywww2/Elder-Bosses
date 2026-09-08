package com.tonywww.elder_bosses.platforms.registry;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
/*import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}

public final class ModEntities {
    //? if forge {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ElderBosses.MOD_ID);
    //?} else {
    /*private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ElderBosses.MOD_ID);
    *///?}

    public static final Supplier<EntityType<MaleniaEntity>> MALENIA = ENTITIES.register(
            "malenia",
            () -> EntityType.Builder.of(MaleniaEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.9F)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build(ElderBosses.MOD_ID + ":malenia")
    );

    public static final Supplier<EntityType<PromisedConsortEntity>> PROMISED_CONSORT =
            ENTITIES.register(
                    "promised_consort",
                    () -> EntityType.Builder.of(PromisedConsortEntity::new, MobCategory.MONSTER)
                            .sized(1.9F, 4.6F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build(ElderBosses.MOD_ID + ":promised_consort")
            );

    public static final Supplier<EntityType<PromisedConsortGravityRockEntity>>
            PROMISED_CONSORT_GRAVITY_ROCK = ENTITIES.register(
                    "promised_consort_gravity_rock",
                    () -> EntityType.Builder.<PromisedConsortGravityRockEntity>of(
                                    PromisedConsortGravityRockEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(0.75F, 0.75F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build(ElderBosses.MOD_ID + ":promised_consort_gravity_rock")
            );

    public static final Supplier<EntityType<PromisedConsortCloneEntity>> PROMISED_CONSORT_CLONE =
            ENTITIES.register(
                    "promised_consort_clone",
                    () -> EntityType.Builder.<PromisedConsortCloneEntity>of(
                                    PromisedConsortCloneEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(1.9F, 4.6F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build(ElderBosses.MOD_ID + ":promised_consort_clone")
            );

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}