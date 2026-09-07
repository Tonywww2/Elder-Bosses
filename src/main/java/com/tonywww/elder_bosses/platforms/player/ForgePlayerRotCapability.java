//? if forge {
package com.tonywww.elder_bosses.platforms.player;

import com.tonywww.elder_bosses.combat.status.ScarletRotData;
import com.tonywww.elder_bosses.combat.status.ScarletRotEntityData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;

public final class ForgePlayerRotCapability {
    public static final Capability<ScarletRotData> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private ForgePlayerRotCapability() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(ScarletRotData.class);
    }

    public static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final ScarletRotData data = new ScarletRotEntityData();
        private final LazyOptional<ScarletRotData> optional = LazyOptional.of(() -> data);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> requested, Direction side) {
            return requested == CAPABILITY ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.load(tag);
        }

        public void invalidate() {
            optional.invalidate();
        }
    }
}
//?}