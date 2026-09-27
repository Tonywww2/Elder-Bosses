package com.tonywww.elder_bosses.platforms.arena;

import com.tonywww.elder_bosses.arena.MaleniaArenaLayout;
import com.tonywww.elder_bosses.arena.MaleniaArenaFloor;
import com.tonywww.elder_bosses.arena.MaleniaArenaLayout.BlockDefinition;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class PlatformMaleniaArenaTemplates {
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;
    private static final String PREFIX = "arena/malenia/";

    private PlatformMaleniaArenaTemplates() {
    }

    public static Loaded load(ResourceManager resources, Registry<Block> blocks) throws IOException {
        return load(resources, blocks, "root");
    }

    public static Loaded loadWorldgen(ResourceManager resources, Registry<Block> blocks) throws IOException {
        return load(resources, blocks, "worldgen_root");
    }

    private static Loaded load(ResourceManager resources, Registry<Block> blocks, String rootName) throws IOException {
        Map<String, String> hashes = new LinkedHashMap<>();
        CompoundTag root = readResource(resources, rootName, hashes);
        Map<String, CompoundTag> parts = new LinkedHashMap<>();
        for (String name : MaleniaArenaLayout.partNames(root)) {
            parts.put(name, readResource(resources, name, hashes));
        }
        Map<String, BlockDefinition> definitions = new LinkedHashMap<>();
        MaleniaArenaLayout layout = MaleniaArenaLayout.read(root, parts,
                name -> definitions.computeIfAbsent(name, key -> blocks.getOptional(PlatformResourceLocation.parse(key))
                        .map(BlockDefinition::from).orElse(null)));
        Map<String, StructureTemplate> templates = new LinkedHashMap<>();
        Map<BlockPos, BlockState> composition = new LinkedHashMap<>();
        parts.forEach((name, data) -> {
            StructureTemplate template = new StructureTemplate();
            template.load(blocks.asLookup(), data);
            templates.put(name, template);
            BlockPos offset = layout.parts().stream().filter(part -> part.name().equals(name)).findFirst().orElseThrow().offset();
            ListTag palette = data.getList("palette", 10);
            BlockState[] states = new BlockState[palette.size()];
            for (int index = 0; index < palette.size(); index++) {
                states[index] = NbtUtils.readBlockState(blocks.asLookup(), palette.getCompound(index));
                if (!states[index].getFluidState().isEmpty() && !MaleniaArenaFloor.isPoolSlab(states[index])) {
                    throw new IllegalArgumentException("Arena contains uncontained fluid");
                }
            }
            ListTag voxels = data.getList("blocks", 10);
            for (int index = 0; index < voxels.size(); index++) {
                CompoundTag voxel = voxels.getCompound(index);
                BlockState state = states[voxel.getInt("state")];
                if (state.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)) continue;
                ListTag position = voxel.getList("pos", 3);
                composition.put(offset.offset(position.getInt(0),position.getInt(1),position.getInt(2)),state);
            }
        });
        validateGeometry(layout, composition);
        return new Loaded(layout, templates, hashes, composition);
    }

    private static void validateGeometry(MaleniaArenaLayout layout, Map<BlockPos,BlockState> blocks) {
        blocks.forEach((pos,state)->{
            if (!MaleniaArenaFloor.isPoolSlab(state)) return;
            var below=blocks.get(pos.below());
            if (below==null || !below.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,pos.below())) {
                throw new IllegalArgumentException("Unsealed pool bottom at "+pos);
            }
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var neighborPos=pos.relative(direction);
                var neighbor=blocks.get(neighborPos);
                if (neighbor==null || !(MaleniaArenaFloor.isPoolSlab(neighbor)
                        || neighbor.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,neighborPos))) {
                    throw new IllegalArgumentException("Unsealed pool shore at "+pos);
                }
            }
        });
        BlockPos center = layout.anchors().get("arena_center");
        Map<BlockPos,Double> heights = new LinkedHashMap<>();
        for (int x=-26;x<=26;x++) for(int z=-26;z<=26;z++) {
            if(x*x+z*z>26*26)continue;
            int floor = -1;
            double height = -1;
            for(int rise=5;rise>=0;rise--) {
                BlockPos pos=center.offset(x,rise-1,z);
                var state=blocks.get(pos);
                double top=state==null ? -1 : MaleniaArenaFloor.top(state,EmptyBlockGetter.INSTANCE,pos);
                if(top>0){floor=rise;height=rise-1+top;break;}
            }
            if(floor<0 || x*x+z*z<=18*18 && floor!=0) throw new IllegalArgumentException("Invalid arena floor at "+x+", "+z);
            heights.put(new BlockPos(x,0,z),height);
            for(int y=0;y<18;y++) requireClear(blocks,center.offset(x,floor+y,z));
        }
        heights.forEach((p,y)->{
            for(var delta : new BlockPos[]{new BlockPos(1,0,0),new BlockPos(0,0,1)}) {
                Double neighbor=heights.get(p.offset(delta));
                if(neighbor!=null && Math.abs(y-neighbor)>1)throw new IllegalArgumentException("Unwalkable arena slope");
            }
        });
        layout.anchors().forEach((name,pos)->{
            if(name.equals("arena_origin"))return;
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<4;y++)requireClear(blocks,pos.offset(x,y,z));
            if(!name.equals("aeonia_opening")) {
                var support=blocks.get(pos.below());
                if(support==null || MaleniaArenaFloor.top(support,EmptyBlockGetter.INSTANCE,pos.below())<0) {
                    throw new IllegalArgumentException("Unsupported arena anchor: "+name);
                }
            }
        });
    }

    private static void requireClear(Map<BlockPos,BlockState> blocks,BlockPos pos) {
        var state=blocks.get(pos);
        if(state==null || !state.getCollisionShape(EmptyBlockGetter.INSTANCE,pos).isEmpty()) {
            throw new IllegalArgumentException("Arena clearance obstructed at "+pos.toShortString());
        }
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

    public record Loaded(MaleniaArenaLayout layout, Map<String, StructureTemplate> templates,
                         Map<String, String> hashes, Map<BlockPos, BlockState> composition) {
        public Loaded {
            templates = Map.copyOf(templates);
            hashes = Map.copyOf(hashes);
            // BlockPos hashes occupy a narrow range in a dense voxel grid. Map.copyOf
            // uses linear probing and becomes quadratic here; retain hash buckets.
            composition = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(composition));
        }
    }
}
