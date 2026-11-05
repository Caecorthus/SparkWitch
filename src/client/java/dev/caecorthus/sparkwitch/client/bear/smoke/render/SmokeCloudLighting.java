package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.mixin.bear.SmokeLightmapImageAccessor;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudSnapshot;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * World light at each smoke cloud: a few probe cells per cloud read their block and sky light once per client tick,
 * and every frame those levels are looked up in the lightmap's CPU image (the colour the terrain there is drawn with)
 * and averaged, times the smoke albedo; the share of probes under the open sky gates the direct sun/moon light.
 * Render thread only.
 * 各烟团处的世界光照：每团烟取几个探针格，每个客户端 tick 读取一次其方块光与天空光；每帧再到光照图的 CPU 图像中查出
 * 这些光照等级对应的颜色（该处地形的绘制颜色）并取平均，再乘以烟雾反照率；见天探针所占比例决定日月直射光的强弱。
 * 仅限渲染线程。
 */
final class SmokeCloudLighting {
    /** Light probes per cloud. / 每团烟的光照探针数。 */
    static final int PROBES = 9;
    private static final Map<Integer, Probes> BY_CLOUD = new HashMap<>();

    private static final class Probes {
        final SmokeCloudSnapshot cloud;
        final BlockPos[] positions;
        final int[] block;
        final int[] sky;
        long sampledTime = Long.MIN_VALUE;

        Probes(SmokeCloudSnapshot cloud) {
            this.cloud = cloud;
            this.positions = probePositions(cloud);
            this.block = new int[positions.length];
            this.sky = new int[positions.length];
        }
    }

    private SmokeCloudLighting() {
    }

    /**
     * Writes albedo times the mean lightmap colour at cloud {@code cloudId} into {@code out[offset..offset + 2]} and
     * its sky exposure (0 indoors .. 1 under the open sky) into {@code out[offset + 3]} (white and 1 when the cloud or
     * the lightmap is unknown).
     * 把反照率乘以烟团 {@code cloudId} 处光照图颜色的平均值写入 {@code out[offset..offset + 2]}，见天程度（室内 0 ..
     * 露天 1）写入 {@code out[offset + 3]}（烟团或光照图未知时为白色与 1）。
     */
    static void write(ClientWorld world, LightmapTextureManager lightmap, int cloudId, float[] out, int offset) {
        Probes probes = probesFor(cloudId);
        NativeImage image = lightmap == null ? null : ((SmokeLightmapImageAccessor) lightmap).sparkwitch$smokeLightmapImage();
        float r = 1.0F;
        float g = 1.0F;
        float b = 1.0F;
        float sky = 1.0F;
        if (probes != null && image != null && probes.positions.length > 0) {
            sample(world, probes);
            r = 0.0F;
            g = 0.0F;
            b = 0.0F;
            sky = 0.0F;
            for (int i = 0; i < probes.positions.length; i++) {
                // Lightmap x = block light, y = sky light; colours are ABGR. / 光照图 x = 方块光，y = 天空光；颜色为 ABGR。
                int color = image.getColor(probes.block[i], probes.sky[i]);
                r += (color & 0xFF) / 255.0F;
                g += ((color >> 8) & 0xFF) / 255.0F;
                b += ((color >> 16) & 0xFF) / 255.0F;
                sky += skyExposure(probes.sky[i]);
            }
            float inv = 1.0F / probes.positions.length;
            r *= inv;
            g *= inv;
            b *= inv;
            sky *= inv;
        }
        out[offset] = r * SmokeMarchRules.ALBEDO_R;
        out[offset + 1] = g * SmokeMarchRules.ALBEDO_G;
        out[offset + 2] = b * SmokeMarchRules.ALBEDO_B;
        out[offset + 3] = sky;
    }

    /**
     * Sky exposure of one probe from its sky light: 1 under the open sky (15), 0 from 11 down (a few blocks inside).
     * 单个探针按天空光得出的见天程度：露天（15）为 1，11 及以下（深入室内几格）为 0。
     */
    static float skyExposure(int skyLight) {
        float t = Math.max(0.0F, Math.min(1.0F, (skyLight - 11) / 4.0F));
        return t * t * (3.0F - 2.0F * t);
    }

    static void clear() {
        BY_CLOUD.clear();
    }

    private static Probes probesFor(int cloudId) {
        SmokeCloudSnapshot current = null;
        for (SmokeCloudSnapshot cloud : SmokeClientState.clouds()) {
            if (cloud.cloudId() == cloudId) {
                current = cloud;
                break;
            }
        }
        if (current == null) {
            BY_CLOUD.remove(cloudId);
            return null;
        }
        Probes probes = BY_CLOUD.get(cloudId);
        if (probes == null || probes.cloud != current) {
            if (BY_CLOUD.size() > 4 * SmokeMarchRules.MAX_CLOUDS) {
                BY_CLOUD.clear();
            }
            probes = new Probes(current);
            BY_CLOUD.put(cloudId, probes);
        }
        return probes;
    }

    private static void sample(ClientWorld world, Probes probes) {
        long now = world.getTime();
        if (probes.sampledTime == now) {
            return;
        }
        probes.sampledTime = now;
        for (int i = 0; i < probes.positions.length; i++) {
            int packed = WorldRenderer.getLightmapCoordinates(world, probes.positions[i]);
            probes.block[i] = LightmapTextureManager.getBlockLightCoordinates(packed);
            probes.sky[i] = LightmapTextureManager.getSkyLightCoordinates(packed);
        }
    }

    /** Up to {@link #PROBES} present cells spread through the cloud. / 均匀分布于烟团中的至多 {@link #PROBES} 个有烟格。 */
    private static BlockPos[] probePositions(SmokeCloudSnapshot cloud) {
        int present = cloud.presentCount();
        if (present == 0) {
            return new BlockPos[0];
        }
        int stride = Math.max(1, present / PROBES);
        List<BlockPos> positions = new ArrayList<>(PROBES);
        int seen = 0;
        int next = stride / 2;
        double cell = SmokeCloudSnapshot.CELL_SIZE;
        for (int k = 0; k < cloud.sizeZ() && positions.size() < PROBES; k++) {
            for (int j = 0; j < cloud.sizeY() && positions.size() < PROBES; j++) {
                for (int i = 0; i < cloud.sizeX() && positions.size() < PROBES; i++) {
                    if (!cloud.present(cloud.index(i, j, k))) {
                        continue;
                    }
                    if (seen++ == next) {
                        BlockPos pos = BlockPos.ofFloored(cloud.minX() + (i + 0.5) * cell,
                                cloud.minY() + (j + 0.5) * cell, cloud.minZ() + (k + 0.5) * cell);
                        if (!positions.contains(pos)) {
                            positions.add(pos);
                        }
                        next += stride;
                    }
                }
            }
        }
        return positions.toArray(new BlockPos[0]);
    }
}
