package dev.caecorthus.sparkwitch.client.bear.smoke;

import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudSnapshot;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeHole;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeTimeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frozen contract (client thread only): the smoke clouds, blast holes and bullet tunnels this client knows about.
 * Network receivers write it; {@link SmokeVolumes} and the renderers only read it. {@link #revision()} changes on every
 * write so readers can rebuild cached GPU data; {@link #densityRevision()} (BEAR WP5) changes only when the baked
 * densities can change (clouds and blast holes, not tunnels, which the passes evaluate analytically).
 * 冻结契约（仅客户端线程）：本客户端已知的烟团、爆炸空洞与子弹通道。网络接收端写入；{@link SmokeVolumes} 与各渲染器只读。
 * 每次写入都会改变 {@link #revision()}，读取方据此重建 GPU 缓存；{@link #densityRevision()}（BEAR WP5）只在烘焙浓度可能
 * 变化时改变（烟团与爆炸空洞；通道由各步骤解析计算，不在其中）。
 */
public final class SmokeClientState {
    private static final Map<Integer, SmokeCloudSnapshot> CLOUDS = new LinkedHashMap<>();
    private static final List<SmokeHole> HOLES = new ArrayList<>();
    /** Live tunnels, oldest first. / 存活通道，最早的在前。 */
    private static final List<SmokeHole> TUNNELS = new ArrayList<>();
    private static long revision;
    private static long densityRevision;

    private SmokeClientState() {
    }

    public static void put(SmokeCloudSnapshot cloud) {
        CLOUDS.put(cloud.cloudId(), cloud);
        revision++;
        densityRevision++;
    }

    public static void remove(int cloudId) {
        if (CLOUDS.remove(cloudId) != null) {
            revision++;
            densityRevision++;
        }
    }

    /** Adds a blast hole, or a tunnel ({@link SmokeHole#isTunnel()}) with no cap. / 加入爆炸空洞或（无上限的）通道。 */
    public static void addHole(SmokeHole hole) {
        if (hole.isTunnel()) {
            TUNNELS.add(hole);
        } else {
            HOLES.add(hole);
            densityRevision++;
        }
        revision++;
    }

    /**
     * Adds a tunnel, first dropping the oldest ones so at most {@code cap} remain (the newest shots always show).
     * 加入一条通道，先丢弃最早的使总数不超过 {@code cap}（最新的射击总能显示）。
     */
    public static void addTunnel(SmokeHole tunnel, int cap) {
        while (!TUNNELS.isEmpty() && TUNNELS.size() >= cap) {
            TUNNELS.remove(0);
            revision++;
        }
        if (cap > 0) {
            addHole(tunnel);
        }
    }

    public static void clear() {
        if (!CLOUDS.isEmpty() || !HOLES.isEmpty() || !TUNNELS.isEmpty()) {
            CLOUDS.clear();
            HOLES.clear();
            TUNNELS.clear();
            revision++;
            densityRevision++;
        }
    }

    /** Drops clouds, holes and tunnels that can no longer show anything. / 丢弃已不可能显示的烟团、空洞与通道。 */
    public static void prune(long worldTime) {
        boolean dense = CLOUDS.values().removeIf(cloud -> SmokeTimeline.expired(worldTime - cloud.spawnTime()));
        dense |= HOLES.removeIf(hole -> hole.expired(worldTime));
        boolean tunnels = TUNNELS.removeIf(tunnel -> tunnel.expired(worldTime));
        if (dense || tunnels) {
            revision++;
        }
        if (dense) {
            densityRevision++;
        }
    }

    public static Collection<SmokeCloudSnapshot> clouds() {
        return Collections.unmodifiableCollection(CLOUDS.values());
    }

    /** Blast (sphere) holes, baked into the volumes. / 爆炸（球形）空洞，烘焙进浓度体。 */
    public static List<SmokeHole> holes() {
        return Collections.unmodifiableList(HOLES);
    }

    /** Bullet tunnels (capsules), oldest first, evaluated analytically. / 子弹通道（胶囊），最早的在前，解析计算。 */
    public static List<SmokeHole> tunnels() {
        return Collections.unmodifiableList(TUNNELS);
    }

    public static boolean isEmpty() {
        return CLOUDS.isEmpty();
    }

    public static long revision() {
        return revision;
    }

    /** Changes whenever clouds or blast holes change. / 烟团或爆炸空洞变化时改变。 */
    public static long densityRevision() {
        return densityRevision;
    }
}
