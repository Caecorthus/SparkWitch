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
 * Frozen contract (client thread only): the smoke clouds and holes this client knows about. Network receivers write
 * it; {@link SmokeVolumes} and the renderers only read it. {@link #revision()} changes on every write so readers can
 * rebuild cached GPU data.
 * 冻结契约（仅客户端线程）：本客户端已知的烟团与空洞。网络接收端写入；{@link SmokeVolumes} 与各渲染器只读。
 * 每次写入都会改变 {@link #revision()}，读取方据此重建 GPU 缓存。
 */
public final class SmokeClientState {
    private static final Map<Integer, SmokeCloudSnapshot> CLOUDS = new LinkedHashMap<>();
    private static final List<SmokeHole> HOLES = new ArrayList<>();
    private static long revision;

    private SmokeClientState() {
    }

    public static void put(SmokeCloudSnapshot cloud) {
        CLOUDS.put(cloud.cloudId(), cloud);
        revision++;
    }

    public static void remove(int cloudId) {
        if (CLOUDS.remove(cloudId) != null) {
            revision++;
        }
    }

    public static void addHole(SmokeHole hole) {
        HOLES.add(hole);
        revision++;
    }

    public static void clear() {
        if (!CLOUDS.isEmpty() || !HOLES.isEmpty()) {
            CLOUDS.clear();
            HOLES.clear();
            revision++;
        }
    }

    /** Drops clouds and holes that can no longer show anything. / 丢弃已不可能显示的烟团与空洞。 */
    public static void prune(long worldTime) {
        boolean changed = CLOUDS.values().removeIf(cloud -> SmokeTimeline.expired(worldTime - cloud.spawnTime()));
        changed |= HOLES.removeIf(hole -> hole.expired(worldTime));
        if (changed) {
            revision++;
        }
    }

    public static Collection<SmokeCloudSnapshot> clouds() {
        return Collections.unmodifiableCollection(CLOUDS.values());
    }

    public static List<SmokeHole> holes() {
        return Collections.unmodifiableList(HOLES);
    }

    public static boolean isEmpty() {
        return CLOUDS.isEmpty();
    }

    public static long revision() {
        return revision;
    }
}
