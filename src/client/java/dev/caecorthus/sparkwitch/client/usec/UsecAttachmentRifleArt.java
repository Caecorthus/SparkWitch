package dev.caecorthus.sparkwitch.client.usec;

import java.util.ArrayList;
import java.util.List;

/**
 * Client only, pure: the attachment screen's bolt-action rifle line art, muzzle to the right, 189 x 54 GUI px (local x
 * 0..188, y -5..48), in four variants (muzzle brake or suppressor, magazine fitted or a dashed ghost). Each pixel has a
 * semantic {@link Ink}; {@code UsecAttachmentPaint} picks the colours, so a restyle never touches this data. The rows
 * are generated from the owner-approved U2 mockup script ({@code usec-art/ui/scripts/attachments.py}, class
 * {@code Rifle}) and run-length encoded as count + ink character ({@code .} = transparent).
 * 仅客户端，纯数据：配件界面的栓动步枪线稿，枪口朝右，189 × 54 GUI 像素（局部 x 0..188，y -5..48），共四种变体（制退器或
 * 消音器、已装弹匣或虚线弹匣）。每个像素带有语义 {@link Ink}，颜色由 {@code UsecAttachmentPaint} 决定，换皮从不触碰这些
 * 数据。各行由所有者批准的 U2 样稿脚本（{@code usec-art/ui/scripts/attachments.py} 的 {@code Rifle} 类）生成，按“数量 +
 * 墨水字符”行程编码（{@code .} 为透明）。
 */
public final class UsecAttachmentRifleArt {
    public static final int WIDTH = 189;
    public static final int HEIGHT = 54;
    /** Local y of the first row (the turret cap). 第一行的局部 y（转轮帽）。 */
    public static final int TOP = -5;

    /** Semantic pixel class. 像素语义类别。 */
    public enum Ink {
        BODY_FILL('b'),
        LINE('B'),
        DETAIL('d'),
        MAG_FILL('m'),
        MAG_LINE('M'),
        MUZZLE_FILL('z'),
        MUZZLE_LINE('Z'),
        MAG_GHOST('g');

        private final char code;

        Ink(char code) {
            this.code = code;
        }

        static Ink of(char code) {
            for (Ink ink : values()) {
                if (ink.code == code) {
                    return ink;
                }
            }
            throw new IllegalArgumentException("unknown ink " + code);
        }
    }

    /** One horizontal run at local (x, y). 局部坐标 (x, y) 处的一段水平像素。 */
    public record Run(int x, int y, int length, Ink ink) {
    }

    private UsecAttachmentRifleArt() {
    }

    /** Runs of one variant, top to bottom. 某一变体的像素段，自上而下。 */
    public static List<Run> runs(boolean suppressed, boolean magazine) {
        return Decoded.RUNS[variant(suppressed, magazine)];
    }

    /** Ink at local (x, y), or null when transparent or outside. 局部 (x, y) 处的墨水；透明或越界时为 null。 */
    public static Ink inkAt(boolean suppressed, boolean magazine, int x, int y) {
        int row = y - TOP;
        if (x < 0 || x >= WIDTH || row < 0 || row >= HEIGHT) {
            return null;
        }
        return Decoded.GRIDS[variant(suppressed, magazine)][row][x];
    }

    private static int variant(boolean suppressed, boolean magazine) {
        return (suppressed ? 2 : 0) + (magazine ? 0 : 1);
    }

    /** Decoded on first use, after the data arrays below are initialised. 首次使用时解码，此时下方数据数组已初始化。 */
    private static final class Decoded {
        private static final String[][] VARIANTS = {BRAKE_MAG, BRAKE_NO_MAG, SUPPRESSED_MAG, SUPPRESSED_NO_MAG};
        @SuppressWarnings("unchecked")
        private static final List<Run>[] RUNS = new List[VARIANTS.length];
        private static final Ink[][][] GRIDS = new Ink[VARIANTS.length][][];

