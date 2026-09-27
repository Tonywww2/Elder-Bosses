import com.tonywww.elder_bosses.client.audio.BossMusicState;
import com.tonywww.elder_bosses.client.audio.BossMusicFade;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class BossMusicCheck {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        BossMusicState state = new BossMusicState();
        String boss = "elder_bosses:promised_consort";
        state.observe(1, boss, "phase_one", "dormant", true, 100, 0);
        check(state.select(0, id -> 1, 96) == null, "Dormant bosses have no music");
        state.observe(1, boss, "phase_one", "intro", true, 100, 0);
        check(state.select(0, id -> 1, 96).phase() == 1, "Opening selects instrumental");
        state.observe(2, boss, "phase_two", "phase_2", true, 100, 0);
        check(state.select(0, id -> id == 1 ? 100 : 1, 96).entityId() == 1, "Stable ownership prevents overlapping boss oscillation");
        state.observe(1, boss, "phase_one", "transition", true, 100, 1);
        check(state.select(1, id -> 1, 96).phase() == 2, "Transition selects choir before phase enum changes");
        state.observe(1, boss, "phase_two", "stunned", true, 100, 2);
        check(state.select(2, id -> 1, 96).phase() == 2, "Stagger retains second phase");
        state.observe(1, boss, "phase_two", "meteor_script", true, 100, 3);
        check(state.select(3, id -> 1, 96).phase() == 2, "Meteor script retains second phase");
        state.observe(1, boss, "phase_two", "defeated", true, 0, 4);
        check(state.select(4, id -> 1, 96).entityId() == 2, "Defeat hands off only to another eligible encounter");
        state.observe(2, boss, "phase_two", "phase_2", false, 100, 5);
        check(state.select(5, id -> 1, 96) == null, "HUD audience exit stops playback");
        state.observe(3, "elder_bosses:malenia", "phase_one", "phase_1", true, 100, 5);
        check(state.select(5, id -> 1, 96).boss().equals("elder_bosses:malenia"), "Malenia selects its own music");
        state.observe(3, "elder_bosses:malenia", "phase_one", "transition", true, 0, 6);
        check(state.select(6, id -> 1, 96).phase() == 2, "Zero-health phase transition keeps music and selects phase two");
        state.observe(3, "elder_bosses:malenia", "phase_two", "defeated", true, 0, 7);
        check(state.select(7, id -> 1, 96) == null, "Malenia defeat stops encounter");
        state.observe(4, boss, "phase_one", "phase_1", true, 100, 10);
        check(state.select(10, id -> 96*96+1, 96) == null, "Distance limit");
        check(state.select(10, id -> Double.POSITIVE_INFINITY, 96) == null, "Unloaded entity");
        check(state.select(130, id -> 1, 96) != null, "Exact stale boundary");
        check(state.select(131, id -> 1, 96) == null, "Lost packets expire");
        state.observe(5, boss, "phase_one", "phase_1", true, 100, 140);
        state.remove(5);
        check(state.select(140, id -> 1, 96) == null, "Untracking clears encounter");
        state.observe(6, boss, "phase_one", "phase_1", true, 100, 140);
        state.clear();check(state.select(0, id -> 1, 96) == null, "Disconnect/dimension cleanup resets selection");
        for (float volume : new float[]{0.1F,0.85F,1F}) {
            BossMusicFade fade = new BossMusicFade(40);
            float previous=0;
            for(int tick=1;tick<=40;tick++) {
                float v=fade.tick(volume);
                check(v>=previous && v<=volume && Math.abs(v-volume*tick/40)<0.00001, "Linear fade-in independent of master level");
                previous=v;
            }
            fade.retire(40);
            for(int tick=1;tick<=40;tick++) {
                float v=fade.tick(volume);
                check(v<=previous && v>=0, "Monotonic fade-out"); previous=v;
                if(tick<40)check(!fade.finished(), "Fade must retain requested duration");
            }
            check(fade.finished(), "Finite fade completion");
        }
        var manifest=JsonParser.parseString(Files.readString(Path.of("models/promised_consort/audio/bgm/manifest.json"))).getAsJsonObject();
        var sounds=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/elder_bosses/sounds.json"))).getAsJsonObject();
        for(var value:manifest.getAsJsonArray("tracks")) {
            var track=value.getAsJsonObject();String phase=track.get("phase").getAsString();
            byte[] bytes=Files.readAllBytes(Path.of(track.get("runtime").getAsString()));
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(track.get("sha256").getAsString()),"Pinned music bytes");
            check(new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("OggS"),"Ogg container");
            var sound=sounds.getAsJsonObject("music.promised_consort."+phase).getAsJsonArray("sounds").get(0).getAsJsonObject();
            check(sound.get("stream").getAsBoolean(),"Long music must stream");
            check(sound.get("name").getAsString().equals("elder_bosses:music/promised_consort/"+phase),"Resolved music path");
            for(String target:new String[]{"1.20.1-forge","1.21.1-neoforge"}) {
                var processed=Path.of("versions",target,"build/resources/main/assets/elder_bosses/sounds/music/promised_consort",phase+".ogg");
                check(java.util.Arrays.equals(bytes,Files.readAllBytes(processed)),"Processed music matches on "+target);
                check(Files.exists(Path.of("versions",target,"build/resources/main/META-INF/BGM-CREDITS.txt")),"Packaged music attribution");
            }
        }
        var malenia=JsonParser.parseString(Files.readString(Path.of("models/malenia/audio/manifest.json"))).getAsJsonObject();
        for(var value:malenia.getAsJsonArray("tracks")) {
            var track=value.getAsJsonObject();var file=Path.of(track.get("runtime").getAsString());
            String phase=file.getFileName().toString().replace(".ogg", "");
            byte[] bytes=Files.readAllBytes(file);
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(track.get("sha256").getAsString()),"Pinned Malenia loop bytes");
            check(sounds.getAsJsonObject("music.malenia."+phase).getAsJsonArray("sounds").get(0).getAsJsonObject().get("stream").getAsBoolean(),"Malenia music streams");
            check(track.get("loop_boundary_step").getAsDouble()<.003,"Malenia loop seam de-clicked");
            for(String target:new String[]{"1.20.1-forge","1.21.1-neoforge"}) {
                check(java.util.Arrays.equals(bytes,Files.readAllBytes(Path.of("versions",target,"build/resources/main/assets/elder_bosses/sounds/music/malenia",phase+".ogg"))),"Malenia audio packaged on "+target);
            }
        }
        var parry=malenia.getAsJsonObject("parry");var impact=Files.readAllBytes(Path.of(parry.get("runtime").getAsString()));
        check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(impact)).equals(parry.get("sha256").getAsString()),"Pinned success sound");
        check(parry.get("channels").getAsInt()==1,"Success impact supports positional mono audio");
        check(sounds.getAsJsonObject("entity.malenia.parry_success").getAsJsonArray("sounds").get(0).getAsJsonObject().get("preload").getAsBoolean(),"Short parry sound preloads");
        check(Files.readString(Path.of("src/main/resources/META-INF/BGM-CREDITS.txt")).contains("Sascha Ende"),"Malenia attribution retained");
        System.out.println("BossMusicCheck passed: "+checks+" checks (selection, lifecycle, fades, resources; no in-game audio claim)");
    }
}
