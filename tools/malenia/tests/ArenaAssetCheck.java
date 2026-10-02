import com.tonywww.elder_bosses.arena.MaleniaArenaLayout;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaTemplates;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import java.nio.file.*;
import java.util.*;

/** Contract regressions for the published two-version NBT and malformed overrides. */
public final class ArenaAssetCheck {
    static int checks;
    static CompoundTag read(Path p)throws Exception{try(var in=Files.newInputStream(p)){return PlatformArenaTemplates.readCompressed(in,64L*1024*1024);}}
    public static void main(String[] args)throws Exception{
        verifyMaterials();
        for(String rootName:List.of("root","worldgen_root"))verify(rootName);
        System.out.println("Malenia arena asset checks passed: "+checks+"; both roots, exact dual-version parity, complete seams, four rotations and malformed resources");
    }
    static void verify(String rootName)throws Exception{
        boolean natural=rootName.equals("worldgen_root");
        Path root=Path.of("src/main/resources/data/elder_bosses");
        CompoundTag manifest=read(root.resolve("structures/arena/malenia/"+rootName+".nbt"));
        Map<String,CompoundTag> parts=new LinkedHashMap<>();
        List<String> names=new ArrayList<>(MaleniaArenaLayout.partNames(manifest));names.add(rootName);
        for(String name:names){
            CompoundTag forge=read(root.resolve("structures/arena/malenia/"+name+".nbt"));
            CompoundTag neo=read(root.resolve("structure/arena/malenia/"+name+".nbt"));
            check(forge.getInt("DataVersion")==3465 && neo.getInt("DataVersion")==3955,"Wrong target DataVersion");
            neo.putInt("DataVersion",3465);check(forge.equals(neo),"Cross-version geometry drift: "+name);
            if(!name.equals(rootName))parts.put(name,forge);
        }
        var layout=MaleniaArenaLayout.read(manifest,parts);
        check(layout.parts().size()==(natural?19:18),"Wrong part count");
        Map<BlockPos,String> blocks=new HashMap<>();
        for(var part:layout.parts()){
            var data=parts.get(part.name());var palette=data.getList("palette",10);
            for(var entry:data.getList("blocks",10)){
                var b=(CompoundTag)entry;var pos=b.getList("pos",3);
                BlockPos p=part.offset().offset(pos.getInt(0),pos.getInt(1),pos.getInt(2));
                var state=palette.getCompound(b.getInt("state"));
                check(blocks.put(p,state.getString("Name"))==null,"Part seam overlaps");
                if(state.getString("Name").endsWith("haligtree_silt_slab")) {
                    check(state.getCompound("Properties").getString("waterlogged").equals("true"),"Dry slab in pool");
                    check(state.getCompound("Properties").getString("type").equals("bottom"),"Wrong pool slab half");
                }
            }
        }
        check(blocks.size()==73*42*83+(natural?15*27*17:0),"Incomplete explicit-air volume");
        if(natural){
            check(layout.anchors().get("surface_entry").equals(new BlockPos(3,58,46)),"Surface entry moved");
            check(blocks.values().stream().filter(s->s.equals("minecraft:stone_brick_stairs")).count()>=156,"Stair flights lost");
        }
        check(blocks.values().stream().filter(s->s.endsWith("haligtree_white_petals")).count()>650,"Flower banks lost");
        check(blocks.values().stream().noneMatch(s->s.equals("minecraft:water")||s.equals("minecraft:lava")||s.endsWith("haligtree_shallow_water")),"Uncontained fluid or legacy film in arena");
        check(blocks.values().stream().filter(s->s.endsWith("haligtree_silt_slab")).count()>250,"Pool slab basin lost");
        for(var rotation:net.minecraft.world.level.block.Rotation.values()){
            BlockPos origin=new BlockPos(172,80,-256);
            Set<BlockPos> rotated=new HashSet<>();
            for(BlockPos p:blocks.keySet())check(rotated.add(origin.offset(p.rotate(rotation))),"Rotation collapses voxels");
            check(layout.worldAnchor("boss_spawn",origin,rotation).equals(origin.offset(new BlockPos(0,1,-11).rotate(rotation))),"Spawn rotation wrong");
        }
        var missing=new LinkedHashMap<>(parts);missing.remove(layout.parts().get(0).name());
        reject(()->MaleniaArenaLayout.read(manifest,missing),"Missing part accepted");
        CompoundTag duplicate=manifest.copy();var marks=duplicate.getList("blocks",10);marks.add(marks.getCompound(0).copy());
        reject(()->MaleniaArenaLayout.read(duplicate,parts),"Duplicate marker accepted");
        CompoundTag unknown=manifest.copy();unknown.getList("blocks",10).getCompound(0).getCompound("nbt").putString("metadata","anchor:unknown");
        reject(()->MaleniaArenaLayout.read(unknown,parts),"Unknown anchor accepted");
        CompoundTag overlap=manifest.copy();var markers=overlap.getList("blocks",10);ListTag first=null;
        for(var raw:markers){var tag=(CompoundTag)raw;if(tag.getCompound("nbt").getString("metadata").startsWith("part:")){
            if(first==null)first=tag.getList("pos",3).copy();else{tag.put("pos",first.copy());break;}}}
        reject(()->MaleniaArenaLayout.read(overlap,parts),"Overlap accepted");
        var invalid=new LinkedHashMap<>(parts);String firstName=layout.parts().get(0).name();var bad=invalid.get(firstName).copy();
        bad.getList("blocks",10).getCompound(0).putInt("state",99999);invalid.put(firstName,bad);
        reject(()->MaleniaArenaLayout.read(manifest,invalid),"Bad palette index accepted");
    }
    static void reject(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError(message);}
    static void verifyMaterials()throws Exception {
        Path assets=Path.of("src/main/resources/assets/elder_bosses");
        for(String name:List.of("haligtree_root","haligtree_silt","haligtree_white_petals","haligtree_altar")) {
            var image=javax.imageio.ImageIO.read(assets.resolve("textures/block/"+name+".png").toFile());
            check(image.getWidth()==16 && image.getHeight()==16,"Texture must be native 16x16: "+name);
            int transparent=0;Set<Integer> colors=new HashSet<>();
            for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
                int pixel=image.getRGB(x,y),alpha=pixel>>>24;
                check(alpha==0 || alpha==255,"Texture contains blurred alpha fringes");
                if(alpha==0)transparent++;else colors.add(pixel);
            }
            check(colors.size()>4,"Material lost color variation");
            check(name.endsWith("petals") ? transparent>80 && transparent<220 : transparent==0,"Invalid texture transparency");
        }
        var variants=com.google.gson.JsonParser.parseString(Files.readString(assets.resolve("blockstates/haligtree_white_petals.json")))
                .getAsJsonObject().getAsJsonObject("variants").getAsJsonArray("");
        check(variants.size()==12,"Flower model/rotation diversity lost");
        for(String suffix:List.of("","_2","_3")) {
            var model=com.google.gson.JsonParser.parseString(Files.readString(assets.resolve("models/block/haligtree_white_petals"+suffix+".json"))).getAsJsonObject();
            int planes=model.getAsJsonArray("elements").size();
            check(planes>=14 && planes<=22,"Flower clump must contain 7-11 crossed plants");
            check(model.getAsJsonObject("textures").get("flower").getAsString().equals("elder_bosses:block/haligtree_white_petals"),"Placeholder flower sprite remains");
        }
    }
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
