package com.tonywww.elder_bosses.platforms.arena;

import com.tonywww.elder_bosses.arena.PromisedConsortArenaLayout;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaLayout.BlockDefinition;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class PlatformArenaTemplates {
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;
    private static final String PREFIX = "arena/promised_consort/";

    private PlatformArenaTemplates() {
    }

    public static Loaded load(ResourceManager resources, Registry<Block> blocks) throws IOException {
        Map<String, String> hashes = new LinkedHashMap<>();
        CompoundTag root = readResource(resources, "root", hashes);
        Map<String, CompoundTag> parts = new LinkedHashMap<>();
        for (String name : PromisedConsortArenaLayout.partNames(root)) {
            parts.put(name, readResource(resources, name, hashes));
        }
        Map<String, BlockDefinition> definitions = new LinkedHashMap<>();
        PromisedConsortArenaLayout layout = PromisedConsortArenaLayout.read(root, parts,
                name -> definitions.computeIfAbsent(name, key -> blocks.getOptional(PlatformResourceLocation.parse(key))
                        .map(BlockDefinition::from).orElse(null)));
        Map<String, StructureTemplate> templates = new LinkedHashMap<>();
        Map<BlockPos, Integer> foundations = new LinkedHashMap<>();
        parts.forEach((name, data) -> {
            StructureTemplate template = new StructureTemplate();
            template.load(blocks.asLookup(), data);
            templates.put(name, template);
            BlockPos offset = layout.parts().stream().filter(part -> part.name().equals(name)).findFirst().orElseThrow().offset();
            ListTag palette = data.getList("palette", 10);
            boolean[] support = new boolean[palette.size()];
            for (int index = 0; index < palette.size(); index++) {
                var state = NbtUtils.readBlockState(blocks.asLookup(), palette.getCompound(index));
                support[index] = state.getFluidState().isEmpty()
                        && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            }
            ListTag voxels = data.getList("blocks", 10);
            for (int index = 0; index < voxels.size(); index++) {
                CompoundTag voxel = voxels.getCompound(index);
                if (!support[voxel.getInt("state")]) continue;
                ListTag position = voxel.getList("pos", 3);
                BlockPos column = new BlockPos(offset.getX() + position.getInt(0), 0, offset.getZ() + position.getInt(2));
                foundations.merge(column, offset.getY() + position.getInt(1), Math::min);
            }
        });
        return new Loaded(layout, templates, hashes, foundations);
    }

    public static ResourceLocation resourcePath(String name) {
        if (!name.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid arena template name: " + name);
        }
        //? if forge {
        return PlatformResourceLocation.id("structures/" + PREFIX + name + ".nbt");
        //?} else {
        /*return PlatformResourceLocation.id("structure/" + PREFIX + name + ".nbt");
        *///?}
    }

    private static CompoundTag readResource(ResourceManager resources, String name, Map<String, String> hashes) throws IOException {
        ResourceLocation path = resourcePath(name);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        try (InputStream stream = resources.getResourceOrThrow(path).open();
             DigestInputStream hashed = new DigestInputStream(stream, digest)) {
            CompoundTag data = readCompressed(hashed, MAX_NBT_BYTES);
            int currentVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            if (data.getInt("DataVersion") != currentVersion) {
                throw new IOException("Arena template must be exported for DataVersion " + currentVersion + ": " + path);
            }
            hashes.put(name, HexFormat.of().formatHex(digest.digest()));
            return data;
        } catch (IOException | RuntimeException exception) {
            throw new IOException("Cannot load arena template " + path + ": " + exception.getMessage(), exception);
        }
    }

    public static CompoundTag readCompressed(InputStream stream, long byteBudget) throws IOException {
        if (byteBudget <= 0 || byteBudget > MAX_NBT_BYTES) {
            throw new IllegalArgumentException("Invalid arena NBT byte budget");
        }
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(stream))) {
            //? if forge {
            CompoundTag data = NbtIo.read(input, new NbtAccounter(byteBudget));
            //?} else {
            /*CompoundTag data = NbtIo.read(input, NbtAccounter.create(byteBudget));
            *///?}
            if (input.read() != -1) throw new IOException("Trailing arena NBT data");
            return data;
        }
    }

    public record Loaded(PromisedConsortArenaLayout layout, Map<String, StructureTemplate> templates,
                         Map<String, String> hashes, Map<BlockPos, Integer> foundations) {
        public Loaded {
            templates = Map.copyOf(templates);
            hashes = Map.copyOf(hashes);
            foundations = Map.copyOf(foundations);
        }
    }
}