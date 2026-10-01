package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongFunction;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory Deep Dark Zone registry of one world (design B), keyed by packed block positions and generic over the
 * block-state type so it stays testable. It is never persisted: the server world never changes, so a crash leaves
 * nothing behind. Each cell keeps its fake look, the real state snapshotted at landing and one window per owning
 * zone; it counts as converted while any window is active, so overlapping zones form a union and the cell restores
 * only when its last window ends. A cell whose real state no longer matches its snapshot is dropped (compare-and-swap):
 * a legitimate server write always wins over the overlay.
 * 单个世界的深暗领域内存登记表（方案 B），以打包的方块坐标为键，对方块状态类型泛型以便测试。从不持久化：服务端世界从不改变，
 * 崩服不会留下任何东西。每格保存假外观、落地时快照的真实状态，以及每个覆盖它的领域各一个时间窗；任一时间窗生效时即算已转换，
 * 因此重叠领域取并集，最后一个时间窗结束时才恢复。真实状态与快照不一致的格子会被丢弃（比较并交换）：服务端的合法写入
 * 永远优先于假外观。
 */
public final class DeepDarkZoneState<S> {
    private final Long2ObjectOpenHashMap<Cell<S>> cells = new Long2ObjectOpenHashMap<>();
    /** Ticks at which a cell's visibility may change, oldest first. / 格子可见性可能变化的时刻，按时间排序。 */
    private final Long2ObjectRBTreeMap<LongArrayList> due = new Long2ObjectRBTreeMap<>();
    private final Long2ObjectLinkedOpenHashMap<Zone> zones = new Long2ObjectLinkedOpenHashMap<>();
    private long nextZoneId = 1L;

    /** One thrown flask's zone; {@code owner} is the thrower's UUID when known. / 一个孢瓶的领域。 */
    public record Zone(long id, @Nullable UUID owner, long centerPos, long landingTick, long endTick) {
    }

    /** One block a new zone converts, with its snapshot and window. / 新领域要转换的一个方块。 */
    public record Claim<S>(long pos, S fake, S original, long convertTick, long restoreTick) {
    }

    /** Visibility changes of one tick: cells to show as fake and cells to send back to real. / 本刻的可见性变化。 */
    public record Changes(LongList shown, LongList hidden) {
        public boolean isEmpty() {
            return shown.isEmpty() && hidden.isEmpty();
        }
    }

    /** Heal sweep result: live fakes to resend and cells dropped because the world changed. / 补发结果。 */
    public record Heal(LongList resend, LongList dropped) {
    }

    private record Window(long zoneId, @Nullable UUID owner, long convertTick, long restoreTick) {
        boolean activeAt(long now) {
            return convertTick <= now && now < restoreTick;
        }
    }

    private static final class Cell<S> {
        private final S fake;
        private final S original;
        private final List<Window> windows = new ArrayList<>(1);
        private boolean shown;

        private Cell(S fake, S original) {
            this.fake = fake;
            this.original = original;
        }

