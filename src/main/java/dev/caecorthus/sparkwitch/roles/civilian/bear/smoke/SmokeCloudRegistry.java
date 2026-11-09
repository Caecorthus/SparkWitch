package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net.SmokeHoleS2CPayload;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
 * (it stops growing) and the open-sky flag cleared so the whole cloud thins over the same 3 s. Bullet tunnels (BEAR
 * WP5) are kept apart from blast holes, with their own clipping, merge, per-shooter budget and cap. Callers broadcast
 * the returned changes. Server thread only.
 * 纯粹的单世界烟团与空洞登记：上限、强制消散与按 {@link SmokeTimeline} 的过期。烟团在开始消散前都计入上限。新烟团会让
 * 某拥有者超过 {@link #MAX_PER_OWNER} 或世界超过 {@link #MAX_PER_WORLD} 时，最早的计数烟团立即被强制进入消散：其快照换成
 * {@code spawnTime} 前移、使消散从此刻开始的新快照；尚未到达的格被丢弃（停止扩散），露天标记被清除，使整团烟在同样的
 * 3 秒内一起变淡。子弹通道（BEAR WP5）与爆炸空洞分开登记，有各自的裁剪、合并、每射手预算与上限。调用方负责广播返回的
 * 变化。仅限服务端线程。
 */
final class SmokeCloudRegistry {
    /** Live clouds per owner (owner 2026-11-05 spike brief). / 每个拥有者的存活烟团数。 */
    static final int MAX_PER_OWNER = 3;
    /** Live clouds per world. / 每个世界的存活烟团数。 */
    static final int MAX_PER_WORLD = 8;
    /** Live holes per world; the oldest goes first. / 每个世界的存活空洞数；超出时先丢最早的。 */
    static final int MAX_HOLES = 32;
    /**
     * Live tunnels per world, counted apart from blast holes so gunfire never evicts one; the oldest goes first. Also
     * the most tunnels a GPU pass evaluates per pixel (client {@code SmokeVolumes.MAX_RENDER_TUNNELS}).
     * 每个世界的存活通道数，与爆炸空洞分开计数，枪声永远挤不掉爆炸空洞；超出时先丢最早的。也是 GPU 每像素最多计算的通道数
     * （客户端 {@code SmokeVolumes.MAX_RENDER_TUNNELS}）。
     */
    static final int MAX_TUNNELS = 16;
    /**
     * Per shooter (a shotgun fires many pellets in one tick): at most this many tunnel-carving rays per
     * {@link #TUNNEL_WINDOW_TICKS} (a ray through several clouds is still one); the rest are dropped.
     * 每名射手（霰弹枪一刻打出多颗弹丸）：每 {@link #TUNNEL_WINDOW_TICKS} 最多这么多条开出通道的射线（穿过多团烟的射线
     * 仍算一条），其余丢弃。
     */
    static final int TUNNELS_PER_SHOOTER = 4;
    static final int TUNNEL_WINDOW_TICKS = 10;
    /**
     * A new tunnel whose clipped axis stays within this share of a recent live tunnel's radius (carved at most
     * {@link #TUNNEL_MERGE_TICKS} ago, at least as wide) adds nothing visible and is merged into it (dropped, not
     * counted). / 新通道裁剪后的轴线若始终位于某条近期存活通道（至多 {@link #TUNNEL_MERGE_TICKS} 前开出、不比它窄）半径的
     * 这一比例之内，则看不出区别，并入该通道（丢弃且不计数）。
     */
    static final double TUNNEL_MERGE_SHARE = 0.5;
    static final int TUNNEL_MERGE_TICKS = 2;
    /** Clipped tunnels shorter than this are dropped. / 裁剪后短于此长度的通道被丢弃。 */
    static final double MIN_TUNNEL_LENGTH = 1.0E-3;

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
    /** Live tunnels, oldest first. / 存活通道，最早的在前。 */
    private final List<SmokeHole> tunnels = new ArrayList<>();
    /** Shooter key -> {window start tick, tunnels in it}. / 射手键 -> {窗口起点 tick, 窗口内通道数}。 */
    private final Map<Object, long[]> tunnelBudgets = new HashMap<>();

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
        tunnels.removeIf(tunnel -> tunnel.expired(now));
        tunnelBudgets.values().removeIf(budget -> now - budget[0] >= TUNNEL_WINDOW_TICKS);
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

    /**
     * The tunnel {@code (x0, y0, z0)} → {@code (x1, y1, z1)} of {@code radius} clipped to the live clouds it crosses:
     * one capsule per cloud box (grown by the radius plus {@link SmokeTunnels#CLIP_MARGIN}, so the visible smoke's
     * warped edge band is cut too), overlapping or touching ones joined while the joined axis stays within the wire's
     * {@link SmokeHoleS2CPayload#MAX_TUNNEL_LENGTH}, longer ones split. Ordered along the ray; empty when it crosses no
     * cloud or every piece is shorter than {@link #MIN_TUNNEL_LENGTH}.
     * 把半径 {@code radius} 的通道 {@code (x0, y0, z0)} → {@code (x1, y1, z1)} 裁剪到它穿过的存活烟团：每个烟团盒子（按半径加
     * {@link SmokeTunnels#CLIP_MARGIN} 外扩，使可见烟雾被扭曲的边缘带也被切开）一条胶囊；重叠或相接的在合并后轴线不超过线格式
     * {@link SmokeHoleS2CPayload#MAX_TUNNEL_LENGTH} 时合并，更长的拆开。按射线顺序排列；不穿过任何烟团或每段都短于
     * {@link #MIN_TUNNEL_LENGTH} 时为空。
     */
    List<SmokeHole> clipTunnel(double x0, double y0, double z0, double x1, double y1, double z1, float radius,
                               long now, int durationTicks) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length >= MIN_TUNNEL_LENGTH)) {
            return List.of();
        }
        double grow = radius + SmokeTunnels.CLIP_MARGIN;
        double size = SmokeCloudSnapshot.CELL_SIZE;
        List<double[]> spans = new ArrayList<>();
        double[] span = new double[2];
        for (Cloud cloud : clouds.values()) {
            SmokeCloudSnapshot box = cloud.snapshot;
            if (now < cloud.end && SmokeTunnels.clipToBox(x0, y0, z0, x1, y1, z1,
                    box.minX() - grow, box.minY() - grow, box.minZ() - grow,
                    box.minX() + box.sizeX() * size + grow, box.minY() + box.sizeY() * size + grow,
                    box.minZ() + box.sizeZ() * size + grow, span)) {
                spans.add(new double[]{span[0], span[1]});
            }
        }
        spans.sort((a, b) -> Double.compare(a[0], b[0]));
        // A block under the wire limit, so rounding never makes a piece the clients reject. / 比线格式上限少一格，舍入不会越限。
        double maxSpan = (SmokeHoleS2CPayload.MAX_TUNNEL_LENGTH - 1.0) / length;
        List<double[]> joined = new ArrayList<>();
        for (double[] next : spans) {
            double[] last = joined.isEmpty() ? null : joined.get(joined.size() - 1);
            if (last != null && next[0] <= last[1] && Math.max(last[1], next[1]) - last[0] <= maxSpan) {
                last[1] = Math.max(last[1], next[1]);
            } else {
                joined.add(next);
            }
        }
        List<SmokeHole> pieces = new ArrayList<>();
        for (double[] piece : joined) {
            int parts = (int) Math.ceil((piece[1] - piece[0]) / maxSpan);
            for (int part = 0; part < Math.max(1, parts); part++) {
                double from = piece[0] + (piece[1] - piece[0]) * part / Math.max(1, parts);
                double to = piece[0] + (piece[1] - piece[0]) * (part + 1) / Math.max(1, parts);
                if ((to - from) * length >= MIN_TUNNEL_LENGTH) {
                    pieces.add(new SmokeHole(x0 + dx * from, y0 + dy * from, z0 + dz * from, radius, now,
                            durationTicks, x0 + dx * to, y0 + dy * to, z0 + dz * to));
                }
            }
        }
        return pieces;
    }

    /**
     * Records the pieces of one shot ({@link #clipTunnel}) for {@code shooterKey}: pieces that merge into a recent,
     * at-least-as-wide live tunnel are dropped first; if any remain, the shot takes one unit of the shooter's budget
     * (refused when spent) and they are added, the oldest tunnels going at the cap. Returns the pieces clients must be
     * told about (empty when none).
     * 为 {@code shooterKey} 记录一次射击的各段（{@link #clipTunnel}）：先丢弃会并入近期、不比它窄的存活通道的段；若仍有剩余，
     * 这次射击消耗射手预算的一个单位（用完则拒绝）并加入它们，达到上限时丢弃最早的通道。返回需要通知客户端的段（没有时为空）。
     */
    List<SmokeHole> addTunnels(List<SmokeHole> pieces, Object shooterKey, long now) {
        List<SmokeHole> fresh = new ArrayList<>(pieces.size());
        for (SmokeHole piece : pieces) {
            if (!merges(piece, now)) {
                fresh.add(piece);
            }
        }
        if (fresh.isEmpty()) {
            return List.of();
        }
        long[] budget = tunnelBudgets.get(shooterKey);
        if (budget == null || now - budget[0] >= TUNNEL_WINDOW_TICKS) {
            budget = new long[]{now, 0L};
            tunnelBudgets.put(shooterKey, budget);
        }
        if (budget[1] >= TUNNELS_PER_SHOOTER) {
            return List.of();
        }
        budget[1]++;
        for (SmokeHole piece : fresh) {
            if (tunnels.size() >= MAX_TUNNELS) {
                tunnels.remove(0);
            }
            tunnels.add(piece);
        }
        return fresh;
    }

    /** One-piece {@link #addTunnels}: whether clients must be told. / 单段版本：是否需要通知客户端。 */
    boolean addTunnel(SmokeHole tunnel, Object shooterKey, long now) {
        return !addTunnels(List.of(tunnel), shooterKey, now).isEmpty();
    }

    private boolean merges(SmokeHole piece, long now) {
        for (SmokeHole live : tunnels) {
            if (now - live.startTime() <= TUNNEL_MERGE_TICKS && live.radius() >= piece.radius()
                    && !live.expired(now) && covers(live, piece)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rate-limit key of a shot with no known shooter: its origin, to the half block. / 射手未知时的限流键：射击起点，
     * 精确到半格。
     */
    static Object originKey(double x, double y, double z) {
        return new OriginKey((long) Math.floor(x * 2.0), (long) Math.floor(y * 2.0), (long) Math.floor(z * 2.0));
    }

    /** Half-block cell of a shot origin. / 射击起点所在的半格。 */
    private record OriginKey(long x, long y, long z) {
    }

    List<SmokeHole> tunnels() {
        return Collections.unmodifiableList(tunnels);
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
        return clouds.isEmpty() && holes.isEmpty() && tunnels.isEmpty();
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

    /** Both ends of {@code inner}'s axis lie within the merge share of {@code outer}'s radius. / 两端都在合并范围内。 */
    private static boolean covers(SmokeHole outer, SmokeHole inner) {
        double reach = outer.radius() * TUNNEL_MERGE_SHARE;
        return outer.distanceTo(inner.x(), inner.y(), inner.z()) <= reach
                && outer.distanceTo(inner.endX(), inner.endY(), inner.endZ()) <= reach;
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
