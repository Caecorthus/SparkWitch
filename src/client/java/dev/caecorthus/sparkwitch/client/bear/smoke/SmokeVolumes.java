package dev.caecorthus.sparkwitch.client.bear.smoke;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudSnapshot;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeHole;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeTimeline;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Frozen contract (render thread only): turns {@link SmokeClientState} into one GPU density volume per cloud. Each
 * volume is a {@code GL_TEXTURE_3D} of format {@code GL_R8} (0..1 density, timeline and holes already applied, linear
 * filtering, clamp to edge) with a one-texel empty border, so sampling it at
 * {@code (worldPos - min) / (max - min)} gives a soft-edged density field. Densities refresh once per client world
 * tick (20 Hz); unchanged ticks skip the upload. Every smoke pass calls {@link #prepare(ClientWorld)} first; the call
 * is idempotent within one tick.
 * 冻结契约（仅渲染线程）：把 {@link SmokeClientState} 转成每团烟一个 GPU 浓度体。每个浓度体是 {@code GL_R8} 格式的
 * {@code GL_TEXTURE_3D}（0..1 浓度，已计入时间线与空洞，线性过滤，边缘钳制），外圈多一层空纹素，因此以
 * {@code (worldPos - min) / (max - min)} 采样即可得到边缘柔和的浓度场。浓度按客户端世界 tick 每秒刷新 20 次，
 * 没有变化的 tick 不上传。各烟雾渲染步骤都先调用 {@link #prepare(ClientWorld)}；同一 tick 内重复调用无副作用。
 */
public final class SmokeVolumes {
    /**
     * One uploaded cloud. Bounds are world coordinates of the whole texture, border included.
     * 一团已上传的烟。边界是整张纹理（含外圈）的世界坐标。
     */
    public record Volume(
            int cloudId,
            int texture,
            int texSizeX,
            int texSizeY,
            int texSizeZ,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            float peakDensity
    ) {
    }

    private static final class Entry {
        final SmokeCloudSnapshot cloud;
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        final byte[] densities;
        ByteBuffer upload;
        int texture;
        float peak;
        boolean uploadedOnce;

        Entry(SmokeCloudSnapshot cloud) {
            this.cloud = cloud;
            this.sizeX = cloud.sizeX() + 2;
            this.sizeY = cloud.sizeY() + 2;
            this.sizeZ = cloud.sizeZ() + 2;
            this.densities = new byte[sizeX * sizeY * sizeZ];
        }
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static final List<Volume> ACTIVE = new ArrayList<>();
    private static final List<Volume> ACTIVE_VIEW = Collections.unmodifiableList(ACTIVE);
    private static long preparedTime = Long.MIN_VALUE;
    private static long preparedRevision = Long.MIN_VALUE;

    /**
     * Optical depth per block of density-1 smoke. The visible smoke and the outline occlusion both use it, so an
     * outline disappears exactly where the smoke looks opaque (about one block of full smoke, owner 2026-11-05).
     * 浓度为 1 的烟每格的光学厚度。可见烟雾与描边遮挡共用此值，使描边恰好在烟看起来不透明处消失
     * （约一格满浓度烟，所有者 2026-11-05）。
     */
    public static final float EXTINCTION_PER_BLOCK = 1.5F;
    private static final double CPU_STEP = SmokeCloudSnapshot.CELL_SIZE * 0.25;

    private SmokeVolumes() {
    }

    /**
     * CPU optical depth of the smoke on the segment between two world points, from the densities of the last
     * {@link #prepare} (0 when no smoke). Used by cheap per-entity checks such as the crosshair name label.
     * 两个世界坐标之间线段上的烟雾光学厚度（CPU 计算，取最近一次 {@link #prepare} 的浓度；无烟时为 0）。
     * 供准星名字标签这类逐实体的廉价判断使用。
     */
    public static float opticalDepth(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 1.0E-6 || ACTIVE.isEmpty()) {
            return 0.0F;
        }
        double sum = 0.0;
        for (Volume volume : ACTIVE) {
            Entry entry = ENTRIES.get(volume.cloudId());
            if (entry == null) {
                continue;
            }
            double[] span = clipToBox(x0, y0, z0, dx, dy, dz, volume);
            if (span == null) {
                continue;
            }
            double from = span[0] * length;
            double to = span[1] * length;
            for (double t = from + CPU_STEP * 0.5; t < to; t += CPU_STEP) {
                double f = t / length;
                sum += sample(entry, volume, x0 + dx * f, y0 + dy * f, z0 + dz * f) * CPU_STEP;
            }
        }
        return (float) (sum * EXTINCTION_PER_BLOCK);
    }

    private static double[] clipToBox(double x0, double y0, double z0, double dx, double dy, double dz, Volume v) {
        double tMin = 0.0;
        double tMax = 1.0;
        double[] origin = {x0, y0, z0};
        double[] dir = {dx, dy, dz};
        double[] min = {v.minX(), v.minY(), v.minZ()};
        double[] max = {v.maxX(), v.maxY(), v.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1.0E-12) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
                    return null;
                }
                continue;
            }
            double a = (min[axis] - origin[axis]) / dir[axis];
            double b = (max[axis] - origin[axis]) / dir[axis];
            tMin = Math.max(tMin, Math.min(a, b));
            tMax = Math.min(tMax, Math.max(a, b));
            if (tMin >= tMax) {
                return null;
            }
        }
        return new double[]{tMin, tMax};
    }

    /** Trilinear sample matching the GPU texture's linear filter. / 与 GPU 线性过滤一致的三线性采样。 */
    private static double sample(Entry entry, Volume volume, double x, double y, double z) {
        double cell = SmokeCloudSnapshot.CELL_SIZE;
        double fx = (x - volume.minX()) / cell - 0.5;
        double fy = (y - volume.minY()) / cell - 0.5;
        double fz = (z - volume.minZ()) / cell - 0.5;
        int ix = (int) Math.floor(fx);
        int iy = (int) Math.floor(fy);
        int iz = (int) Math.floor(fz);
        double tx = fx - ix;
        double ty = fy - iy;
        double tz = fz - iz;
        double result = 0.0;
        for (int c = 0; c < 8; c++) {
            int cx = ix + (c & 1);
            int cy = iy + ((c >> 1) & 1);
            int cz = iz + ((c >> 2) & 1);
            double w = ((c & 1) != 0 ? tx : 1.0 - tx) * (((c >> 1) & 1) != 0 ? ty : 1.0 - ty)
                    * (((c >> 2) & 1) != 0 ? tz : 1.0 - tz);
            if (w <= 0.0) {
                continue;
            }
            cx = Math.max(0, Math.min(entry.sizeX - 1, cx));
            cy = Math.max(0, Math.min(entry.sizeY - 1, cy));
            cz = Math.max(0, Math.min(entry.sizeZ - 1, cz));
            result += w * (entry.densities[cx + entry.sizeX * (cy + entry.sizeY * cz)] & 0xFF) / 255.0;
        }
        return result;
    }

    /**
     * Refreshes the volumes for the current world tick and returns those holding any smoke (empty list when none).
     * 按当前世界 tick 刷新浓度体，返回仍有烟的那些（没有时返回空列表）。
     */
    public static List<Volume> prepare(ClientWorld world) {
        RenderSystem.assertOnRenderThread();
        long now = world.getTime();
        SmokeClientState.prune(now);
        long revision = SmokeClientState.revision();
        if (now == preparedTime && revision == preparedRevision) {
            return ACTIVE_VIEW;
        }
        preparedTime = now;
        preparedRevision = revision;

        Map<Integer, SmokeCloudSnapshot> live = new HashMap<>();
        for (SmokeCloudSnapshot cloud : SmokeClientState.clouds()) {
            live.put(cloud.cloudId(), cloud);
        }
        for (Iterator<Map.Entry<Integer, Entry>> it = ENTRIES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Entry> e = it.next();
            SmokeCloudSnapshot current = live.get(e.getKey());
            if (current != e.getValue().cloud) {
                release(e.getValue());
                it.remove();
            }
        }

        ACTIVE.clear();
        List<SmokeHole> holes = SmokeClientState.holes();
        for (SmokeCloudSnapshot cloud : live.values()) {
            Entry entry = ENTRIES.computeIfAbsent(cloud.cloudId(), id -> new Entry(cloud));
            boolean changed = fill(entry, now, holes);
            if (entry.peak <= 0.0F) {
                continue;
            }
            if (changed || !entry.uploadedOnce) {
                upload(entry);
            }
            ACTIVE.add(toVolume(entry));
        }
        return ACTIVE_VIEW;
    }

    /** Frees every texture (disconnect, world change). / 释放全部纹理（断开连接、切换世界）。 */
    public static void releaseAll() {
        RenderSystem.assertOnRenderThread();
        for (Entry entry : ENTRIES.values()) {
            release(entry);
        }
        ENTRIES.clear();
        ACTIVE.clear();
        preparedTime = Long.MIN_VALUE;
        preparedRevision = Long.MIN_VALUE;
    }

    private static boolean fill(Entry entry, long now, List<SmokeHole> holes) {
        SmokeCloudSnapshot cloud = entry.cloud;
        float age = now - cloud.spawnTime();
        double half = SmokeCloudSnapshot.CELL_SIZE * 0.5;
        boolean changed = false;
        float peak = 0.0F;
        for (int k = 0; k < cloud.sizeZ(); k++) {
            for (int j = 0; j < cloud.sizeY(); j++) {
                for (int i = 0; i < cloud.sizeX(); i++) {
                    int src = cloud.index(i, j, k);
                    float density = 0.0F;
                    if (cloud.present(src)) {
                        density = SmokeTimeline.cellDensity(age, cloud.arrivalTick(src), cloud.openSky(src));
                        if (density > 0.0F && !holes.isEmpty()) {
                            density *= SmokeTimeline.holeFactor(
                                    cloud.minX() + i * SmokeCloudSnapshot.CELL_SIZE + half,
                                    cloud.minY() + j * SmokeCloudSnapshot.CELL_SIZE + half,
                                    cloud.minZ() + k * SmokeCloudSnapshot.CELL_SIZE + half,
                                    holes, now);
                        }
                    }
                    byte value = (byte) Math.round(Math.max(0.0F, Math.min(1.0F, density)) * 255.0F);
                    int dst = (i + 1) + entry.sizeX * ((j + 1) + entry.sizeY * (k + 1));
                    if (entry.densities[dst] != value) {
                        entry.densities[dst] = value;
                        changed = true;
                    }
                    peak = Math.max(peak, density);
                }
            }
        }
        entry.peak = peak;
        return changed;
    }

    private static void upload(Entry entry) {
        if (entry.upload == null) {
            entry.upload = MemoryUtil.memAlloc(entry.densities.length);
        }
        entry.upload.clear();
        entry.upload.put(entry.densities).flip();

        int previousUnit = GlStateManager._getActiveTexture();
        int previousAlignment = GlStateManager._getInteger(GL11.GL_UNPACK_ALIGNMENT);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int previous3d = GlStateManager._getInteger(GL12.GL_TEXTURE_BINDING_3D);
        if (entry.texture == 0) {
            entry.texture = GlStateManager._genTexture();
        }
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, entry.texture);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GlStateManager._pixelStore(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        if (entry.uploadedOnce) {
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, entry.sizeX, entry.sizeY, entry.sizeZ,
                    GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, entry.upload);
        } else {
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8, entry.sizeX, entry.sizeY, entry.sizeZ, 0,
                    GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, entry.upload);
            entry.uploadedOnce = true;
        }
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, previous3d);
        GlStateManager._activeTexture(previousUnit);
    }

    private static Volume toVolume(Entry entry) {
        SmokeCloudSnapshot cloud = entry.cloud;
        double cell = SmokeCloudSnapshot.CELL_SIZE;
        double minX = cloud.minX() - cell;
        double minY = cloud.minY() - cell;
        double minZ = cloud.minZ() - cell;
        return new Volume(cloud.cloudId(), entry.texture, entry.sizeX, entry.sizeY, entry.sizeZ,
                minX, minY, minZ,
                minX + entry.sizeX * cell, minY + entry.sizeY * cell, minZ + entry.sizeZ * cell,
                entry.peak);
    }

    private static void release(Entry entry) {
        if (entry.texture != 0) {
            GlStateManager._deleteTexture(entry.texture);
            entry.texture = 0;
        }
        if (entry.upload != null) {
            MemoryUtil.memFree(entry.upload);
            entry.upload = null;
        }
    }
}
