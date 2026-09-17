package com.tonywww.elder_bosses.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

public record PromisedConsortArenaLayout(Map<String, BlockPos> anchors, List<Part> parts, Map<String, Volume> volumes) {
    private static final Set<String> REQUIRED_ANCHORS = Set.of(
            "arena_origin", "arena_center", "boss_spawn", "phase_return",
            "meteor_departure", "player_entry", "fog_gate", "summon_altar");
    private static final Set<String> FORBIDDEN_BLOCKS = Set.of(
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:jigsaw", "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
            "minecraft:dispenser", "minecraft:dropper", "minecraft:hopper");

    public PromisedConsortArenaLayout {
        anchors = Map.copyOf(anchors);
        parts = List.copyOf(parts);
        volumes = Map.copyOf(volumes);
    }

    public static List<String> partNames(CompoundTag root) {
        List<String> names = new ArrayList<>();
        for (String marker : inspect(root, true).markers().keySet()) {
            if (!marker.startsWith("part:")) continue;
            String name = marker.substring(5);
            require(name.matches("[a-z][a-z0-9_]{0,63}"), "Invalid part name: " + name);
            names.add(name);
        }
        require(!names.isEmpty() && names.size() <= 64, "Expected 1..64 fixed parts");
        return List.copyOf(names);
    }

    public static PromisedConsortArenaLayout read(CompoundTag root, Map<String, CompoundTag> templates) {
        TemplateData metadata = inspect(root, true);
        BlockPos origin = metadata.markers().get("anchor:arena_origin");
        require(origin != null, "Missing anchor:arena_origin");
        Map<String, BlockPos> anchors = new HashMap<>();
        Map<String, BlockPos> offsets = new LinkedHashMap<>();
        Map<String, BlockPos> volumeMarkers = new HashMap<>();
        for (var entry : metadata.markers().entrySet()) {
            String marker = entry.getKey();
            BlockPos position = entry.getValue().subtract(origin);
            if (marker.startsWith("anchor:")) {
                String name = marker.substring(7);
                require(REQUIRED_ANCHORS.contains(name) || name.equals("meteor_default_impact"), "Unknown anchor: " + name);
                anchors.put(name, position);
            } else if (marker.startsWith("part:")) {
                String name = marker.substring(5);
                require(name.matches("[a-z][a-z0-9_]{0,63}"), "Invalid part name: " + name);
                offsets.put(name, position);
            } else if (marker.matches("volume:(protected_[0-9]+|restore|entry)_(min|max)")) {
                volumeMarkers.put(marker.substring(7), position);
            } else {
                throw new IllegalArgumentException("Unknown marker: " + marker);
            }
        }
        require(anchors.keySet().containsAll(REQUIRED_ANCHORS), "Missing required anchors");
        anchors.putIfAbsent("meteor_default_impact", anchors.get("arena_center"));
        require(!offsets.isEmpty() && offsets.size() <= 64, "Expected 1..64 fixed parts");
        require(offsets.keySet().equals(templates.keySet()), "Part markers and supplied templates differ");
        Map<String, Volume> volumes = new HashMap<>();
        for (String marker : volumeMarkers.keySet()) {
            String name = marker.substring(0, marker.length() - 4);
            BlockPos minimum = volumeMarkers.get(name + "_min");
            BlockPos maximum = volumeMarkers.get(name + "_max");
            require(minimum != null && maximum != null, "Unpaired volume: " + name);
            volumes.put(name, new Volume(minimum, maximum));
        }
        List<Part> parts = new ArrayList<>();
        Set<BlockPos> writes = new HashSet<>();
        for (var entry : offsets.entrySet()) {
            TemplateData template = inspect(templates.get(entry.getKey()), false);
            for (BlockPos position : template.writes()) {
                require(writes.add(entry.getValue().offset(position)), "Overlapping part writes: " + entry.getKey());
            }
            parts.add(new Part(entry.getKey(), entry.getValue(), template.size(), template.dataVersion(),
                    template.airBlocks(), template.retainedBlocks()));
        }
        return new PromisedConsortArenaLayout(anchors, parts, volumes);
    }

