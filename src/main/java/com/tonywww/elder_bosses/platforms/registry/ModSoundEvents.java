package com.tonywww.elder_bosses.platforms.registry;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
/*import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}

public final class ModSoundEvents {
    //? if forge {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ElderBosses.MOD_ID);
    //?} else {
    /*private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, ElderBosses.MOD_ID);
    *///?}

    public static final Supplier<SoundEvent> MALENIA_INSTANT_GUARD_CUE = SOUNDS.register(
            "malenia.instant_guard_cue",
            () -> SoundEvent.createVariableRangeEvent(
                    PlatformResourceLocation.id("malenia.instant_guard_cue"))
    );
        public static final Supplier<SoundEvent> PROMISED_CONSORT_INSTANT_GUARD_CUE = SOUNDS.register(
            "promised_consort.instant_guard_cue",
            () -> SoundEvent.createVariableRangeEvent(
                PlatformResourceLocation.id("promised_consort.instant_guard_cue"))
        );
        public static final Supplier<SoundEvent> MALENIA_HURT = register("entity.malenia.hurt");
        public static final Supplier<SoundEvent> MALENIA_STAGGER = register("entity.malenia.stagger");
        public static final Supplier<SoundEvent> MALENIA_GRUNT = register("entity.malenia.grunt");
        private static final java.util.Map<PromisedConsortActionSoundPlan.Sound, Supplier<SoundEvent>> CONSORT_ACTIONS = consortActions();

    private static java.util.Map<PromisedConsortActionSoundPlan.Sound, Supplier<SoundEvent>> consortActions() {
        var sounds = new java.util.EnumMap<PromisedConsortActionSoundPlan.Sound, Supplier<SoundEvent>>(PromisedConsortActionSoundPlan.Sound.class);
        for (var sound : PromisedConsortActionSoundPlan.Sound.values()) {
            sounds.put(sound, SOUNDS.register(sound.eventId(), () -> SoundEvent.createFixedRangeEvent(
                    PlatformResourceLocation.id(sound.eventId()), sound.distance())));
        }
        return java.util.Map.copyOf(sounds);
    }

    public static SoundEvent promisedConsortAction(PromisedConsortActionSoundPlan.Sound sound) {
        return CONSORT_ACTIONS.get(sound).get();
    }

    private ModSoundEvents() {
    }

        private static Supplier<SoundEvent> register(String path) {
                return SOUNDS.register(
                                path,
                                () -> SoundEvent.createVariableRangeEvent(PlatformResourceLocation.id(path))
                );
        }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}