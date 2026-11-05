package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure per-world bookkeeping of live smoke clouds and holes: the caps, forced fades and expiry by {@link SmokeTimeline}.
 * A cloud counts toward the caps until its fade begins. When a new cloud would exceed {@link #MAX_PER_OWNER} for its
 * owner or {@link #MAX_PER_WORLD} for the world, the oldest counted cloud is forced into its fade at once: its snapshot
 * is replaced by one whose {@code spawnTime} is moved back so the fade starts now, with not-yet-arrived cells dropped
 * (it stops growing) and the open-sky flag cleared so the whole cloud thins over the same 3 s. Callers broadcast the
 * returned changes. Server thread only.
 * 纯粹的单世界烟团与空洞登记：上限、强制消散与按 {@link SmokeTimeline} 的过期。烟团在开始消散前都计入上限。新烟团会让
 * 某拥有者超过 {@link #MAX_PER_OWNER} 或世界超过 {@link #MAX_PER_WORLD} 时，最早的计数烟团立即被强制进入消散：其快照换成
 * {@code spawnTime} 前移、使消散从此刻开始的新快照；尚未到达的格被丢弃（停止扩散），露天标记被清除，使整团烟在同样的
 * 3 秒内一起变淡。调用方负责广播返回的变化。仅限服务端线程。
 */
final class SmokeCloudRegistry {
    /** Live clouds per owner (owner 2026-11-05 spike brief). / 每个拥有者的存活烟团数。 */
    static final int MAX_PER_OWNER = 3;
    /** Live clouds per world. / 每个世界的存活烟团数。 */
    static final int MAX_PER_WORLD = 8;
    /** Live holes per world; the oldest goes first. / 每个世界的存活空洞数；超出时先丢最早的。 */
    static final int MAX_HOLES = 32;

    /**
     * What a change means for clients: snapshots to resend (add or replace) and clouds to remove.
     * 一次变化对客户端的意义：需要重发（新增或替换）的快照与需要移除的烟团。
     */
    record Changes(List<SmokeCloudSnapshot> replaced, IntList removed) {
    }

    private static final class Cloud {
        SmokeCloudSnapshot snapshot;
        @Nullable
        final UUID owner;
        long fadeStart;
        long end;

        Cloud(SmokeCloudSnapshot snapshot, @Nullable UUID owner) {
            this.owner = owner;
            set(snapshot);
        }

        void set(SmokeCloudSnapshot snapshot) {
            this.snapshot = snapshot;
            this.fadeStart = fadeStart(snapshot);
            this.end = end(snapshot);
        }

        boolean counted(long now) {
            return now < fadeStart;
        }
    }

    /** Insertion order is spawn order, so the first counted match is the oldest. / 插入顺序即生成顺序，首个匹配即最早。 */
    private final Map<Integer, Cloud> clouds = new LinkedHashMap<>();
    private final List<SmokeHole> holes = new ArrayList<>();

    /** Adds a new cloud, forcing older ones into their fade when a cap is exceeded. / 加入新烟团，超出上限时强制旧烟团消散。 */
    Changes add(SmokeCloudSnapshot snapshot, @Nullable UUID owner, long now) {
        List<SmokeCloudSnapshot> replaced = new ArrayList<>();
        IntList removed = new IntArrayList();
        if (owner != null) {
            while (count(owner, now) >= MAX_PER_OWNER) {
                forceOldest(owner, now, replaced, removed);
            }
        }
        while (count(null, now) >= MAX_PER_WORLD) {
            forceOldest(null, now, replaced, removed);
        }
        Cloud previous = clouds.remove(snapshot.cloudId());
        if (previous != null) {
            removed.add(snapshot.cloudId());
        }
        clouds.put(snapshot.cloudId(), new Cloud(snapshot, owner));
        return new Changes(replaced, removed);
    }

    /** Drops ended clouds and holes; returns the removed cloud ids. / 丢弃已结束的烟团与空洞；返回被移除的烟团 id。 */
    IntList expire(long now) {
        IntList removed = new IntArrayList();
        for (Iterator<Map.Entry<Integer, Cloud>> it = clouds.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Cloud> entry = it.next();
            if (now >= entry.getValue().end) {
                removed.add(entry.getKey().intValue());
                it.remove();
            }
        }
        holes.removeIf(hole -> hole.expired(now));
        return removed;
    }

    /**
     * Records a hole when it overlaps a cloud that still shows smoke; a hole in empty air is dropped, so it can never
     * punch a cloud that bursts later. / 空洞与仍有烟的烟团相交时才记录；空气中的空洞被丢弃，因此不会影响之后才爆开的烟。
     */
    boolean addHole(SmokeHole hole, long now) {
        boolean overlaps = false;
        for (Cloud cloud : clouds.values()) {
            if (now < cloud.end && overlaps(cloud.snapshot, hole)) {
                overlaps = true;
                break;
            }
        }
        if (!overlaps) {
            return false;
        }
        if (holes.size() >= MAX_HOLES) {
            holes.remove(0);
        }
        holes.add(hole);
        return true;
    }

    Collection<SmokeCloudSnapshot> snapshots() {
        List<SmokeCloudSnapshot> list = new ArrayList<>(clouds.size());
        for (Cloud cloud : clouds.values()) {
            list.add(cloud.snapshot);
        }
        return list;
    }

    List<SmokeHole> holes() {
        return Collections.unmodifiableList(holes);
    }

    boolean isEmpty() {
        return clouds.isEmpty() && holes.isEmpty();
    }

    /**
     * Tick at which the cloud's (indoor) fade begins; an all-sky cloud fades at its shorter open-sky time.
     * 烟团（室内部分）开始消散的 tick；全部露天的烟团按较短的露天时间消散。
     */
    static long fadeStart(SmokeCloudSnapshot cloud) {
        return cloud.spawnTime() + SmokeTimeline.GROW_TICKS
                + (hasIndoorCell(cloud) ? SmokeTimeline.FULL_TICKS : SmokeTimeline.FULL_TICKS / 2);
    }

    /** Tick at which no cell shows smoke any more. / 所有格都不再有烟的 tick。 */
    static long end(SmokeCloudSnapshot cloud) {
        return hasIndoorCell(cloud)
                ? cloud.spawnTime() + SmokeTimeline.lifetimeTicks()
                : cloud.spawnTime() + SmokeTimeline.GROW_TICKS + SmokeTimeline.FULL_TICKS / 2
                + SmokeTimeline.FADE_TICKS / 2;
    }

    /**
     * The cloud forced to start its fade at {@code now}, or null when none of its cells still shows smoke. Kept cells:
     * arrived, and (open-sky ones) not yet past their own shorter fade start.
     * 在 {@code now} 被强制开始消散的烟团；没有格仍有烟时返回 null。保留的格：已到达，且（露天格）尚未越过自身较早的消散起点。
     */
    @Nullable
    static SmokeCloudSnapshot fadeNow(SmokeCloudSnapshot cloud, long now) {
        long age = now - cloud.spawnTime();
        byte[] cells = new byte[cloud.cells().length];
        boolean any = false;
        for (int i = 0; i < cells.length; i++) {
            if (!cloud.present(i)) {
                continue;
            }
            int arrival = cloud.arrivalTick(i);
            if (age <= arrival) {
                continue;
            }
            if (cloud.openSky(i) && age >= SmokeTimeline.GROW_TICKS + SmokeTimeline.FULL_TICKS / 2) {
                continue;
            }
            cells[i] = SmokeCloudSnapshot.encodeCell(arrival, false);
            any = true;
        }
        if (!any) {
            return null;
        }
        return new SmokeCloudSnapshot(cloud.cloudId(), cloud.minCellX(), cloud.minCellY(), cloud.minCellZ(),
                cloud.sizeX(), cloud.sizeY(), cloud.sizeZ(), cells,
                now - SmokeTimeline.GROW_TICKS - SmokeTimeline.FULL_TICKS);
    }

    private int count(@Nullable UUID owner, long now) {
        int count = 0;
        for (Cloud cloud : clouds.values()) {
            if (cloud.counted(now) && (owner == null || owner.equals(cloud.owner))) {
                count++;
            }
        }
        return count;
    }

    private void forceOldest(@Nullable UUID owner, long now, List<SmokeCloudSnapshot> replaced, IntList removed) {
        for (Iterator<Cloud> it = clouds.values().iterator(); it.hasNext(); ) {
            Cloud cloud = it.next();
            if (!cloud.counted(now) || owner != null && !owner.equals(cloud.owner)) {
                continue;
            }
            SmokeCloudSnapshot faded = fadeNow(cloud.snapshot, now);
            if (faded == null) {
                it.remove();
                removed.add(cloud.snapshot.cloudId());
                replaced.removeIf(snapshot -> snapshot.cloudId() == cloud.snapshot.cloudId());
            } else {
                cloud.set(faded);
                replaced.removeIf(snapshot -> snapshot.cloudId() == faded.cloudId());
                replaced.add(faded);
            }
            return;
        }
    }

    private static boolean hasIndoorCell(SmokeCloudSnapshot cloud) {
        for (int i = 0; i < cloud.cells().length; i++) {
            if (cloud.present(i) && !cloud.openSky(i)) {
                return true;
            }
        }
        return false;
    }

    /** Sphere against the cloud's box. / 球体与烟团盒子的相交检测。 */
    private static boolean overlaps(SmokeCloudSnapshot cloud, SmokeHole hole) {
        double size = SmokeCloudSnapshot.CELL_SIZE;
        double dx = Math.max(0.0, Math.max(cloud.minX() - hole.x(), hole.x() - (cloud.minX() + cloud.sizeX() * size)));
        double dy = Math.max(0.0, Math.max(cloud.minY() - hole.y(), hole.y() - (cloud.minY() + cloud.sizeY() * size)));
        double dz = Math.max(0.0, Math.max(cloud.minZ() - hole.z(), hole.z() - (cloud.minZ() + cloud.sizeZ() * size)));
        double radius = hole.radius();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