    public static PromisedConsortArenaLayout read(
            CompoundTag root, Map<String, CompoundTag> templates, Function<String, BlockDefinition> blockLookup) {
        PromisedConsortArenaLayout layout = read(root, templates);
        validateStates(root, true, "root", blockLookup);
        templates.forEach((name, template) -> validateStates(template, false, name, blockLookup));
        return layout;
    }

    private static void validateStates(
            CompoundTag template, boolean metadata, String templateName, Function<String, BlockDefinition> blockLookup) {
        ListTag palette = compounds(template, "palette");
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag state = palette.getCompound(index);
            String name = state.getString("Name");
            String location = templateName + " palette[" + index + "] " + name;
            BlockDefinition block = blockLookup.apply(name);
            require(block != null, "Unknown block: " + location);
            require(metadata || !block.blockEntity(), "Block entity is forbidden: " + location);
            CompoundTag properties = state.getCompound("Properties");
            for (String key : properties.getAllKeys()) {
                Set<String> property = block.properties().get(key);
                require(property != null, "Unknown property: " + location + " " + key);
                require(property.contains(properties.getString(key)),
                        "Invalid property value: " + location + " " + key + "=" + properties.getString(key));
            }
        }
    }

    public BlockPos worldAnchor(String name, BlockPos worldOrigin, Rotation rotation) {
        BlockPos local = anchors.get(name);
        require(local != null, "Unknown anchor: " + name);
        return worldOrigin.offset(local.rotate(rotation));
    }

    public Vec3 standingAnchor(String name, BlockPos worldOrigin, Rotation rotation) {
        BlockPos position = worldAnchor(name, worldOrigin, rotation);
        return new Vec3(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
    }

    public CompoundTag instanceMetadata(BlockPos worldOrigin, Rotation rotation, String rootHash) {
        CompoundTag data = new CompoundTag();
        data.putInt("Format", 1);
        data.putLong("Origin", worldOrigin.asLong());
        data.putString("Rotation", rotation.name());
        data.putString("RootHash", rootHash);
        CompoundTag anchorData = new CompoundTag();
        anchors.forEach((name, position) -> anchorData.putLong(name, position.asLong()));
        data.put("Anchors", anchorData);
        CompoundTag volumeData = new CompoundTag();
        volumes.forEach((name, volume) -> {
            CompoundTag bounds = new CompoundTag();
            bounds.putLong("Minimum", volume.minimum().asLong());
            bounds.putLong("Maximum", volume.maximum().asLong());
            volumeData.put(name, bounds);
        });
        data.put("Volumes", volumeData);
        return data;
    }

    private static TemplateData inspect(CompoundTag template, boolean metadata) {
        require(template != null, "Missing template");
        require(template.contains("DataVersion", Tag.TAG_INT) && template.getInt("DataVersion") > 0, "Missing DataVersion");
        BlockPos size = vector(template, "size");
        require(size.getX() > 0 && size.getY() > 0 && size.getZ() > 0
                && size.getX() <= 512 && size.getY() <= 512 && size.getZ() <= 512, "Invalid template size");
        require(!template.contains("palettes"), "Random palettes are not supported");
        require(template.contains("entities", Tag.TAG_LIST) && ((ListTag) template.get("entities")).isEmpty(), "Template entities are forbidden");
        ListTag palette = compounds(template, "palette");
        require(!palette.isEmpty(), "Missing palette");
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag state = palette.getCompound(index);
            require(state.contains("Name", Tag.TAG_STRING), "Missing block name");
            String name = state.getString("Name");
            require(name.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") && !FORBIDDEN_BLOCKS.contains(name), "Forbidden block: " + name);
            require(metadata || !name.equals("minecraft:structure_block"), "Markers belong only in the metadata root");
            if (state.contains("Properties")) {
                require(state.contains("Properties", Tag.TAG_COMPOUND), "Invalid state properties");
                CompoundTag properties = state.getCompound("Properties");
                for (String key : properties.getAllKeys()) require(properties.contains(key, Tag.TAG_STRING), "Non-string block property");
            }
        }
        ListTag blocks = compounds(template, "blocks");
        require(blocks.size() <= 1_000_000, "Template exceeds block budget");
        Set<BlockPos> occupied = new HashSet<>();
        Set<BlockPos> writes = new HashSet<>();
        Map<String, BlockPos> markers = new LinkedHashMap<>();
        int air = 0;
        int retained = 0;
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            BlockPos position = vector(block, "pos");
            require(position.getX() >= 0 && position.getY() >= 0 && position.getZ() >= 0
                    && position.getX() < size.getX() && position.getY() < size.getY() && position.getZ() < size.getZ(), "Block outside template bounds");
            require(occupied.add(position), "Duplicate block position: " + position);
            require(block.contains("state", Tag.TAG_INT), "Missing palette index");
            int stateIndex = block.getInt("state");
            require(stateIndex >= 0 && stateIndex < palette.size(), "Invalid palette index");
            CompoundTag state = palette.getCompound(stateIndex);
            String name = state.getString("Name");
            if (name.equals("minecraft:structure_void")) {
                require(!block.contains("nbt"), "Structure void cannot carry block entity data");
                retained++;
            } else if (metadata && name.equals("minecraft:structure_block")) {
                require(block.contains("nbt", Tag.TAG_COMPOUND), "Missing data marker payload");
                CompoundTag payload = block.getCompound("nbt");
                require(payload.getString("mode").equals("DATA")
                        && state.getCompound("Properties").getString("mode").equals("data"), "Only DATA markers are supported");
                String marker = payload.getString("metadata");
                require(markers.putIfAbsent(marker, position) == null, "Duplicate marker: " + marker);
            } else {
                require(!metadata, "Metadata root may contain only markers and structure_void");
                require(!block.contains("nbt"), "Block entities are not allowed in arena parts");
                writes.add(position);
                if (name.equals("minecraft:air")) air++;
            }
        }
        return new TemplateData(size, template.getInt("DataVersion"), writes, markers, air, retained);
    }

    private static ListTag compounds(CompoundTag parent, String key) {
        require(parent.contains(key, Tag.TAG_LIST), "Missing list: " + key);
        ListTag list = (ListTag) parent.get(key);
        require(list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND, "Expected compound list: " + key);
        return list;
    }

    private static BlockPos vector(CompoundTag parent, String key) {
        require(parent.contains(key, Tag.TAG_LIST), "Missing coordinate list: " + key);
        ListTag list = (ListTag) parent.get(key);
        require(list.size() == 3 && list.getElementType() == Tag.TAG_INT, "Expected three integer coordinates: " + key);
        return new BlockPos(list.getInt(0), list.getInt(1), list.getInt(2));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public record BlockDefinition(boolean blockEntity, Map<String, Set<String>> properties) {
        public BlockDefinition {
            properties = properties.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        }

        public static BlockDefinition from(Block block) {
            Map<String, Set<String>> properties = new HashMap<>();
            for (Property<?> property : block.getStateDefinition().getProperties()) {
                properties.put(property.getName(), values(property));
            }
            return new BlockDefinition(block.defaultBlockState().hasBlockEntity(), properties);
        }

        private static <Value extends Comparable<Value>> Set<String> values(Property<Value> property) {
            return property.getPossibleValues().stream().map(property::getName).collect(Collectors.toUnmodifiableSet());
        }
    }

    public record Part(String name, BlockPos offset, BlockPos size, int dataVersion, int airBlocks, int retainedBlocks) {
        public BlockPos placementOrigin(BlockPos worldOrigin, Rotation rotation) {
            return worldOrigin.offset(offset.rotate(rotation));
        }
    }

    public record Volume(BlockPos minimum, BlockPos maximum) {
        public Volume {
            require(minimum.getX() <= maximum.getX() && minimum.getY() <= maximum.getY()
                    && minimum.getZ() <= maximum.getZ(), "Inverted volume");
        }

        public boolean contains(BlockPos position) {
            return position.getX() >= minimum.getX() && position.getX() <= maximum.getX()
                    && position.getY() >= minimum.getY() && position.getY() <= maximum.getY()
                    && position.getZ() >= minimum.getZ() && position.getZ() <= maximum.getZ();
        }
    }

    private record TemplateData(BlockPos size, int dataVersion, Set<BlockPos> writes,
                                Map<String, BlockPos> markers, int airBlocks, int retainedBlocks) {
    }
}