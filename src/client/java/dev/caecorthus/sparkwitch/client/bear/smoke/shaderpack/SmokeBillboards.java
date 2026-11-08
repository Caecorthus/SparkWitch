package dev.caecorthus.sparkwitch.client.bear.smoke.shaderpack;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudSnapshot;
import it.unimi.dsi.fastutil.ints.IntArrays;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Fail-closed BEAR smoke under an Iris shader pack when the after-final-pass smoke is not proven to work
 * ({@link SmokeShaderPackBridge#primaryHealthy()} false): soft, camera-facing puffs, one per block of
 * {@link SmokeVolumes} smoke, emitted at {@code WorldRenderEvents.AFTER_ENTITIES} into the world's entity buffers through
 * the vanilla {@code entity_translucent} layer, which Iris draws with the pack's own entity program. The pack therefore
 * lights, fogs and depth-tests the puffs itself; nothing here depends on Iris internals. Puffs overlap, are sorted far
 * to near, and their alpha follows the density, so about one block of full smoke is opaque (a little denser than the
 * ray march, to stay closed). With the camera inside smoke, puffs around the eye would be cut by the near plane and
 * leave holes, so they are left out and a camera-facing veil just in front of the eye stands for the local density; it
 * lies beyond Iris's compressed hand depth, so the first-person hand stays in front of it. Positions, light and alpha
 * are rebuilt once per world tick; only orientation and order change per frame. Render thread only; client-only
 * presentation.
 * Iris 光影包下最终处理之后的烟雾未被证明可用时（{@link SmokeShaderPackBridge#primaryHealthy()} 为 false）失败即遮挡的 BEAR 烟雾：
 * {@link SmokeVolumes} 中每格烟放一个柔和、朝向相机的烟团，在 {@code WorldRenderEvents.AFTER_ENTITIES} 通过原版
 * {@code entity_translucent} 图层写入世界实体缓冲，由 Iris 用光影包自己的实体程序绘制。因此烟团的光照、雾与深度测试都由光影包
 * 完成，这里不依赖 Iris 内部实现。烟团彼此重叠、由远到近排序，alpha 随浓度变化，约一格满浓度烟即不透明（比光线步进略浓，以保持
 * 遮挡）。相机在烟内时，眼睛周围的烟团会被近平面裁掉而留下空洞，因此不画它们，改在眼前放一层朝向相机的薄幕代表所在处的浓度；
 * 薄幕位于 Iris 压缩后的手部深度之外，第一人称手部仍在其前方。位置、光照与 alpha 每个世界 tick 重建一次；每帧只更新朝向与顺序。
 * 仅限渲染线程；纯客户端展示。
 */
final class SmokeBillboards {
    /** Most puffs per frame; denser clouds are thinned with larger puffs. / 每帧烟团上限；更多时以更大的烟团稀疏放置。 */
    static final int MAX_PUFFS = 3000;
    /**
     * Texels per puff along each axis: one puff per block keeps the overdraw (each puff runs the pack's entity
     * program) affordable. / 每个烟团沿各轴覆盖的纹素数：每格一个烟团，使过度绘制（每个烟团都运行光影包的实体程序）可以承受。
     */
    static final int GROUP = 2;
    /** Puff half-size in blocks for one group. / 单个纹素组烟团的半边长（格）。 */
    static final float HALF_SIZE = 0.9F;
    /** Centre alpha per unit density: about one block of full smoke is opaque. / 每单位浓度的中心 alpha：约一格满浓度烟即不透明。 */
    static final float ALPHA_PER_DENSITY = 2.0F;
    private static final float MIN_DENSITY = 0.03F;
    /**
     * Veil distance in front of the eye: beyond Iris's hand depth (which reads as at most 0.114 blocks), so the hand
     * stays in front. Half-size covers about 150 degrees. / 薄幕距眼睛的距离：在 Iris 手部深度（至多相当于 0.114 格）之外，
     * 手部仍在前方。半边长覆盖约 150 度。
     */
    private static final float VEIL_DISTANCE = 0.13F;
    private static final float VEIL_HALF_SIZE = 0.5F;
    /** Puffs this close to the eye are replaced by the veil. / 距眼睛这么近的烟团由薄幕代替。 */
    private static final float VEIL_RADIUS_SQUARED = 0.6F * 0.6F;
    /** Veil alpha per unit density at the eye: opaque from about 0.4 density. / 眼睛处每单位浓度的薄幕 alpha。 */
    private static final float VEIL_ALPHA_PER_DENSITY = 2.5F;
    private static final int GREY = 0xD2D2D2;
    private static final int TEXTURE_SIZE = 32;
    private static final Identifier TEXTURE = SparkWitch.id("dynamic/bear_smoke_puff");

    private static float[] puffs = new float[0];
    private static int[] lights = new int[0];
    private static int[] order = new int[0];
    private static float[] distances = new float[0];
    private static int count;
    private static long builtTime = Long.MIN_VALUE;
    private static boolean textureReady;
    private static final Vector3f CORNER = new Vector3f();

    private SmokeBillboards() {
    }

    /** Emits the puffs for one world render. / 为一次世界渲染输出烟团。 */
    static void render(WorldRenderContext context) {
        ClientWorld world = context.world();
        VertexConsumerProvider consumers = context.consumers();
        if (world == null || consumers == null) {
            return;
        }
        List<SmokeVolumes.Volume> volumes = SmokeVolumes.prepare(world);
        if (volumes.isEmpty()) {
            count = 0;
            return;
        }
        if (builtTime != world.getTime()) {
            build(world, volumes);
        }
        if (count == 0) {
            return;
        }
        ensureTexture();
        Camera camera = context.camera();
        Vec3d eye = camera.getPos();
        sort(eye);
        Quaternionf rotation = camera.getRotation();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE, false));
        float eyeDensity = eyeDensity(eye, volumes);
        boolean veil = eyeDensity >= MIN_DENSITY;
        for (int n = 0; n < count; n++) {
            int i = order[n];
            if (veil && distances[i] < VEIL_RADIUS_SQUARED) {
                continue;
            }
            int base = i * 6;
            float x = (float) (puffs[base] - eye.x);
            float y = (float) (puffs[base + 1] - eye.y);
            float z = (float) (puffs[base + 2] - eye.z);
            float size = puffs[base + 3];
            float alpha = puffs[base + 4];
            float roll = puffs[base + 5];
            float length = MathHelper.sqrt(x * x + y * y + z * z);
            float nx = length > 1.0E-4F ? -x / length : 0.0F;
            float ny = length > 1.0E-4F ? -y / length : 1.0F;
            float nz = length > 1.0E-4F ? -z / length : 0.0F;
            int color = ((int) (alpha * 255.0F) << 24) | GREY;
            int light = lights[i];
            float cos = MathHelper.cos(roll) * size;
            float sin = MathHelper.sin(roll) * size;
            corner(buffer, rotation, x, y, z, -cos + sin, -sin - cos, color, 0.0F, 1.0F, light, nx, ny, nz);
            corner(buffer, rotation, x, y, z, -cos - sin, -sin + cos, color, 0.0F, 0.0F, light, nx, ny, nz);
            corner(buffer, rotation, x, y, z, cos - sin, sin + cos, color, 1.0F, 0.0F, light, nx, ny, nz);
            corner(buffer, rotation, x, y, z, cos + sin, sin - cos, color, 1.0F, 1.0F, light, nx, ny, nz);
        }
        if (veil) {
            veil(world, buffer, camera, eyeDensity);
        }
    }

    /** Density of the texel holding the eye (0 outside smoke). / 眼睛所在纹素的浓度（烟外为 0）。 */
    private static float eyeDensity(Vec3d eye, List<SmokeVolumes.Volume> volumes) {
        float density = 0.0F;
        double cell = SmokeCloudSnapshot.CELL_SIZE;
        for (SmokeVolumes.Volume v : volumes) {
            density = Math.max(density, SmokeVolumes.texelDensity(v, (int) Math.floor((eye.x - v.minX()) / cell),
                    (int) Math.floor((eye.y - v.minY()) / cell), (int) Math.floor((eye.z - v.minZ()) / cell)));
        }
        return density;
    }

    /** Camera inside smoke: one flat quad over the whole view. / 相机在烟内：覆盖整个视野的一块平幕。 */
    private static void veil(ClientWorld world, VertexConsumer buffer, Camera camera, float density) {
        Vec3d eye = camera.getPos();
        Vector3f forward = camera.getHorizontalPlane();
        float x = forward.x * VEIL_DISTANCE;
        float y = forward.y * VEIL_DISTANCE;
        float z = forward.z * VEIL_DISTANCE;
        int color = ((int) (Math.min(1.0F, VEIL_ALPHA_PER_DENSITY * density) * 255.0F) << 24) | GREY;
        int light = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(eye));
        Quaternionf rotation = camera.getRotation();
        float h = VEIL_HALF_SIZE;
        // The texture centre (u, v = 0.5) is fully opaque, so the veil is flat. / 纹理中心完全不透明，因此薄幕是平的。
        corner(buffer, rotation, x, y, z, -h, -h, color, 0.5F, 0.5F, light, -forward.x, -forward.y, -forward.z);
        corner(buffer, rotation, x, y, z, -h, h, color, 0.5F, 0.5F, light, -forward.x, -forward.y, -forward.z);
        corner(buffer, rotation, x, y, z, h, h, color, 0.5F, 0.5F, light, -forward.x, -forward.y, -forward.z);
        corner(buffer, rotation, x, y, z, h, -h, color, 0.5F, 0.5F, light, -forward.x, -forward.y, -forward.z);
    }

    private static void corner(VertexConsumer buffer, Quaternionf rotation, float x, float y, float z, float u, float v,
                               int color, float texU, float texV, int light, float nx, float ny, float nz) {
        CORNER.set(u, v, 0.0F).rotate(rotation);
        buffer.vertex(x + CORNER.x, y + CORNER.y, z + CORNER.z, color, texU, texV, OverlayTexture.DEFAULT_UV, light,
                nx, ny, nz);
    }

    /**
     * One puff per 2x2x2 texel group (one block) holding smoke, with the group's mean density; thinned with bigger
     * puffs past {@link #MAX_PUFFS}. / 每个有烟的 2x2x2 纹素组（一格）一个烟团，取组内平均浓度；超过上限时稀疏放置更大的烟团。
     */
    private static void build(ClientWorld world, List<SmokeVolumes.Volume> volumes) {
        builtTime = world.getTime();
        int dense = 0;
        for (SmokeVolumes.Volume v : volumes) {
            for (int k = 0; k < v.texSizeZ(); k += GROUP) {
                for (int j = 0; j < v.texSizeY(); j += GROUP) {
                    for (int i = 0; i < v.texSizeX(); i += GROUP) {
                        if (groupDensity(v, i, j, k) >= MIN_DENSITY) {
                            dense++;
                        }
                    }
                }
            }
        }
        // Thinning keeps the per-ray coverage: stride s spreads one puff over s groups, so it grows by cbrt(s).
        // 稀疏放置保持每条视线的覆盖：步长 s 让一个烟团代表 s 个纹素组，因此边长放大 s 的立方根倍。
        int stride = Math.max(1, (dense + MAX_PUFFS - 1) / MAX_PUFFS);
        float scale = (float) Math.cbrt(stride);
        ensureCapacity(Math.min(MAX_PUFFS, dense));
        count = 0;
        double size = SmokeCloudSnapshot.CELL_SIZE * GROUP;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int seen = 0;
        for (SmokeVolumes.Volume v : volumes) {
            for (int k = 0; k < v.texSizeZ(); k += GROUP) {
                for (int j = 0; j < v.texSizeY(); j += GROUP) {
                    for (int i = 0; i < v.texSizeX(); i += GROUP) {
                        float density = groupDensity(v, i, j, k);
                        if (density < MIN_DENSITY || seen++ % stride != 0 || count >= MAX_PUFFS) {
                            continue;
                        }
                        int hash = hash(v.cloudId(), i, j, k);
                        double x = v.minX() + (i / GROUP + 0.5) * size + jitter(hash) * size * 0.15;
                        double y = v.minY() + (j / GROUP + 0.5) * size + jitter(hash >>> 8) * size * 0.15;
                        double z = v.minZ() + (k / GROUP + 0.5) * size + jitter(hash >>> 16) * size * 0.15;
                        int base = count * 6;
                        puffs[base] = (float) x;
                        puffs[base + 1] = (float) y;
                        puffs[base + 2] = (float) z;
                        puffs[base + 3] = HALF_SIZE * scale;
                        puffs[base + 4] = Math.min(1.0F, ALPHA_PER_DENSITY * density);
                        puffs[base + 5] = (hash >>> 24) * (MathHelper.TAU / 256.0F);
                        lights[count] = WorldRenderer.getLightmapCoordinates(world, pos.set(x, y, z));
                        count++;
                    }
                }
            }
        }
    }

    private static float groupDensity(SmokeVolumes.Volume v, int i, int j, int k) {
        float sum = 0.0F;
        for (int c = 0; c < 8; c++) {
            sum += SmokeVolumes.texelDensity(v, i + (c & 1), j + ((c >> 1) & 1), k + ((c >> 2) & 1));
        }
        return sum * 0.125F;
    }

    private static void sort(Vec3d eye) {
        for (int i = 0; i < count; i++) {
            float dx = (float) (puffs[i * 6] - eye.x);
            float dy = (float) (puffs[i * 6 + 1] - eye.y);
            float dz = (float) (puffs[i * 6 + 2] - eye.z);
            distances[i] = dx * dx + dy * dy + dz * dz;
            order[i] = i;
        }
        // Far to near, so blending is right whether or not the batch is re-sorted. / 由远到近，无论批次是否重新排序混合都正确。
        IntArrays.quickSort(order, 0, count, (a, b) -> Float.compare(distances[b], distances[a]));
    }

    private static void ensureCapacity(int puffCount) {
        if (puffs.length / 6 < puffCount) {
            puffs = new float[puffCount * 6];
            lights = new int[puffCount];
            order = new int[puffCount];
            distances = new float[puffCount];
        }
    }

    private static int hash(int cloud, int i, int j, int k) {
        int h = cloud * 0x9E3779B1 + i * 0x85EBCA77 + j * 0xC2B2AE3D + k * 0x27D4EB2F;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h;
    }

    /** -1..1 from the low 8 bits. / 由低 8 位得到 -1..1。 */
    private static float jitter(int bits) {
        return (bits & 0xFF) / 127.5F - 1.0F;
    }

    /** A soft round puff, generated once (no asset). / 柔和的圆形烟团，只生成一次（无需资源文件）。 */
    private static void ensureTexture() {
        if (textureReady) {
            return;
        }
        NativeImage image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, false);
        float half = TEXTURE_SIZE * 0.5F;
        for (int py = 0; py < TEXTURE_SIZE; py++) {
            for (int px = 0; px < TEXTURE_SIZE; px++) {
                float dx = (px + 0.5F - half) / half;
                float dy = (py + 0.5F - half) / half;
                float r2 = Math.min(1.0F, dx * dx + dy * dy);
                // A wide shoulder, so neighbouring puffs leave no gaps. / 宽肩衰减，使相邻烟团之间不留缝隙。
                float falloff = (1.0F - r2) * (1.0F + 0.5F * r2) / (1.0F + 0.5F * r2 * r2);
                int alpha = Math.round(255.0F * falloff);
                image.setColor(px, py, (alpha << 24) | 0x00FFFFFF);
            }
        }
        MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE, new NativeImageBackedTexture(image));
        textureReady = true;
    }

    /** Resource reload / disconnect: the texture manager may have dropped the texture. / 资源重载、断线：纹理管理器可能已丢弃纹理。 */
    static void reset() {
        textureReady = false;
        count = 0;
        builtTime = Long.MIN_VALUE;
    }
}