        static {
            for (int index = 0; index < VARIANTS.length; index++) {
                RUNS[index] = decode(VARIANTS[index]);
                Ink[][] grid = new Ink[HEIGHT][WIDTH];
                for (Run run : RUNS[index]) {
                    for (int x = run.x(); x < run.x() + run.length(); x++) {
                        grid[run.y() - TOP][x] = run.ink();
                    }
                }
                GRIDS[index] = grid;
            }
        }
    }

    static List<Run> decode(String[] rows) {
        if (rows.length != HEIGHT) {
            throw new IllegalStateException("rifle art must have " + HEIGHT + " rows");
        }
        List<Run> runs = new ArrayList<>();
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int x = 0;
            int count = 0;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (Character.isDigit(c)) {
                    count = count * 10 + (c - '0');
                    continue;
                }
                if (c != '.') {
                    runs.add(new Run(x, TOP + row, count, Ink.of(c)));
                }
                x += count;
                count = 0;
            }
            if (x != WIDTH) {
                throw new IllegalStateException("rifle art row " + row + " is " + x + " px wide");
            }
        }
        return List.copyOf(runs);
    }

    // ---- generated data (do not edit by hand) / 生成的数据（请勿手改） ----
    static final String[] BRAKE_MAG = {
            "85.10B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.2B6b2B94.",
            "87.1B4b1B26.11B59.",
            "57.10B20.1B4b1B25.1B1d7b1d1b1B59.",
            "56.1B1b1d8b5B15.1B4b1B23.2B1b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1B4.5B6.1B4b1B11.5B6.1B3b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1b4B5b6B6b11B5b6B4b1d7b1d1b1B59.",
            "56.1B1b1d11b1d18b3d27b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1b4B5b8B3d12B5b6B4b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1B4.1B3b1B23.1B3b1B6.1B3b1d7b1d1b1B59.",
            "5.1B50.1B1b1d8b5B4.1B1b1d1b1B23.1B1b1d1b1B7.2B1b1d7b1d1b1B59.",
            "2.3B1b1B6.22B22.10B9.1B1b1d1b1B23.1B1b1d1b1B9.1B1d7b1d1b1B59.",
            "1.1B1d3b1B5.1B22b1B40.1B1b1d1b1B23.1B1b1d1b1B10.11B59.",
            "1B1b1d3b1B5.1B22b1B40.1B1b1B1b1B23.2B2b1B80.",
            "1B1b1d3b1B5.5B2b10B2b5B22.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B60.",
            "1B1b1d3b1B10.2B10.2B27.1B1b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B1b1B60.",
            "1B1b1d3b1B10.2B10.2B11.16B70b1B60.",
            "1B1b1d3b1B10.2B10.2B11.1B14b1d41b1d28b1B60.",
            "1B1b1d4b10B2b10B2b11B15b1d16b12d13b1d28b1B51.8Z1.",
            "1B1b1d42b2d10b1d16b1d10b1d13b1d28b1B51.1Z7z1Z",
            "1B1b1d54b1d16b1d10b1d13b1d29b51B1Z1z1d1z1d1z1d1z1Z",
            "1B1b1d4b35B15b1d16b12d13b1d80b1Z1z1d1z1d1z1d1z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d3b4d2b4d2b4d2b4d55b1Z1z1d1z1d1z1d1z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d29b51B1Z1z1d1z1d1z1d1z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d28b1B51.1Z7z1Z",
            "1B1b1d3b1B35.1B2b2d10b1d41b1d28b1B51.8Z1.",
            "1B1b1d3b1B31.4B15b1d41b1d28b1B60.",
            "1B1b1d3b1B27.4B11b3B2b8B12b4B1b7B1b1B11M1d4M23b1B61.",
            "1B1b1d3b1B23.4B5b10B3.2B8.1B10b1B4.1B7.1B1.2M13m1M2B3b12B5b1B62.",
            "1B1b1d3b1B19.4B5b4B14.1B8.1B9b2B4.1B7.1B3.1M1m1d9m1M3.1B1b1B12.1B5b1B61.",
            "1B1b1d3b1B16.3B5b4B16.2B1b2B5.1B9b1B1.1B5.1B6.1B3.1M1m1d9m1M3.1B2b12B6b1B61.",
            "1B1b1d3b1B12.4B4b4B19.1B5b1B4.1B9b1B1.1B5.1B6.1B3.1M11m1M3.1B2b12d6b1B61.",
            "1B1b1d3b1B8.4B5b3B23.1B5b1B4.1B9b1B1.1B5.1B6.1B3.1M11m1M3.1B2b18B62.",
            "1B1b1d3b1B4.4B5b4B26.1B5b1B4.1B1b5d3b1B1.1B12.1B3.1M11d1M3.3B80.",
            "1B1b1d4b4B5b4B30.1B5b1B3.1B10b1B1.1B12.1B3.1M11m1M86.",
            "1B1b1d9b4B35.5B4.1B10b1B1.13B4.1M11m1M86.",
            "1B1b1d5b4B48.1B9b1B19.1M11m1M86.",
            "1B1b1d4b1B52.1B1b5d3b1B19.1M11m1M86.",
            "1B1b1d3b1B52.1B10b1B19.1M11d1M86.",
            "1.1B1d3b1B52.1B10b1B19.1M11m1M86.",
            "2.3B1b1B52.1B10b1B19.1M11m1M86.",
            "5.1B53.1B1b5d4b1B19.1M11m1M86.",
            "58.1B10b1B20.1M11m1M86.",
            "58.1B10b1B20.1M11m1M86.",
            "58.1B10b1B20.1M11m1M86.",
            "59.1B8b2B19.1M13m1M85.",
            "60.8B21.1M13m1M85.",
            "89.15M85."
    };
    static final String[] BRAKE_NO_MAG = {
            "85.10B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.2B6b2B94.",
            "87.1B4b1B26.11B59.",
            "57.10B20.1B4b1B25.1B1d7b1d1b1B59.",
            "56.1B1b1d8b5B15.1B4b1B23.2B1b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1B4.5B6.1B4b1B11.5B6.1B3b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1b4B5b6B6b11B5b6B4b1d7b1d1b1B59.",
            "56.1B1b1d11b1d18b3d27b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1b4B5b8B3d12B5b6B4b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1B4.1B3b1B23.1B3b1B6.1B3b1d7b1d1b1B59.",
            "5.1B50.1B1b1d8b5B4.1B1b1d1b1B23.1B1b1d1b1B7.2B1b1d7b1d1b1B59.",
            "2.3B1b1B6.22B22.10B9.1B1b1d1b1B23.1B1b1d1b1B9.1B1d7b1d1b1B59.",
            "1.1B1d3b1B5.1B22b1B40.1B1b1d1b1B23.1B1b1d1b1B10.11B59.",
            "1B1b1d3b1B5.1B22b1B40.1B1b1B1b1B23.2B2b1B80.",
            "1B1b1d3b1B5.5B2b10B2b5B22.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B60.",
            "1B1b1d3b1B10.2B10.2B27.1B1b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B1b1B60.",
            "1B1b1d3b1B10.2B10.2B11.16B70b1B60.",
            "1B1b1d3b1B10.2B10.2B11.1B14b1d41b1d28b1B60.",
            "1B1b1d4b10B2b10B2b11B15b1d16b12d13b1d28b1B51.8Z1.",
            "1B1b1d42b2d10b1d16b1d10b1d13b1d28b1B51.1Z7z1Z",
            "1B1b1d54b1d16b1d10b1d13b1d29b51B1Z1z1d1z1d1z1d1z1Z",
            "1B1b1d4b35B15b1d16b12d13b1d80b1Z1z1d1z1d1z1d1z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d3b4d2b4d2b4d2b4d55b1Z1z1d1z1d1z1d1z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d29b51B1Z1z1d1z1d1z1d1z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d28b1B51.1Z7z1Z",
            "1B1b1d3b1B35.1B2b2d10b1d41b1d28b1B51.8Z1.",
            "1B1b1d3b1B31.4B15b1d41b1d28b1B60.",
            "1B1b1d3b1B27.4B11b3B2b8B12b4B1b7B1b2B2g1B2g1B2g1B1g1d1b2g24b1B61.",
            "1B1b1d3b1B23.4B5b10B3.2B8.1B10b1B4.1B7.1B1.2g9.4B1g2B3b12B5b1B62.",
            "1B1b1d3b1B19.4B5b4B14.1B8.1B9b2B4.1B7.1B3.1g11.1g3.1B1b1B12.1B5b1B61.",
            "1B1b1d3b1B16.3B5b4B16.2B1b2B5.1B9b1B1.1B5.1B6.1B3.1g11.1g3.1B2b12B6b1B61.",
            "1B1b1d3b1B12.4B4b4B19.1B5b1B4.1B9b1B1.1B5.1B6.1B19.1B2b12d6b1B61.",
            "1B1b1d3b1B8.4B5b3B23.1B5b1B4.1B9b1B1.1B5.1B6.1B3.1g11.1g3.1B2b18B62.",
            "1B1b1d3b1B4.4B5b4B26.1B5b1B4.1B1b5d3b1B1.1B12.1B3.1g11.1g3.3B80.",
            "1B1b1d4b4B5b4B30.1B5b1B3.1B10b1B1.1B12.1B102.",
            "1B1b1d9b4B35.5B4.1B10b1B1.13B4.1g11.1g86.",
            "1B1b1d5b4B48.1B9b1B19.1g11.1g86.",
            "1B1b1d4b1B52.1B1b5d3b1B118.",
            "1B1b1d3b1B52.1B10b1B19.1g11.1g86.",
            "1.1B1d3b1B52.1B10b1B19.1g11.1g86.",
            "2.3B1b1B52.1B10b1B118.",
            "5.1B53.1B1b5d4b1B19.1g11.1g86.",
            "58.1B10b1B20.1g11.1g86.",
            "58.1B10b1B119.",
            "58.1B10b1B20.1g11.1g86.",
            "59.1B8b2B19.1g99.",
            "60.8B21.1g13.1g85.",
            "90.2g1.2g1.2g1.2g1.2g85."
    };
    static final String[] SUPPRESSED_MAG = {
            "85.10B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.2B6b2B94.",
            "87.1B4b1B26.11B59.",
            "57.10B20.1B4b1B25.1B1d7b1d1b1B59.",
            "56.1B1b1d8b5B15.1B4b1B23.2B1b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1B4.5B6.1B4b1B11.5B6.1B3b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1b4B5b6B6b11B5b6B4b1d7b1d1b1B59.",
            "56.1B1b1d11b1d18b3d27b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1b4B5b8B3d12B5b6B4b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1B4.1B3b1B23.1B3b1B6.1B3b1d7b1d1b1B59.",
            "5.1B50.1B1b1d8b5B4.1B1b1d1b1B23.1B1b1d1b1B7.2B1b1d7b1d1b1B59.",
            "2.3B1b1B6.22B22.10B9.1B1b1d1b1B23.1B1b1d1b1B9.1B1d7b1d1b1B59.",
            "1.1B1d3b1B5.1B22b1B40.1B1b1d1b1B23.1B1b1d1b1B10.11B59.",
            "1B1b1d3b1B5.1B22b1B40.1B1b1B1b1B23.2B2b1B80.",
            "1B1b1d3b1B5.5B2b10B2b5B22.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B60.",
            "1B1b1d3b1B10.2B10.2B27.1B1b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B1b1B60.",
            "1B1b1d3b1B10.2B10.2B11.16B70b1B60.",
            "1B1b1d3b1B10.2B10.2B11.1B14b1d41b1d28b1B38.21Z1.",
            "1B1b1d4b10B2b10B2b11B15b1d16b12d13b1d28b1B37.1Z2z1d15z1d2z1Z",
            "1B1b1d42b2d10b1d16b1d10b1d13b1d28b1B37.1Z2z1d15z1d2z1Z",
            "1B1b1d54b1d16b1d10b1d13b1d29b37B1Z2z1d15z1d2z1Z",
            "1B1b1d4b35B15b1d16b12d13b1d66b1Z2z1d15z1d2z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d3b4d2b4d2b4d2b4d41b1Z2z1d15z1d2z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d29b37B1Z2z1d15z1d2z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d28b1B37.1Z2z1d15z1d2z1Z",
            "1B1b1d3b1B35.1B2b2d10b1d41b1d28b1B37.1Z2z1d15z1d2z1Z",
            "1B1b1d3b1B31.4B15b1d41b1d28b1B38.21Z1.",
            "1B1b1d3b1B27.4B11b3B2b8B12b4B1b7B1b1B11M1d4M23b1B61.",
            "1B1b1d3b1B23.4B5b10B3.2B8.1B10b1B4.1B7.1B1.2M13m1M2B3b12B5b1B62.",
            "1B1b1d3b1B19.4B5b4B14.1B8.1B9b2B4.1B7.1B3.1M1m1d9m1M3.1B1b1B12.1B5b1B61.",
            "1B1b1d3b1B16.3B5b4B16.2B1b2B5.1B9b1B1.1B5.1B6.1B3.1M1m1d9m1M3.1B2b12B6b1B61.",
            "1B1b1d3b1B12.4B4b4B19.1B5b1B4.1B9b1B1.1B5.1B6.1B3.1M11m1M3.1B2b12d6b1B61.",
            "1B1b1d3b1B8.4B5b3B23.1B5b1B4.1B9b1B1.1B5.1B6.1B3.1M11m1M3.1B2b18B62.",
            "1B1b1d3b1B4.4B5b4B26.1B5b1B4.1B1b5d3b1B1.1B12.1B3.1M11d1M3.3B80.",
            "1B1b1d4b4B5b4B30.1B5b1B3.1B10b1B1.1B12.1B3.1M11m1M86.",
            "1B1b1d9b4B35.5B4.1B10b1B1.13B4.1M11m1M86.",
            "1B1b1d5b4B48.1B9b1B19.1M11m1M86.",
            "1B1b1d4b1B52.1B1b5d3b1B19.1M11m1M86.",
            "1B1b1d3b1B52.1B10b1B19.1M11d1M86.",
            "1.1B1d3b1B52.1B10b1B19.1M11m1M86.",
            "2.3B1b1B52.1B10b1B19.1M11m1M86.",
            "5.1B53.1B1b5d4b1B19.1M11m1M86.",
            "58.1B10b1B20.1M11m1M86.",
            "58.1B10b1B20.1M11m1M86.",
            "58.1B10b1B20.1M11m1M86.",
            "59.1B8b2B19.1M13m1M85.",
            "60.8B21.1M13m1M85.",
            "89.15M85."
    };
    static final String[] SUPPRESSED_NO_MAG = {
            "85.10B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.1B1d1b1d1b1d1b1d1b1B94.",
            "85.2B6b2B94.",
            "87.1B4b1B26.11B59.",
            "57.10B20.1B4b1B25.1B1d7b1d1b1B59.",
            "56.1B1b1d8b5B15.1B4b1B23.2B1b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1B4.5B6.1B4b1B11.5B6.1B3b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1b4B5b6B6b11B5b6B4b1d7b1d1b1B59.",
            "56.1B1b1d11b1d18b3d27b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d17b1d3b1d26b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1b4B5b8B3d12B5b6B4b1d7b1d1b1B59.",
            "56.1B1b1d11b1d1B4.1B3b1B23.1B3b1B6.1B3b1d7b1d1b1B59.",
            "5.1B50.1B1b1d8b5B4.1B1b1d1b1B23.1B1b1d1b1B7.2B1b1d7b1d1b1B59.",
            "2.3B1b1B6.22B22.10B9.1B1b1d1b1B23.1B1b1d1b1B9.1B1d7b1d1b1B59.",
            "1.1B1d3b1B5.1B22b1B40.1B1b1d1b1B23.1B1b1d1b1B10.11B59.",
            "1B1b1d3b1B5.1B22b1B40.1B1b1B1b1B23.2B2b1B80.",
            "1B1b1d3b1B5.5B2b10B2b5B22.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B1.2B60.",
            "1B1b1d3b1B10.2B10.2B27.1B1b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B2b1B1b1B60.",
            "1B1b1d3b1B10.2B10.2B11.16B70b1B60.",
            "1B1b1d3b1B10.2B10.2B11.1B14b1d41b1d28b1B38.21Z1.",
            "1B1b1d4b10B2b10B2b11B15b1d16b12d13b1d28b1B37.1Z2z1d15z1d2z1Z",
            "1B1b1d42b2d10b1d16b1d10b1d13b1d28b1B37.1Z2z1d15z1d2z1Z",
            "1B1b1d54b1d16b1d10b1d13b1d29b37B1Z2z1d15z1d2z1Z",
            "1B1b1d4b35B15b1d16b12d13b1d66b1Z2z1d15z1d2z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d3b4d2b4d2b4d2b4d41b1Z2z1d15z1d2z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d29b37B1Z2z1d15z1d2z1Z",
            "1B1b1d3b1B35.1B14b1d41b1d28b1B37.1Z2z1d15z1d2z1Z",
            "1B1b1d3b1B35.1B2b2d10b1d41b1d28b1B37.1Z2z1d15z1d2z1Z",
            "1B1b1d3b1B31.4B15b1d41b1d28b1B38.21Z1.",
            "1B1b1d3b1B27.4B11b3B2b8B12b4B1b7B1b2B2g1B2g1B2g1B1g1d1b2g24b1B61.",
            "1B1b1d3b1B23.4B5b10B3.2B8.1B10b1B4.1B7.1B1.2g9.4B1g2B3b12B5b1B62.",
            "1B1b1d3b1B19.4B5b4B14.1B8.1B9b2B4.1B7.1B3.1g11.1g3.1B1b1B12.1B5b1B61.",
            "1B1b1d3b1B16.3B5b4B16.2B1b2B5.1B9b1B1.1B5.1B6.1B3.1g11.1g3.1B2b12B6b1B61.",
            "1B1b1d3b1B12.4B4b4B19.1B5b1B4.1B9b1B1.1B5.1B6.1B19.1B2b12d6b1B61.",
            "1B1b1d3b1B8.4B5b3B23.1B5b1B4.1B9b1B1.1B5.1B6.1B3.1g11.1g3.1B2b18B62.",
            "1B1b1d3b1B4.4B5b4B26.1B5b1B4.1B1b5d3b1B1.1B12.1B3.1g11.1g3.3B80.",
            "1B1b1d4b4B5b4B30.1B5b1B3.1B10b1B1.1B12.1B102.",
            "1B1b1d9b4B35.5B4.1B10b1B1.13B4.1g11.1g86.",
            "1B1b1d5b4B48.1B9b1B19.1g11.1g86.",
            "1B1b1d4b1B52.1B1b5d3b1B118.",
            "1B1b1d3b1B52.1B10b1B19.1g11.1g86.",
            "1.1B1d3b1B52.1B10b1B19.1g11.1g86.",
            "2.3B1b1B52.1B10b1B118.",
            "5.1B53.1B1b5d4b1B19.1g11.1g86.",
            "58.1B10b1B20.1g11.1g86.",
            "58.1B10b1B119.",
            "58.1B10b1B20.1g11.1g86.",
            "59.1B8b2B19.1g99.",
            "60.8B21.1g13.1g85.",
            "90.2g1.2g1.2g1.2g1.2g85."
    };
}
