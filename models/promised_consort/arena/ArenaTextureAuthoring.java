import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

public final class ArenaTextureAuthoring {
    private static final Path ASSETS = Path.of("src/main/resources/assets/elder_bosses/textures");

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 1 && arguments[0].equals("--custom-textures")) {
            Path input = Path.of("build/ai-previews/arena-custom-v6/rasters");
            try (var files = Files.list(input)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".argb")).toList()) {
                    byte[] bytes = Files.readAllBytes(file);
                    if (bytes.length != 16 * 16 * 4) throw new IllegalArgumentException("Expected 16x16 ARGB raster");
                    BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                    var pixels = java.nio.ByteBuffer.wrap(bytes);
                    for (int row = 0; row < 16; row++) for (int column = 0; column < 16; column++) image.setRGB(column, row, pixels.getInt());
                    Path destination = ASSETS.resolve("block/" + file.getFileName().toString().replace(".argb", ".png"));
                    Files.createDirectories(destination.getParent());
                    if (!ImageIO.write(image, "png", destination.toFile())) throw new IllegalStateException("PNG writer unavailable");
                    BufferedImage restored = ImageIO.read(destination.toFile());
                    for (int row = 0; row < 16; row++) for (int column = 0; column < 16; column++) {
                        if (restored.getRGB(column, row) != image.getRGB(column, row)) throw new IllegalStateException("Lossy pixel export");
                    }
                }
            }
            System.out.println("Custom native16x material rasters exported losslessly.");
            return;
        }
        if (arguments.length == 1 && arguments[0].equals("--sample-vanilla")) {
            extractSampleResources();
            return;
        }
        if (arguments.length == 1 && arguments[0].equals("--sample-candidate")) {
            writeCandidateTextures(false);
            return;
        }
        if (arguments.length == 1 && arguments[0].equals("--sample-refined")) {
            writeCandidateTextures(true);
            return;
        }
        if (arguments.length == 1 && arguments[0].equals("--sample-sculpted")) {
            writeSculptedTextures();
            return;
        }
        if (arguments.length == 1 && arguments[0].equals("--sample-voxel")) {
            writeVoxelTextures();
            return;
        }
        if (arguments.length != 0) throw new IllegalArgumentException("Use no arguments or a --sample-vanilla/candidate/refined/sculpted/voxel mode");
        int[] stone = {0x777970, 0x96988c, 0xb0b2a6, 0xc3c4b8, 0xd2d2c6};
        String[] surface = {
            "2223333322223332", "2333443332233332", "2334443322333222", "2234432223332212",
            "2223322222321112", "3222223322211223", "3332234432222233", "3322334433332233",
            "3222344334432223", "2223333334332222", "2223222233322332", "2332211222223443",
            "3332112222333443", "3322222333333332", "3223333443322222", "2233333443222332"
        };
        write("block/weathered_divine_stone", stone, surface);
        write("block/root_relief_stone", new int[]{0x494d44, 0x707669, 0x979e8c, 0xb7beaa, 0xd0d3bf}, new String[]{
            "2210422210322103", "2210432210432103", "2104322104322103", "2104321043222104",
            "2103210432221043", "2210104322210432", "2221043222104322", "2210403221043222",
            "2104320320432221", "1043222104322210", "0432221043222104", "4322210432221043",
            "3222104322210432", "3221043222210432", "2210432222104322", "2210432221032222"
        });
        write("block/pale_sediment", stone, new String[]{
            "2233332223333222", "2333443234433332", "2334433334433443", "2234333443333443",
            "2333223443223332", "3343223332234322", "3443322222344332", "3333443223344332",
            "2233443333333222", "2223333443322233", "2333223443222344", "3443322333332344",
            "3444332234432333", "3334432234433322", "2233333333333222", "2222333332222222"
        });
        write("block/consort_altar_top", new int[]{0x4f574c, 0x7d8772, 0xa9af9b, 0xc4c7b3, 0xdecfb0}, new String[]{
            "2333333333333332", "3222222222222223", "3211111111111123", "3212222222222123",
            "3212221043222123", "3212210432222123", "3212104322222123", "3211043222222123",
            "3212104322222123", "3212210432222123", "3212221043222123", "3212222104322123",
            "3212222222222123", "3211111111111123", "3222222222222223", "2333333333333332"
        });
        write("block/consort_altar_side", stone, new String[]{
            "3333334443333333", "2222223322222222", "1112222222211111", "2222333333322222",
            "2333332223333332", "2332222222223332", "2322212222122332", "2322112222112332",
            "2322122222212332", "2322222222222332", "2332223332223332", "2333334433333332",
            "2223333333332222", "1111222222221111", "3333333333333333", "2222222222222222"
        });
        write("item/rune_fragment", new int[]{0x00000000, 0x635b39, 0x96804a, 0xc0a867, 0xe3d599}, new String[]{
            "0000000000000000", "0000000011000000", "0000000123100000", "0000001234310000",
            "0000012343210000", "0000123432100000", "0001234321000000", "0012343211100000",
            "0013432234310000", "0001321234321000", "0000112343210000", "0000013432100000",
            "0000001321000000", "0000000110000000", "0000000000000000", "0000000000000000"
        });
        System.out.println("Authored 6 original 16x16 arena textures.");
    }

    private static void extractSampleResources() throws Exception {
        Path classpath = Path.of("versions/1.20.1-forge/.gradle/loom-cache/forge_minecraft_classpath.txt");
        Path archive = Files.readAllLines(classpath).stream().map(String::trim)
                .filter(line -> line.endsWith("client-extra.jar")).map(Path::of).findFirst().orElseThrow();
        Path output = Path.of("build/ai-previews/arena-material-resources");
        String[] resources = {
                "models/block/block.json", "models/block/cube.json", "models/block/cube_all.json",
                "models/block/slab.json", "models/block/slab_top.json",
                "models/block/smooth_stone.json", "models/block/smooth_stone_slab.json",
                "models/block/smooth_stone_slab_top.json", "models/block/smooth_stone_slab_double.json",
                "models/block/cube_column.json", "blockstates/smooth_stone_slab.json", "blockstates/stone_brick_stairs.json",
                "models/block/template_wall_post.json", "models/block/template_wall_side.json",
                "models/block/stone_brick_wall_post.json", "models/block/stone_brick_wall_side.json",
                "textures/block/smooth_stone.png", "textures/block/smooth_stone_slab_side.png",
                "textures/block/stone_bricks.png"
        };
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (String resource : resources) {
                String name = "assets/minecraft/" + resource;
                var entry = zip.getEntry(name);
                if (entry == null) throw new IllegalStateException("Missing vanilla study resource: " + name);
                Path destination = output.resolve(name);
                Files.createDirectories(destination.getParent());
                try (var input = zip.getInputStream(entry)) {
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        System.out.println("Extracted " + resources.length + " vanilla resources for local material study only: " + output);
    }

        private static void writeCandidateTextures(boolean refined) throws Exception {
        Path output = Path.of("build/ai-previews", refined ? "arena-material-refined" : "arena-material-candidate",
            "assets/elder_bosses/textures");
        int[] floorPalette = refined ? new int[]{0xacafa3, 0xb4b6ab, 0xb9bbaf, 0xbec0b5, 0xc6c8bd}
            : new int[]{0xa6a89c, 0xb1b3a7, 0xb9bbaf, 0xc0c2b6, 0xc8cabf};
        String[] floorRows = refined ? new String[]{
            "3333222222111123", "3333322222111223", "3333322222111223", "3333222221112223",
            "3332222221122233", "3322222221222333", "3222222211223333", "3222222111223333",
            "3322222111222333", "3322221112222333", "3332221112222233", "3332222111222233",
            "3333222211122223", "3333222221112223", "3333222222111223", "3333222222111123"
        } : new String[]{
                "2222222222222222", "2222222223332222", "2222222333333222", "2222223333333222",
                "2222223333333222", "2222222333333222", "2222222233332222", "2222222222222222",
                "2222222222222222", "2222112222222222", "2221111222222222", "2221111222222222",
                "2222112222222222", "2222222222222222", "2222222222222222", "2222222222222222"
        };
        int[] reliefPalette = refined ? new int[]{0x858b7f, 0x999f91, 0xa6ab9e, 0xb3b9aa, 0xc5cbbd}
            : new int[]{0x74796f, 0x8f9487, 0xa6ab9e, 0xbfc3b4, 0xd0d3c2};
        String[] reliefRows = refined ? new String[]{
            "2222322222233222", "2223332222232222", "2233322222222222", "2233222222122222",
            "2223222221122222", "2222222221232222", "2222222212333222", "2222211122333222",
            "2222112222233222", "2222122222232222", "2221232222222222", "2221333222222222",
            "2222333222122222", "2222332221122222", "2222322222233222", "2222322222233222"
        } : new String[]{
                "2223332222333222", "2213432222333222", "2213432221233222", "2213432221343222",
                "2213332211343222", "2223332111333222", "2223331122333222", "2223321222333222",
                "2223212222333222", "2223122221233222", "2213322221343222", "2213432221343222",
                "2213432221333222", "2213332222333222", "2223332222333222", "2223332222333222"
        };
        if (refined) {
            requireConnectedWear(floorRows);
            requireBrokenHighlights(reliefRows);
            String[] dotted = floorRows.clone();
            java.util.Arrays.fill(dotted, "2222222222222222");
            dotted[7] = "2222222322222222";
            expectPatternFailure(() -> requireConnectedWear(dotted), "isolated floor patch");
            String[] striped = reliefRows.clone();
            java.util.Arrays.fill(striped, "2222322222222222");
            expectPatternFailure(() -> requireBrokenHighlights(striped), "unbroken relief highlight");
        }
        write(output, "block/weathered_divine_stone", floorPalette, floorRows);
        write(output, "block/root_relief_stone", reliefPalette, reliefRows);
        for (String name : new String[]{"weathered_divine_stone", "root_relief_stone"}) {
            BufferedImage original = ImageIO.read(ASSETS.resolve("block/" + name + ".png").toFile());
            BufferedImage candidate = ImageIO.read(output.resolve("block/" + name + ".png").toFile());
            double originalVariation = neighborVariation(original);
            double candidateVariation = neighborVariation(candidate);
            if (candidateVariation >= originalVariation * 0.8) throw new IllegalStateException("Candidate noise was not reduced: " + name);
            System.out.printf(java.util.Locale.ROOT, "%s: mean neighbor luma difference %.3f -> %.3f%n", name, originalVariation, candidateVariation);
        }
        System.out.println("Authored two local " + (refined ? "refined v3" : "candidate v2") + " textures; runtime assets unchanged.");
        if (refined) System.out.println("Cross-edge floor wear, broken highlights and two negative controls passed.");
    }

    private static void writeVoxelTextures() throws Exception {
        Path output = Path.of("build/ai-previews/arena-material-voxel/assets/elder_bosses/textures");
        write(output, "block/weathered_divine_stone", new int[]{0x999e94,0xadb1a7,0xb9bdb3,0xc3c6bd,0xcdd0c7}, new String[]{
                "2222233322222222", "2222333322222222", "2222333222211222", "2222332222111122",
                "2222222222111222", "3222222222112223", "3332222222222233", "3333222222222333",
                "2333222222222332", "2232222233222222", "2222222333322222", "2221222333322222",
                "2211122333222222", "2211122222222222", "2221222222222222", "2222233322222222"
        });
        write(output, "block/root_relief_stone", new int[]{0x636d60,0x828d7c,0xa1ab99,0xb9c1ae,0xcdd2c1}, new String[]{
                "2223222212222322", "2223322212223322", "2213322222233222", "2213322222232222",
                "2212222221222222", "2222222211222222", "2222222112332222", "2222122112333222",
                "2221122222332222", "2211222222222222", "2212332222222122", "2223332222221122",
                "2223322222211222", "2222222222212222", "2223222222222222", "2223222212222322"
        });
        write(output, "block/pale_sediment", new int[]{0xa1a99a,0xb5bcae,0xc7ccbf,0xd5d8ce,0xe1e2d9}, new String[]{
                "2223332222223322", "2233332222233332", "2334433222233322", "2334333222222222",
                "2233322222112222", "2222222221112222", "2222212221122232", "3322112222222333",
                "3322122222223343", "2222222333222332", "2222223343322222", "2221223344322222",
                "2211222333322222", "2212222222222222", "2222222222223222", "2223332222223322"
        });
        for (String name : new String[]{"weathered_divine_stone", "root_relief_stone", "pale_sediment"}) {
            BufferedImage image = ImageIO.read(output.resolve("block/" + name + ".png").toFile());
            if (image.getWidth() != 16 || image.getHeight() != 16) throw new IllegalStateException("Not native 16x: " + name);
            java.util.Set<Integer> colors = new java.util.HashSet<>();
            for (int row = 0; row < 16; row++) for (int column = 0; column < 16; column++) {
                int color = image.getRGB(column, row);
                if (color >>> 24 != 255) throw new IllegalStateException("Unexpected transparency");
                colors.add(color);
            }
            if (colors.size() > 5) throw new IllegalStateException("Interpolated texture colors");
            System.out.println(name + ": native 16x16, opaque, " + colors.size() + " discrete colors");
        }
    }

    private static void writeSculptedTextures() throws Exception {
        Path output = Path.of("build/ai-previews/arena-material-sculpted/assets/elder_bosses/textures/block");
        Files.createDirectories(output);
        for (String name : new String[]{"weathered_divine_stone", "root_relief_stone", "pale_sediment"}) {
            BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            var brush = image.createGraphics();
            brush.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
            boolean relief = name.equals("root_relief_stone");
            boolean sediment = name.equals("pale_sediment");
            brush.setColor(new java.awt.Color(relief ? 0x979c90 : sediment ? 0xc5c7bd : 0xb8baaf));
            brush.fillRect(0, 0, 32, 32);
            polygon(brush, relief ? 0xa0a597 : sediment ? 0xd1d2c8 : 0xbbbdB2,
                    new int[]{-4,8,14,12,5,-3}, new int[]{2,-2,6,13,18,15});
            polygon(brush, relief ? 0x91988c : sediment ? 0xb9bdb2 : 0xb5b8ac,
                    new int[]{18,25,34,36,27,22,16}, new int[]{17,13,16,29,35,30,24});
            polygon(brush, relief ? 0xa5aa9b : sediment ? 0xd8d8cf : 0xbdbfb4,
                    new int[]{-3,6,13,17,12,2,-3}, new int[]{29,26,27,32,36,35,29});
            if (relief) {
                stroke(brush, 0x828b7c, 2, new int[]{7,8,8}, new int[]{1,6,11});
                stroke(brush, 0xb0b8a5, 2, new int[]{5,6,6}, new int[]{1,6,10});
                stroke(brush, 0x879080, 2, new int[]{6,5,5}, new int[]{19,25,30});
                stroke(brush, 0xaab39e, 2, new int[]{4,3,3}, new int[]{20,25,30});
                stroke(brush, 0x848d7e, 3, new int[]{24,23,23}, new int[]{-3,1,6});
                stroke(brush, 0xadb5a1, 2, new int[]{26,25,25}, new int[]{-3,1,5});
                stroke(brush, 0x889181, 2, new int[]{21,22,22}, new int[]{12,16,22});
                stroke(brush, 0xb0b8a6, 2, new int[]{23,24,24}, new int[]{12,16,21});
                stroke(brush, 0x969e8d, 1, new int[]{11,14,16}, new int[]{16,15,12});
                stroke(brush, 0xa8b19d, 1, new int[]{11,13}, new int[]{15,14});
                stroke(brush, 0x8d9586, 1, new int[]{15,15,14}, new int[]{25,27,29});
                stroke(brush, 0xb6bdac, 1, new int[]{5,6}, new int[]{4,7});
            } else if (sediment) {
                polygon(brush, 0xb1b8ab, new int[]{3,9,12,9,4}, new int[]{5,3,7,10,9});
                polygon(brush, 0xdeddd3, new int[]{5,9,11,8,4}, new int[]{4,3,6,8,7});
                polygon(brush, 0xb7bdae, new int[]{18,23,26,25,20}, new int[]{21,19,21,25,26});
                polygon(brush, 0xdadbd0, new int[]{17,23,25,23,18}, new int[]{20,18,20,23,24});
                stroke(brush, 0xa8b09f, 1, new int[]{11,14,17}, new int[]{14,13,15});
            } else {
                stroke(brush, 0xb0b5a7, 1, new int[]{-3,3,6,7,11}, new int[]{21,20,18,15,12});
                stroke(brush, 0xc0c4b7, 1, new int[]{-3,3,5,6}, new int[]{22,21,19,16});
                polygon(brush, 0xb5b8ac, new int[]{24,28,31,30,25}, new int[]{3,2,4,8,7});
            }
            brush.dispose();
            for (int row = 0; row < 32; row++) for (int column = 0; column < 32; column++) {
                if ((image.getRGB(column, row) >>> 24) != 255) throw new IllegalStateException("Unexpected transparent material texel");
            }
            double variation = neighborVariation(image);
            if (variation > (relief ? 13 : 5)) throw new IllegalStateException("Sculpted material is excessively noisy: " + name);
            Path destination = output.resolve(name + ".png");
            if (!ImageIO.write(image, "png", destination.toFile())) throw new IllegalStateException("PNG writer unavailable");
            BufferedImage restored = ImageIO.read(destination.toFile());
            if (restored.getWidth() != 32 || restored.getHeight() != 32) throw new IllegalStateException("Sculpted image roundtrip failed");
            System.out.printf(java.util.Locale.ROOT, "%s: 32x32 opaque, neighbor luma difference %.3f%n", name, variation);
        }
        System.out.println("Sculpted v4: three local candidate textures; approved and runtime assets unchanged.");
    }

    private static void polygon(java.awt.Graphics2D brush, int color, int[] horizontal, int[] vertical) {
        brush.setColor(new java.awt.Color(color));
        for (int offsetX : new int[]{-32,0,32}) for (int offsetY : new int[]{-32,0,32}) {
            int[] shiftedX = horizontal.clone();
            int[] shiftedY = vertical.clone();
            for (int index = 0; index < shiftedX.length; index++) {
                shiftedX[index] += offsetX;
                shiftedY[index] += offsetY;
            }
            brush.fillPolygon(shiftedX, shiftedY, shiftedX.length);
        }
    }

    private static void stroke(java.awt.Graphics2D brush, int color, int width, int[] horizontal, int[] vertical) {
        brush.setColor(new java.awt.Color(color));
        brush.setStroke(new java.awt.BasicStroke(width, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_BEVEL));
        for (int offsetX : new int[]{-32,0,32}) for (int offsetY : new int[]{-32,0,32}) {
            int[] shiftedX = horizontal.clone();
            int[] shiftedY = vertical.clone();
            for (int index = 0; index < shiftedX.length; index++) {
                shiftedX[index] += offsetX;
                shiftedY[index] += offsetY;
            }
            brush.drawPolyline(shiftedX, shiftedY, shiftedX.length);
        }
    }

    private static void requireConnectedWear(String[] rows) {
        boolean[][] visited = new boolean[16][16];
        for (int row = 0; row < 16; row++) {
            if (rows[row].length() != 16) throw new IllegalArgumentException("Invalid floor row width");
            if (rows[row].charAt(0) != rows[row].charAt(15) || rows[0].charAt(row) != rows[15].charAt(row)) {
                throw new IllegalStateException("Floor edge mismatch");
            }
            for (int column = 0; column < 16; column++) {
                char shade = rows[row].charAt(column);
                if (shade == '2' || visited[row][column]) continue;
                var queue = new java.util.ArrayDeque<int[]>();
                queue.add(new int[]{column, row});
                visited[row][column] = true;
                boolean crossesEdge = false;
                while (!queue.isEmpty()) {
                    int[] point = queue.removeFirst();
                    crossesEdge |= point[0] == 0 || point[0] == 15 || point[1] == 0 || point[1] == 15;
                    for (int[] offset : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        int nextColumn = point[0] + offset[0];
                        int nextRow = point[1] + offset[1];
                        if (nextColumn < 0 || nextColumn >= 16 || nextRow < 0 || nextRow >= 16
                                || visited[nextRow][nextColumn] || rows[nextRow].charAt(nextColumn) != shade) continue;
                        visited[nextRow][nextColumn] = true;
                        queue.add(new int[]{nextColumn, nextRow});
                    }
                }
                if (!crossesEdge) throw new IllegalStateException("isolated floor patch");
            }
        }
    }

    private static void requireBrokenHighlights(String[] rows) {
        for (int column = 0; column < 16; column++) {
            boolean fullHeight = true;
            for (String row : rows) {
                if (row.length() != 16) throw new IllegalArgumentException("Invalid relief row width");
                fullHeight &= row.charAt(column) >= '3';
            }
            if (fullHeight) throw new IllegalStateException("unbroken relief highlight");
        }
    }

    private static void expectPatternFailure(Runnable validation, String message) {
        try {
            validation.run();
        } catch (IllegalStateException failure) {
            if (failure.getMessage().equals(message)) return;
            throw failure;
        }
        throw new IllegalStateException("Pattern negative control failed: " + message);
    }

    private static double neighborVariation(BufferedImage image) {
        double variation = 0;
        int count = 0;
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                int color = image.getRGB(column, row);
                for (int[] offset : new int[][]{{1, 0}, {0, 1}}) {
                    int neighbor = image.getRGB((column + offset[0]) % image.getWidth(), (row + offset[1]) % image.getHeight());
                    double luminance = ((color >> 16) & 255) * 0.2126 + ((color >> 8) & 255) * 0.7152 + (color & 255) * 0.0722;
                    double neighborLuminance = ((neighbor >> 16) & 255) * 0.2126 + ((neighbor >> 8) & 255) * 0.7152 + (neighbor & 255) * 0.0722;
                    variation += Math.abs(luminance - neighborLuminance);
                    count++;
                }
            }
        }
        return variation / count;
    }

    private static void write(String name, int[] palette, String[] rows) throws Exception {
        write(ASSETS, name, palette, rows);
    }

    private static void write(Path assets, String name, int[] palette, String[] rows) throws Exception {
        if (rows.length != 16) throw new IllegalArgumentException(name);
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < 16; row++) {
            if (rows[row].length() != 16) throw new IllegalArgumentException(name + ": row " + row);
            for (int column = 0; column < 16; column++) {
                int index = Character.digit(rows[row].charAt(column), 10);
                int color = palette[index];
                if (!(name.startsWith("item/") && index == 0)) color |= 0xff000000;
                image.setRGB(column, row, color);
            }
        }
        Path destination = assets.resolve(name + ".png");
        Files.createDirectories(destination.getParent());
        if (!ImageIO.write(image, "png", destination.toFile())) throw new IllegalStateException("PNG writer unavailable");
    }
}