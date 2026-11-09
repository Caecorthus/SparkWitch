package dev.caecorthus.sparkwitch.client.bear.smoke.shaderpack;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeOcclusion;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Per-entity outline veto under an Iris shader pack (defense in depth next to the per-pixel {@code SmokeOutlineMask}):
 * an entity behind BEAR smoke gets no outline at all ({@code SmokeOutlineVetoMixin} answers {@code hasOutline} false),
 * which covers every producer that asks {@code hasOutline}: Wathe instinct and its faction colours, vanilla glowing,
 * corpses and SparkWitch highlights, including anything a pack would draw from that flag. "Behind" is decided on the
 * client thread once per client tick, from the camera to five points of the entity's box (centre, head, feet, both
 * sides across the view) with {@link SmokeVolumes#opticalDepth}; a point is hidden at
 * {@link SmokeOcclusion#HIDDEN_OPTICAL_DEPTH}. While the after-final-pass smoke works the per-pixel mask already
 * trims partly covered bodies, so only an entity with every point hidden is vetoed; while the billboard fallback runs,
 * a majority (3 of 5) is enough (fail closed). Results are published as immutable id sets through a volatile field and
 * only read by {@link #hides}, because {@code hasOutline} is also called off-thread (Entity Culling's CullThread).
 * Without a pack, or without smoke, the sets are empty and nothing changes. Client-only presentation.
 * Iris 光影包下的逐实体描边否决（与逐像素 {@code SmokeOutlineMask} 互为纵深防御）：BEAR 烟后面的实体完全没有描边
 * （{@code SmokeOutlineVetoMixin} 让 {@code hasOutline} 返回 false），从而覆盖所有询问 {@code hasOutline} 的来源：Wathe 本能及其阵营
 * 颜色、原版发光、尸体与 SparkWitch 高亮，也包括光影包可能依据该标志绘制的内容。“在烟后”在客户端线程每个客户端 tick 判定
 * 一次：用 {@link SmokeVolumes#opticalDepth} 计算相机到实体碰撞箱五个点（中心、头、脚、垂直于视线的两侧）的光学厚度，达到
 * {@link SmokeOcclusion#HIDDEN_OPTICAL_DEPTH} 的点视为被挡住。最终处理之后的烟雾正常时，逐像素遮罩已裁掉部分被挡的身体，
 * 因此只否决五点全被挡的实体；公告板兜底运行时，多数（五点中三点）即可（失败即遮挡）。结果以不可变 id 集合经 volatile 字段
 * 发布，{@link #hides} 只读取，因为 {@code hasOutline} 也会在其他线程被调用（Entity Culling 的 CullThread）。没有光影包或没有烟时
 * 集合为空，一切照旧。纯客户端展示。
 */
public final class SmokeEntityOcclusion {
    /** Hidden points needed while the billboard fallback runs. / 公告板兜底运行时需要被挡住的点数。 */
    static final int FALLBACK_HIDDEN_POINTS = 3;
    static final int POINTS = 5;
    /** Entities farther than this from the camera are never vetoed. / 距相机超过此值的实体不做否决。 */
    private static final double RANGE = 96.0;
    private static final double INSET = 0.1;

    private static volatile IntSet fullyHidden = IntSets.EMPTY_SET;
    private static volatile IntSet mostlyHidden = IntSets.EMPTY_SET;
    private static volatile boolean fallback;

    private SmokeEntityOcclusion() {
    }

    /**
     * Any thread: true when {@code entity}'s outline must not be drawn. Only reads published sets.
     * 任意线程：{@code entity} 的描边不得绘制时为 true。只读取已发布的集合。
     */
    public static boolean hides(Entity entity) {
        IntSet set = fallback ? mostlyHidden : fullyHidden;
        return !set.isEmpty() && entity != null && set.contains(entity.getId());
    }

    /**
     * Client thread, end of every client tick: recomputes the sets (empty without a pack or smoke).
     * 客户端线程，每个客户端 tick 结束时：重新计算集合（没有光影包或没有烟时为空）。
     */
    static void tick(MinecraftClient client, boolean shaderPack, boolean fallbackActive) {
        fallback = fallbackActive;
        ClientWorld world = client.world;
        Camera camera = client.gameRenderer.getCamera();
        if (!shaderPack || world == null || SmokeClientState.isEmpty() || camera == null || !camera.isReady()) {
            clear();
            return;
        }
        List<SmokeVolumes.Volume> volumes = SmokeVolumes.prepare(world);
        if (volumes.isEmpty()) {
            clear();
            return;
        }
        Vec3d eye = camera.getPos();
        IntOpenHashSet fully = null;
        IntOpenHashSet mostly = null;
        double[] points = new double[POINTS * 3];
        for (Entity entity : world.getEntities()) {
            if (entity == camera.getFocusedEntity() && !camera.isThirdPerson()) {
                continue;
            }
            Box box = entity.getBoundingBox();
            if (box.getCenter().squaredDistanceTo(eye) > RANGE * RANGE || !crossesSmoke(eye, box, volumes)) {
                continue;
            }
            samplePoints(eye, box, points);
            int hidden = 0;
            for (int i = 0; i < POINTS; i++) {
                if (SmokeVolumes.opticalDepth(eye.x, eye.y, eye.z, points[i * 3], points[i * 3 + 1], points[i * 3 + 2])
                        >= SmokeOcclusion.HIDDEN_OPTICAL_DEPTH) {
                    hidden++;
                }
            }
            if (hidden >= FALLBACK_HIDDEN_POINTS) {
                if (mostly == null) {
                    mostly = new IntOpenHashSet();
                }
                mostly.add(entity.getId());
            }
            if (hidden >= POINTS) {
                if (fully == null) {
                    fully = new IntOpenHashSet();
                }
                fully.add(entity.getId());
            }
        }
        fullyHidden = fully == null ? IntSets.EMPTY_SET : fully;
        mostlyHidden = mostly == null ? IntSets.EMPTY_SET : mostly;
    }

    static void clear() {
        fullyHidden = IntSets.EMPTY_SET;
        mostlyHidden = IntSets.EMPTY_SET;
    }

    /**
     * Centre, head, feet and the two sides perpendicular to the horizontal view direction, inset from the box faces.
     * 中心、头、脚，以及垂直于水平视线方向的两侧，均自碰撞箱表面向内收。
     */
    static void samplePoints(Vec3d eye, Box box, double[] out) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double halfHeight = Math.max(0.0, (box.maxY - box.minY) * 0.5 - INSET);
        double halfWidth = Math.max(0.0, Math.min(box.maxX - box.minX, box.maxZ - box.minZ) * 0.5 - INSET);
        double dx = cx - eye.x;
        double dz = cz - eye.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double sideX = length > 1.0E-6 ? -dz / length : 1.0;
        double sideZ = length > 1.0E-6 ? dx / length : 0.0;
        set(out, 0, cx, cy, cz);
        set(out, 1, cx, cy + halfHeight, cz);
        set(out, 2, cx, cy - halfHeight, cz);
        set(out, 3, cx + sideX * halfWidth, cy, cz + sideZ * halfWidth);
        set(out, 4, cx - sideX * halfWidth, cy, cz - sideZ * halfWidth);
    }

    private static void set(double[] out, int index, double x, double y, double z) {
        out[index * 3] = x;
        out[index * 3 + 1] = y;
        out[index * 3 + 2] = z;
    }

    /** Cheap reject: the eye-to-box segment's bounds must touch some smoke box. / 廉价剔除：视线段包围盒须触及某个烟盒。 */
    private static boolean crossesSmoke(Vec3d eye, Box box, List<SmokeVolumes.Volume> volumes) {
        double minX = Math.min(eye.x, box.minX);
        double minY = Math.min(eye.y, box.minY);
        double minZ = Math.min(eye.z, box.minZ);
        double maxX = Math.max(eye.x, box.maxX);
        double maxY = Math.max(eye.y, box.maxY);
        double maxZ = Math.max(eye.z, box.maxZ);
        for (SmokeVolumes.Volume v : volumes) {
            if (v.maxX() > minX && v.minX() < maxX && v.maxY() > minY && v.minY() < maxY
                    && v.maxZ() > minZ && v.minZ() < maxZ) {
                return true;
            }
        }
        return false;
    }
}
