//? if neoforge {
/*package com.tonywww.elder_bosses.platforms.player;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.combat.status.ScarletRotData;
import com.tonywww.elder_bosses.combat.status.ScarletRotEntityData;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class NeoForgePlayerRotAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ElderBosses.MOD_ID);

    public static final Supplier<AttachmentType<ScarletRotData>> SCARLET_ROT = ATTACHMENTS.register(
            "scarlet_rot",
            () -> AttachmentType.<ScarletRotData>builder(ScarletRotEntityData::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, ScarletRotData>() {
                        @Override
                        public ScarletRotData read(
                                IAttachmentHolder holder,
                                CompoundTag tag,
                                HolderLookup.Provider provider
                        ) {
                            ScarletRotData data = new ScarletRotEntityData();
                            data.load(tag);
                            return data;
                        }

                        @Override
                        public CompoundTag write(
                                ScarletRotData attachment,
                                HolderLookup.Provider provider
                        ) {
                            return attachment.save();
                        }
                    })
                    .build()
    );

    private NeoForgePlayerRotAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
*///?}