        private boolean activeAt(long now) {
            for (Window window : windows) {
                if (window.activeAt(now)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Registers a zone and its claims. A cell already claimed with the same snapshot gains one more window and keeps
     * its look; a cell whose older snapshot no longer matches is stale and is replaced. The returned positions were
     * showing a stale fake and must be sent back to their real state now.
     * 登记一个领域及其方块。已被认领且快照相同的格子增加一个时间窗并保留外观；旧快照已不一致的格子视为过期并被替换。
     * 返回的位置正显示过期的假外观，必须立即发回真实状态。
     */
    public LongList addZone(@Nullable UUID owner, long centerPos, long landingTick, long endTick,
                            Collection<Claim<S>> claims) {
        Zone zone = new Zone(nextZoneId++, owner, centerPos, landingTick, endTick);
        zones.put(zone.id(), zone);
        LongArrayList stale = new LongArrayList();
        for (Claim<S> claim : claims) {
            if (claim.restoreTick() <= claim.convertTick()) {
                continue;
            }
            Cell<S> cell = cells.get(claim.pos());
            if (cell != null && !Objects.equals(cell.original, claim.original())) {
                if (cell.shown) {
                    stale.add(claim.pos());
                }
                cell = null;
            }
            if (cell == null) {
                cell = new Cell<>(claim.fake(), claim.original());
                cells.put(claim.pos(), cell);
            }
            cell.windows.add(new Window(zone.id(), owner, claim.convertTick(), claim.restoreTick()));
            schedule(claim.convertTick(), claim.pos());
            schedule(claim.restoreTick(), claim.pos());
        }
        return stale;
    }

    /** True while any window covering the cell is active. / 覆盖该格的任一时间窗生效时为 true。 */
    public boolean isConverted(long pos, long now) {
        Cell<S> cell = cells.get(pos);
        return cell != null && cell.activeAt(now);
    }

    /** Owners of the zones currently converting the cell, in claim order, without duplicates. / 当前转换该格的领域主人。 */
    public List<UUID> ownersAt(long pos, long now) {
        Cell<S> cell = cells.get(pos);
        if (cell == null) {
            return List.of();
        }
        List<UUID> owners = new ArrayList<>(cell.windows.size());
        for (Window window : cell.windows) {
            if (window.activeAt(now) && window.owner() != null && !owners.contains(window.owner())) {
                owners.add(window.owner());
            }
        }
        return owners;
    }

    public boolean hasZones() {
        return !zones.isEmpty();
    }

    public boolean isEmpty() {
        return zones.isEmpty() && cells.isEmpty();
    }

    public Collection<Zone> zones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    /** The fake look of a registered cell, or null. / 已登记格子的假外观，未登记时为 null。 */
    public @Nullable S fakeAt(long pos) {
        Cell<S> cell = cells.get(pos);
        return cell == null ? null : cell.fake;
    }

    /**
     * Applies every due window edge up to {@code now} and ends finished zones. A cell about to be shown is first
     * compared with the world ({@code realState}; null means unknown, e.g. an unloaded chunk) and dropped when the
     * real block changed since the snapshot, so it is never shown.
     * 处理截至 {@code now} 的所有时间窗边界并结束已完成的领域。即将显示的格子会先与世界比较（{@code realState}；
     * null 表示未知，例如区块未加载），若真实方块自快照后已改变则丢弃，永不显示。
     */
    public Changes advance(long now, LongFunction<S> realState) {
        LongArrayList shown = new LongArrayList();
        LongArrayList hidden = new LongArrayList();
        LongOpenHashSet touched = new LongOpenHashSet();
        while (!due.isEmpty() && due.firstLongKey() <= now) {
            LongArrayList positions = due.remove(due.firstLongKey());
            for (int i = 0; i < positions.size(); i++) {
                long pos = positions.getLong(i);
                if (touched.add(pos)) {
                    evaluate(pos, now, realState, shown, hidden);
                }
            }
        }
        zones.values().removeIf(zone -> zone.endTick() <= now);
        return new Changes(shown, hidden);
    }

    /**
     * Heal sweep: every shown cell whose world state still equals its snapshot is resent; one that changed is dropped
     * (stopping both the look and the effect) and must be sent back to its real state.
     * 补发扫描：世界状态仍与快照一致的已显示格子会被重发；已改变的格子被丢弃（外观与效果同时停止），并须发回真实状态。
     */
    public Heal heal(LongFunction<S> realState) {
        LongArrayList resend = new LongArrayList();
        LongArrayList dropped = new LongArrayList();
        ObjectIterator<Long2ObjectMap.Entry<Cell<S>>> iterator = cells.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<Cell<S>> entry = iterator.next();
            Cell<S> cell = entry.getValue();
            if (!cell.shown) {
                continue;
            }
            long pos = entry.getLongKey();
            S real = realState.apply(pos);
            if (real != null && !Objects.equals(real, cell.original)) {
                iterator.remove();
                dropped.add(pos);
            } else {
                resend.add(pos);
            }
        }
        return new Heal(resend, dropped);
    }

    /** Drops everything; returns the cells that were showing a fake. / 清空一切；返回正在显示假外观的格子。 */
    public LongList clear() {
        LongArrayList shown = new LongArrayList();
        for (Long2ObjectMap.Entry<Cell<S>> entry : cells.long2ObjectEntrySet()) {
            if (entry.getValue().shown) {
                shown.add(entry.getLongKey());
            }
        }
        cells.clear();
        due.clear();
        zones.clear();
        return shown;
    }

    private void evaluate(long pos, long now, LongFunction<S> realState, LongList shown, LongList hidden) {
        Cell<S> cell = cells.get(pos);
        if (cell == null) {
            return;
        }
        cell.windows.removeIf(window -> window.restoreTick() <= now);
        boolean active = cell.activeAt(now);
        if (active && !cell.shown) {
            S real = realState.apply(pos);
            if (real != null && !Objects.equals(real, cell.original)) {
                cells.remove(pos);
                return;
            }
            cell.shown = true;
            shown.add(pos);
        } else if (!active && cell.shown) {
            cell.shown = false;
            hidden.add(pos);
        }
        if (cell.windows.isEmpty()) {
            cells.remove(pos);
        }
    }

    private void schedule(long tick, long pos) {
        LongArrayList positions = due.get(tick);
        if (positions == null) {
            positions = new LongArrayList();
            due.put(tick, positions);
        }
        positions.add(pos);
    }
}
