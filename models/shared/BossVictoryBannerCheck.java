import com.google.gson.JsonParser;
import com.tonywww.elder_bosses.client.hud.BossVictoryBannerState;
import com.tonywww.elder_bosses.network.BossDefeatedPacket;
import com.tonywww.elder_bosses.network.BossDefeatedPacket.Victory;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

public final class BossVictoryBannerCheck {
    private static int checks;
    private static final UUID RADAHN = new UUID(0, 1);
    private static final UUID MALENIA = new UUID(0, 2);

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        var state = new BossVictoryBannerState();
        require(!state.frame(0).visible(), "Idle HUD must be empty");
        state.offer(RADAHN, Victory.GOD_SLAIN);
        float previous = 0;
        for (int tick = 0; tick < BossVictoryBannerState.DURATION_TICKS; tick++) {
            for (int sample = 0; sample < 10; sample++) {
                float opacity = state.frame(sample / 10.0F).opacity();
                require(Float.isFinite(opacity) && opacity >= 0 && opacity <= 1,
                        "Invalid fade opacity");
                if (tick < BossVictoryBannerState.FADE_IN_TICKS)
                    require(opacity >= previous, "Fade-in must be monotonic");
                else if (tick >= BossVictoryBannerState.FADE_IN_TICKS + BossVictoryBannerState.HOLD_TICKS)
                    require(opacity <= previous, "Fade-out must be monotonic");
                else require(opacity == 1, "Hold must remain opaque");
                previous = opacity;
            }
            if (tick == 35) {
                float before = state.frame(0).opacity();
                state.offer(RADAHN, Victory.GOD_SLAIN);
                require(state.frame(0).opacity() == before, "Duplicate event restarted banner");
                state.offer(MALENIA, Victory.DEMIGOD_FELLED);
            }
            state.tick();
        }
        require(state.frame(0).victory() == Victory.DEMIGOD_FELLED, "Concurrent victory was lost");
        require(state.frame(0).opacity() == 0, "Queued banner must have its own fade-in");
        for (int tick = 0; tick < BossVictoryBannerState.DURATION_TICKS; tick++) state.tick();
        require(state.frame(0).victory() == null, "Duplicate packet queued another banner");
        state.offer(RADAHN, Victory.GOD_SLAIN);
        require(state.frame(0).victory() == null, "Completed encounter replayed");
        state.clear();
        state.offer(RADAHN, Victory.GOD_SLAIN);
        state.offer(MALENIA, Victory.DEMIGOD_FELLED);
        state.clear();
        for (int i = 0; i < 200; i++) state.tick();
        require(state.frame(0).victory() == null, "World change left an active/queued banner");
        state.offer(RADAHN, Victory.GOD_SLAIN);
        require(state.frame(0).victory() == Victory.GOD_SLAIN, "New session retained old UUIDs");
        state.clear();
        for (int i = 0; i < 2000; i++) state.offer(new UUID(1, i), Victory.GOD_SLAIN);
        for (int i = 0; i < 9 * BossVictoryBannerState.DURATION_TICKS; i++) state.tick();
        require(state.frame(0).victory() == null, "Mass kills produced an unbounded queue");

        for (Victory victory : Victory.values()) {
            for (String dimension : new String[] {"minecraft:overworld", "elder_bosses:arena"}) {
                var packet = new BossDefeatedPacket(UUID.randomUUID(), new ResourceLocation(dimension), victory);
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    packet.write(buffer);
                    require(packet.equals(BossDefeatedPacket.read(buffer)), "Victory packet round-trip failed");
                    require(!buffer.isReadable(), "Victory decoder left unread data");
                } finally {
                    buffer.release();
                }
            }
        }

        Path resources = Path.of("src/main/resources");
        Path fontPath = Path.of("assets/elder_bosses/font/cinzel_regular.ttf");
        Font font = Font.createFont(Font.TRUETYPE_FONT, resources.resolve(fontPath).toFile());
        require(font.canDisplayUpTo("GOD SLAIN DEMIGOD FELLED") == -1, "Missing victory glyphs");
        String digest = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(resources.resolve(fontPath))));
        require(digest.equals("af0031129f27dc752e8629a80b793d27abea94027faa27cc660c3fc33f607a1f"),
                "Bundled font differs from credited upstream");
        var definition = JsonParser.parseString(Files.readString(resources.resolve(
                "assets/elder_bosses/font/victory.json"))).getAsJsonObject();
        require(definition.getAsJsonArray("providers").get(0).getAsJsonObject()
                .get("file").getAsString().equals("elder_bosses:cinzel_regular.ttf"), "Invalid font reference");
        for (String language : new String[] {"en_us", "zh_cn"}) {
            var translations = JsonParser.parseString(Files.readString(resources.resolve(
                    "assets/elder_bosses/lang/" + language + ".json"))).getAsJsonObject();
            require(translations.get(Victory.GOD_SLAIN.languageKey()).getAsString().equals("GOD SLAIN"),
                    "Missing Radahn title");
            require(translations.get(Victory.DEMIGOD_FELLED.languageKey()).getAsString().equals("DEMIGOD FELLED"),
                    "Missing Malenia title");
        }
        for (String version : new String[] {"1.20.1-forge", "1.21.1-neoforge"}) {
            for (String asset : new String[] {fontPath.toString(), "assets/elder_bosses/font/victory.json",
                    "META-INF/CINZEL-OFL.txt", "META-INF/VICTORY-FONT-CREDITS.txt"}) {
                require(Arrays.equals(Files.readAllBytes(resources.resolve(asset)), Files.readAllBytes(
                        Path.of("versions", version, "build/resources/main").resolve(asset))),
                        version + " omitted/changed banner font resource: " + asset);
            }
        }
        System.out.println("Boss victory banner passed: " + checks + " checks (fade, queue, deduplication, lifecycle, packet, font and both resource outputs)");
    }
}
