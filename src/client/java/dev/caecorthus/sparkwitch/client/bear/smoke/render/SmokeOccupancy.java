package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudSnapshot;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Empty-space skipping for the visible smoke march: per cloud, a coarse {@code GL_R8} 3D texture (nearest filtering,
 * on the density volume's cell grid widened by {@link #EXTENSION} cells) that is 0 only where no present cell lies
 * within {@link #DILATION} cells. A march sample whose unwarped point reads 0 in every cloud whose ray interval holds
 * it can only find density 0 at its warped and mirrored taps ({@link SmokeMarchRules#warpReach()} per axis plus the
 * trilinear footprint), so the shader takes the empty branch without its noise and density taps: the same step, the
 * same result, just cheaper. Presence never changes within one snapshot (timeline and holes only lower density), so a
 * texture is built once per snapshot. Render thread only; reads {@link SmokeClientState}.
 * 可见烟雾步进的空区跳过：每团烟一张粗糙的 {@code GL_R8} 3D 纹理（最近点过滤，位于浓度体的小格网格上并向外扩
 * {@link #EXTENSION} 格），只有在 {@link #DILATION} 格内没有任何有烟格时才为 0。某步的未扭曲采样点在所有光线区间包含它的
 * 烟团中都读到 0 时，其扭曲与镜像采样（每轴至多 {@link SmokeMarchRules#warpReach()}，再加三线性覆盖范围）只能读到浓度 0，
 * 因此着色器直接走空样本分支、省去噪声与浓度采样：步长相同、结果相同，只是更便宜。同一快照内有烟格不会变化（时间线与空洞
 * 只会降低浓度），所以每个快照只构建一次纹理。仅限渲染线程；读取 {@link SmokeClientState}。
 */
final class SmokeOccupancy {
    /**
     * Clouds with an occupancy texture per march (texture units); later clouds never skip.
     * 每次步进带占用纹理的烟团数（纹理单元）；之后的烟团不跳过。
     */
    static final int MAX_CLOUDS = SmokeMarchRules.OCCUPANCY_CLOUDS;
    /**
     * Cells added on each side of the density box, so the whole warp-widened march interval
     * ({@link SmokeMarchRules#warpReach()} = 3.6 cells) is covered.
     * 浓度盒每侧多出的格数，覆盖按扭曲外扩后的整个步进区间（{@link SmokeMarchRules#warpReach()} = 3.6 格）。
     */
    static final int EXTENSION = (int) Math.ceil(SmokeMarchRules.warpReach() / SmokeCloudSnapshot.CELL_SIZE);
    /**
     * Chebyshev reach in cells from an occupancy texel to any density cell its point can read: up to one cell inside
     * the texel, the warp, and the trilinear footprint, plus one spare cell against rounding.
     * 从占用纹素到其采样点可能读到的任何浓度格的切比雪夫距离（格）：纹素内至多一格、扭曲、三线性覆盖范围，再加一格防舍入误差。
     */
    static final int DILATION = EXTENSION + 2;

    private static final Map<Integer, Entry> BY_CLOUD = new HashMap<>();

    private static final class Entry {
        final SmokeCloudSnapshot cloud;
        int texture;

        Entry(SmokeCloudSnapshot cloud) {
            this.cloud = cloud;
        }
    }

    private SmokeOccupancy() {
    }

    /**
     * The occupancy texture of {@code volume}, or 0 when its snapshot is unknown (that cloud then never skips). The
     * texture's box is the volume's widened by {@link #EXTENSION} cells on every side. Called with the GL state saved.
     * {@code volume} 的占用纹理；其快照未知时为 0（该烟团不跳过）。纹理的盒子为浓度体盒子每侧外扩 {@link #EXTENSION} 格。
     * 调用时 GL 状态已保存。
     */
    static int texture(SmokeVolumes.Volume volume) {
        SmokeCloudSnapshot cloud = null;
        for (SmokeCloudSnapshot candidate : SmokeClientState.clouds()) {
            if (candidate.cloudId() == volume.cloudId()) {
                cloud = candidate;
                break;
            }
        }
        if (cloud == null || cloud.sizeX() + 2 != volume.texSizeX() || cloud.sizeY() + 2 != volume.texSizeY()
                || cloud.sizeZ() + 2 != volume.texSizeZ()) {
            return 0;
        }
        Entry entry = BY_CLOUD.get(cloud.cloudId());
        if (entry == null || entry.cloud != cloud) {
            if (entry != null) {
                delete(entry);
            }
            prune();
            entry = new Entry(cloud);
            entry.texture = upload(cloud);
            BY_CLOUD.put(cloud.cloudId(), entry);
        }
        return entry.texture;
    }

    /** Frees every texture (renderer reset or failure). / 释放全部纹理（渲染器重置或失败）。 */
    static void release() {
        for (Entry entry : BY_CLOUD.values()) {
            delete(entry);
        }
        BY_CLOUD.clear();
    }

    /**
     * Dilated presence on the widened grid, x fastest: texel {@code (x, y, z)} covers density cell
     * {@code (x, y, z) - EXTENSION} (density cell 0 is the volume's empty border, so snapshot cell {@code i} is density
     * cell {@code i + 1}). / 外扩网格上膨胀后的有烟标记（x 最快）：纹素 {@code (x, y, z)} 对应浓度格
     * {@code (x, y, z) - EXTENSION}（浓度格 0 是浓度体的空外圈，因此快照格 {@code i} 为浓度格 {@code i + 1}）。
     */
    static byte[] occupancy(SmokeCloudSnapshot cloud) {
        int nx = cloud.sizeX() + 2 + 2 * EXTENSION;
        int ny = cloud.sizeY() + 2 + 2 * EXTENSION;
        int nz = cloud.sizeZ() + 2 + 2 * EXTENSION;
        byte[] grid = new byte[nx * ny * nz];
        int offset = EXTENSION + 1;
        for (int k = 0; k < cloud.sizeZ(); k++) {
            for (int j = 0; j < cloud.sizeY(); j++) {
                for (int i = 0; i < cloud.sizeX(); i++) {
                    if (cloud.present(cloud.index(i, j, k))) {
                        grid[(i + offset) + nx * ((j + offset) + ny * (k + offset))] = 1;
                    }
                }
            }
        }
        dilate(grid, nx, ny, nz, 1, nx);
        dilate(grid, nx, ny, nz, nx, ny);
        dilate(grid, nx, ny, nz, nx * ny, nz);
        for (int i = 0; i < grid.length; i++) {
            grid[i] = grid[i] != 0 ? (byte) 0xFF : 0;
        }
        return grid;
    }

    /** Sliding-window OR of radius {@link #DILATION} along one axis. / 沿一个轴做半径 {@link #DILATION} 的滑动窗口“或”。 */
    private static void dilate(byte[] grid, int nx, int ny, int nz, int stride, int length) {
        int[] prefix = new int[length + 1];
        int lines = nx * ny * nz / length;
        for (int line = 0; line < lines; line++) {
            int start = lineStart(line, nx, ny, stride);
            for (int i = 0; i < length; i++) {
                prefix[i + 1] = prefix[i] + (grid[start + i * stride] != 0 ? 1 : 0);
            }
            for (int i = 0; i < length; i++) {
                int from = Math.max(0, i - DILATION);
                int to = Math.min(length, i + DILATION + 1);
                grid[start + i * stride] = prefix[to] - prefix[from] > 0 ? (byte) 1 : 0;
            }
        }
    }

    /**
     * First index of the {@code line}-th line along the axis with {@code stride}.
     * 沿步长为 {@code stride} 的轴第 {@code line} 条线的起始下标。
     */
    private static int lineStart(int line, int nx, int ny, int stride) {
        if (stride == 1) {
            return line * nx;
        }
        if (stride == nx) {
            return (line % nx) + (line / nx) * nx * ny;
        }
        return line;
    }

    private static int upload(SmokeCloudSnapshot cloud) {
        byte[] grid = occupancy(cloud);
        ByteBuffer data = MemoryUtil.memAlloc(grid.length);
        try {
            data.put(grid).flip();
            int previousUnit = GlStateManager._getActiveTexture();
            int previousAlignment = GlStateManager._getInteger(GL11.GL_UNPACK_ALIGNMENT);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            int previous3d = GlStateManager._getInteger(GL12.GL_TEXTURE_BINDING_3D);
            int texture = GlStateManager._genTexture();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
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
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8, cloud.sizeX() + 2 + 2 * EXTENSION,
                    cloud.sizeY() + 2 + 2 * EXTENSION, cloud.sizeZ() + 2 + 2 * EXTENSION, 0, GL11.GL_RED,
                    GL11.GL_UNSIGNED_BYTE, data);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, previous3d);
            GlStateManager._activeTexture(previousUnit);
            return texture;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    /** Drops entries whose cloud is gone. / 丢弃烟团已消失的条目。 */
    private static void prune() {
        for (Iterator<Map.Entry<Integer, Entry>> it = BY_CLOUD.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Entry> e = it.next();
            boolean live = false;
            for (SmokeCloudSnapshot cloud : SmokeClientState.clouds()) {
                if (cloud == e.getValue().cloud) {
                    live = true;
                    break;
                }
            }
            if (!live) {
                delete(e.getValue());
                it.remove();
            }
        }
    }

    private static void delete(Entry entry) {
        if (entry.texture != 0) {
            GlStateManager._deleteTexture(entry.texture);
            entry.texture = 0;
        }
    }
}
