package dev.caecorthus.sparkwitch.client.bear.smoke;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudSnapshot;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeHole;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeTimeline;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeTunnels;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
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
 * is idempotent within one tick. Only blast holes are baked: bullet tunnels (BEAR WP5) are far thinner than a cell, so
 * {@link #tunnels()} hands every pass the live capsules of the prepared tick and each evaluates them analytically per
 * sample (the visible march at the unwarped point, the outline occlusion march, {@link #opticalDepth}); a tunnel never
 * re-uploads a volume. Each cloud cycles through {@link #TEXTURE_RING} textures, so an upload never writes a texture
 * the GPU may still be reading from the previous ticks' frames (an implicit sync on Apple's GL).
 * 冻结契约（仅渲染线程）：把 {@link SmokeClientState} 转成每团烟一个 GPU 浓度体。每个浓度体是 {@code GL_R8} 格式的
 * {@code GL_TEXTURE_3D}（0..1 浓度，已计入时间线与空洞，线性过滤，边缘钳制），外圈多一层空纹素，因此以
 * {@code (worldPos - min) / (max - min)} 采样即可得到边缘柔和的浓度场。浓度按客户端世界 tick 每秒刷新 20 次，
 * 没有变化的 tick 不上传。各烟雾渲染步骤都先调用 {@link #prepare(ClientWorld)}；同一 tick 内重复调用无副作用。
 * 只烘焙爆炸空洞：子弹通道（BEAR WP5）远细于一格，因此 {@link #tunnels()} 把所准备 tick 的存活胶囊交给各步骤，由其逐采样
 * 解析计算（可见步进在未扭曲的点、描边遮挡步进、{@link #opticalDepth}）；通道从不触发浓度体重新上传。每团烟轮流使用
 * {@link #TEXTURE_RING} 张纹理，上传永远不会写入 GPU 可能仍在读取的前几 tick 帧所用纹理（在 Apple 的 GL 上会隐式同步）。
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

    /**
     * One live bullet tunnel of the prepared tick (BEAR WP5): world-space axis {@code (x0, y0, z0)} →
     * {@code (x1, y1, z1)} and its current radius (closing included, always positive). Every pass applies
     * {@code SmokeTimeline.edgeFactor(radius, SmokeTimeline.TUNNEL_EDGE, distance to the axis)} to the density.
     * 所准备 tick 的一条存活子弹通道（BEAR WP5）：世界坐标轴线与当前半径（含合拢，恒为正）。各步骤都对浓度乘以
     * {@code SmokeTimeline.edgeFactor(半径, SmokeTimeline.TUNNEL_EDGE, 到轴线的距离)}。
     */
    public record Tunnel(double x0, double y0, double z0, double x1, double y1, double z1, float radius) {
    }

    /**
     * Most tunnels a pass evaluates (the server keeps at most as many per world); the newest win, and a tunnel left
     * out stays closed (fail closed for the outline mask). / 每次渲染计算的最多通道数（服务端每世界也至多这么多）；取最新的，
     * 被舍弃的通道保持闭合（对描边遮罩而言失败即遮挡）。
     */
    public static final int MAX_RENDER_TUNNELS = 16;
    /** Textures per cloud cycled by the uploads. / 每团烟轮流上传的纹理数。 */
    static final int TEXTURE_RING = 3;

    private static final class Entry {
        final SmokeCloudSnapshot cloud;
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        final byte[] densities;
        ByteBuffer upload;
        /** The ring; {@link #texture} is the one last uploaded. / 纹理环；{@link #texture} 为最近上传的那张。 */
        final int[] ring = new int[TEXTURE_RING];
        int ringIndex = -1;
        int texture;
        float peak;

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
    private static final List<Tunnel> TUNNELS = new ArrayList<>();
    private static final List<Tunnel> TUNNELS_VIEW = Collections.unmodifiableList(TUNNELS);
    private static long preparedTime = Long.MIN_VALUE;
    private static long preparedRevision = Long.MIN_VALUE;
    private static long preparedDensityRevision = Long.MIN_VALUE;
    /** False while the shader-pack billboard fallback draws the smoke ({@link #showTunnels}). / 公告板兜底期间为 false。 */
    private static boolean tunnelsShown = true;

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
        Tunnel[] near = tunnelsNear(x0, y0, z0, x1, y1, z1);
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
                double x = x0 + dx * f;
                double y = y0 + dy * f;
                double z = z0 + dz * f;
                double density = sample(entry, volume, x, y, z);
                if (density > 0.0 && near != null) {
                    density *= tunnelFactor(near, x, y, z);
                }
                sum += density * CPU_STEP;
            }
        }
        return (float) (sum * EXTINCTION_PER_BLOCK);
    }

    /**
     * Live tunnels of the last {@link #prepare}, newest first, at most {@link #MAX_RENDER_TUNNELS}, each touching an
     * active volume (empty when none). / 最近一次 {@link #prepare} 的存活通道，最新的在前，至多
     * {@link #MAX_RENDER_TUNNELS} 条，且都触及某个活动浓度体（没有时为空）。
     */
    public static List<Tunnel> tunnels() {
        return TUNNELS_VIEW;
    }

    /**
     * Packs {@link #tunnels()} camera-relative for the GPU passes' {@code TunnelStart} (xyz start, w radius; 4 floats
     * each) and {@code TunnelAxis} (end - start; 3 floats each) uniforms, zero-filling the rest; returns the count.
     * 把 {@link #tunnels()} 以相机为原点打包成 GPU 各步骤的 {@code TunnelStart}（xyz 起点、w 半径；每条 4 个 float）与
     * {@code TunnelAxis}（终点 - 起点；每条 3 个 float）uniform，其余填 0；返回条数。
     */
    public static int packTunnels(double eyeX, double eyeY, double eyeZ, float[] start, float[] axis) {
        Arrays.fill(start, 0.0F);
        Arrays.fill(axis, 0.0F);
        int count = Math.min(TUNNELS.size(), Math.min(start.length / 4, axis.length / 3));
        for (int i = 0; i < count; i++) {
            Tunnel tunnel = TUNNELS.get(i);
            start[i * 4] = (float) (tunnel.x0() - eyeX);
            start[i * 4 + 1] = (float) (tunnel.y0() - eyeY);
            start[i * 4 + 2] = (float) (tunnel.z0() - eyeZ);
            start[i * 4 + 3] = tunnel.radius();
            axis[i * 3] = (float) (tunnel.x1() - tunnel.x0());
            axis[i * 3 + 1] = (float) (tunnel.y1() - tunnel.y0());
            axis[i * 3 + 2] = (float) (tunnel.z1() - tunnel.z0());
        }
        return count;
    }

    /**
     * Whether bullet tunnels open the smoke. The shader-pack billboard fallback ({@code shaderpack.SmokeBillboards}, one
     * puff per block) cannot show a tunnel, so while it runs tunnels are dropped here and the outline mask and
     * {@link #opticalDepth} stay as closed as the smoke looks (fail closed); blast holes are unaffected.
     * 子弹通道是否打开烟雾。光影包公告板兜底（{@code shaderpack.SmokeBillboards}，每格一个烟团）显示不了通道，因此其运行期间在此
     * 丢弃通道，使描边遮罩与 {@link #opticalDepth} 与烟看上去一样闭合（失败即遮挡）；爆炸空洞不受影响。
     */
    public static void showTunnels(boolean shown) {
        if (tunnelsShown != shown) {
            tunnelsShown = shown;
            // Re-collect on the next prepare. / 下一次 prepare 时重新收集。
            preparedRevision = Long.MIN_VALUE;
        }
    }

    /**
     * Density multiplier the given tunnels leave at a world point (CPU mirror of the GPU passes).
     * 给定通道在某世界坐标处留下的浓度系数（GPU 各步骤的 CPU 镜像）。
     */
    static double tunnelFactor(Tunnel[] tunnels, double x, double y, double z) {
        float factor = 1.0F;
        for (Tunnel tunnel : tunnels) {
            double distance = SmokeTunnels.distanceToSegment(x, y, z, tunnel.x0(), tunnel.y0(), tunnel.z0(),
                    tunnel.x1(), tunnel.y1(), tunnel.z1());
            factor *= SmokeTimeline.edgeFactor(tunnel.radius(), SmokeTimeline.TUNNEL_EDGE, (float) distance);
            if (factor <= 0.0F) {
                return 0.0;
            }
        }
        return factor;
    }

    /** Tunnels whose capsule reaches the segment, or null when none does. / 胶囊触及该线段的通道；没有时为 null。 */
    private static Tunnel[] tunnelsNear(double x0, double y0, double z0, double x1, double y1, double z1) {
        if (TUNNELS.isEmpty()) {
            return null;
        }
        List<Tunnel> near = null;
        for (Tunnel tunnel : TUNNELS) {
            double reach = tunnel.radius();
            if (SmokeTunnels.segmentSegmentDistanceSquared(x0, y0, z0, x1, y1, z1, tunnel.x0(), tunnel.y0(),
                    tunnel.z0(), tunnel.x1(), tunnel.y1(), tunnel.z1()) < reach * reach) {
                if (near == null) {
                    near = new ArrayList<>(2);
                }
                near.add(tunnel);
            }
        }
        return near == null ? null : near.toArray(new Tunnel[0]);
    }

    /**
     * CPU density (0..1) of texel {@code (x, y, z)} of {@code volume} (border texels included; they are 0) from the last
     * {@link #prepare}; 0 when the volume is gone or the texel is out of range. Used by the shader-pack billboard
     * fallback, which places one puff per texel.
     * {@code volume} 第 {@code (x, y, z)} 个纹素的 CPU 浓度（0..1，含外圈纹素，其值为 0），取最近一次 {@link #prepare}；浓度体已不存在
     * 或纹素越界时为 0。供光影包公告板兜底使用，每个纹素放一个烟团。
     */
    public static float texelDensity(Volume volume, int x, int y, int z) {
        Entry entry = ENTRIES.get(volume.cloudId());
        if (entry == null || x < 0 || y < 0 || z < 0 || x >= entry.sizeX || y >= entry.sizeY || z >= entry.sizeZ) {
            return 0.0F;
        }
        return (entry.densities[x + entry.sizeX * (y + entry.sizeY * z)] & 0xFF) / 255.0F;
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
        long densityRevision = SmokeClientState.densityRevision();
        if (now == preparedTime && densityRevision == preparedDensityRevision) {
            // Only tunnels changed this tick: the baked densities stand. / 本 tick 只有通道变化：烘焙浓度不变。
            preparedRevision = revision;
            collectTunnels(now);
            return ACTIVE_VIEW;
        }
        preparedTime = now;
        preparedRevision = revision;
        preparedDensityRevision = densityRevision;

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
            if (changed || entry.texture == 0) {
                upload(entry);
            }
            ACTIVE.add(toVolume(entry));
        }
        collectTunnels(now);
        return ACTIVE_VIEW;
    }

    /**
     * Rebuilds {@link #tunnels()}: newest first, live at {@code now}, touching an active volume (box grown by the
     * radius), at most {@link #MAX_RENDER_TUNNELS}. / 重建 {@link #tunnels()}：最新的在前、在 {@code now} 仍存活、触及某个
     * 活动浓度体（盒子按半径外扩）、至多 {@link #MAX_RENDER_TUNNELS} 条。
     */
    private static void collectTunnels(long now) {
        TUNNELS.clear();
        if (!tunnelsShown) {
            return;
        }
        List<SmokeHole> tunnels = SmokeClientState.tunnels();
        double[] span = new double[2];
        for (int i = tunnels.size() - 1; i >= 0 && TUNNELS.size() < MAX_RENDER_TUNNELS && !ACTIVE.isEmpty(); i--) {
            SmokeHole tunnel = tunnels.get(i);
            float radius = SmokeTimeline.holeRadius(tunnel, now);
            if (!(radius > 0.0F)) {
                continue;
            }
            // Same reach as the server clip: the radius plus the warped edge band. / 与服务端裁剪相同：半径加扭曲边缘带。
            double grow = radius + SmokeTunnels.CLIP_MARGIN;
            for (Volume v : ACTIVE) {
                if (SmokeTunnels.clipToBox(tunnel.x(), tunnel.y(), tunnel.z(), tunnel.endX(), tunnel.endY(),
                        tunnel.endZ(), v.minX() - grow, v.minY() - grow, v.minZ() - grow, v.maxX() + grow,
                        v.maxY() + grow, v.maxZ() + grow, span)) {
                    TUNNELS.add(new Tunnel(tunnel.x(), tunnel.y(), tunnel.z(), tunnel.endX(), tunnel.endY(),
                            tunnel.endZ(), radius));
                    break;
                }
            }
        }
    }

    /** Frees every texture (disconnect, world change). / 释放全部纹理（断开连接、切换世界）。 */
    public static void releaseAll() {
        RenderSystem.assertOnRenderThread();
        for (Entry entry : ENTRIES.values()) {
            release(entry);
        }
        ENTRIES.clear();
        ACTIVE.clear();
        TUNNELS.clear();
        preparedTime = Long.MIN_VALUE;
        preparedRevision = Long.MIN_VALUE;
        preparedDensityRevision = Long.MIN_VALUE;
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
        // Next texture of the ring: the GPU finished with it ticks ago, so the write never waits for in-flight frames.
        // 纹理环中的下一张：GPU 几个 tick 前就已用完，写入无需等待仍在执行的帧。
        entry.ringIndex = (entry.ringIndex + 1) % TEXTURE_RING;
        boolean allocate = entry.ring[entry.ringIndex] == 0;
        if (allocate) {
            entry.ring[entry.ringIndex] = GlStateManager._genTexture();
        }
        entry.texture = entry.ring[entry.ringIndex];
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
        if (!allocate) {
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, entry.sizeX, entry.sizeY, entry.sizeZ,
                    GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, entry.upload);
        } else {
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8, entry.sizeX, entry.sizeY, entry.sizeZ, 0,
                    GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, entry.upload);
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
        for (int i = 0; i < TEXTURE_RING; i++) {
            if (entry.ring[i] != 0) {
                GlStateManager._deleteTexture(entry.ring[i]);
                entry.ring[i] = 0;
            }
        }
        entry.texture = 0;
        entry.ringIndex = -1;
        if (entry.upload != null) {
            MemoryUtil.memFree(entry.upload);
            entry.upload = null;
        }
    }
}
