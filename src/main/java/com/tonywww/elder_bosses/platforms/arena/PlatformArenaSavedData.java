package com.tonywww.elder_bosses.platforms.arena;

import com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
//? if neoforge {
/*import net.minecraft.core.HolderLookup;
*///?}

public final class PlatformArenaSavedData extends SavedData {
    private static final String NAME = "elder_bosses_consort_arenas";
    private final Map<BlockPos, Occupant> occupants = new HashMap<>();

    public static PlatformArenaSavedData get(ServerLevel level) {
        //? if forge {
        return level.getDataStorage().computeIfAbsent(PlatformArenaSavedData::load, PlatformArenaSavedData::new, NAME);
        //?} else {
        /*return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlatformArenaSavedData::new,
                (tag, registries) -> load(tag), null), NAME);
        *///?}
    }

    public static PlatformArenaSavedData load(CompoundTag tag) {
        if (tag.getInt("Format") != 1) throw new IllegalArgumentException("Unsupported arena instance data");
        var data = new PlatformArenaSavedData();
        for (var entry : tag.getList("Occupants", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) entry;
            if (!saved.hasUUID("Boss")) throw new IllegalArgumentException("Missing arena boss UUID");
            var binding = new PromisedConsortArenaBinding(saved.getCompound("Binding"));
            if (data.occupants.putIfAbsent(binding.origin(), new Occupant(binding, saved.getUUID("Boss"))) != null) {
                throw new IllegalArgumentException("Duplicate saved arena instance");
            }
        }
        return data;
    }

    public boolean occupied(BlockPos origin) {
        return occupants.containsKey(origin);
    }

    public boolean claim(PromisedConsortArenaBinding binding, UUID boss) {
        var current = occupants.get(binding.origin());
        if (current != null) return current.boss().equals(boss) && current.binding().save().equals(binding.save());
        occupants.put(binding.origin(), new Occupant(binding, boss));
        setDirty();
        return true;
    }

    public void release(BlockPos origin, UUID boss) {
        var current = occupants.get(origin);
        if (current != null && current.boss().equals(boss)) {
            occupants.remove(origin);
            setDirty();
        }
    }

    //? if forge {
    @Override
    public CompoundTag save(CompoundTag tag) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    *///?}
        return write(tag);
    }

    public CompoundTag write(CompoundTag tag) {
        tag.putInt("Format", 1);
        var entries = new ListTag();
        occupants.values().forEach(occupant -> {
            var entry = new CompoundTag();
            entry.putUUID("Boss", occupant.boss());
            entry.put("Binding", occupant.binding().save());
            entries.add(entry);
        });
        tag.put("Occupants", entries);
        return tag;
    }

    private record Occupant(PromisedConsortArenaBinding binding, UUID boss) {
    }
}