import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Mechanical 16x export of the archived imagegen originals; no procedural artwork. */
public final class ExportTextures {
    public static void main(String[] args) throws Exception {
        Path source = Path.of("tools/malenia/arena/textures/source");
        Path output = Path.of("src/main/resources/assets/elder_bosses/textures/block");
        Files.createDirectories(output);
        String[] names = {"haligtree_root", "haligtree_silt", "haligtree_white_petals", "haligtree_altar"};
        BufferedImage sheet = new BufferedImage(1024, 292, BufferedImage.TYPE_INT_RGB);
        var g = sheet.createGraphics();
        g.setColor(new Color(45, 51, 46)); g.fillRect(0, 0, 1024, 292);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        for (int n = 0; n < names.length; n++) {
            BufferedImage original = ImageIO.read(source.resolve(names[n] + ".png").toFile());
            BufferedImage tile = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            // Area downsampling in premultiplied alpha keeps thin stems and glyph strokes.
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                long a = 0, r = 0, green = 0, b = 0, count = 0;
                for (int sy = y * original.getHeight() / 16; sy < (y + 1) * original.getHeight() / 16; sy++)
                    for (int sx = x * original.getWidth() / 16; sx < (x + 1) * original.getWidth() / 16; sx++) {
                        int p = original.getRGB(sx, sy), alpha = p >>> 24;
                        a += alpha; r += ((p >> 16) & 255) * alpha;
                        green += ((p >> 8) & 255) * alpha; b += (p & 255) * alpha; count++;
                    }
                // Runtime cutout pixels are strictly opaque or transparent, without fringes.
                int pixel = a < count * 80 ? 0 : 0xff000000 | (int)(r / a) << 16 | (int)(green / a) << 8 | (int)(b / a);
                tile.setRGB(x, y, pixel);
            }
            ImageIO.write(tile, "png", output.resolve(names[n] + ".png").toFile());
            g.drawImage(tile, n * 256, 0, 256, 256, null);
            g.setColor(Color.WHITE); g.drawString(names[n].replace("haligtree_", "") + " / 16 x 16", n * 256 + 8, 280);
            System.out.println(names[n] + ": " + original.getWidth() + "x" + original.getHeight() + " -> 16x16");
        }
        g.dispose();
        ImageIO.write(sheet, "png", source.getParent().resolve("texture-review.png").toFile());
    }
}
