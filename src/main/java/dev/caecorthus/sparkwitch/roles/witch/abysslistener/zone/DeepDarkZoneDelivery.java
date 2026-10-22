package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import net.minecraft.util.math.ChunkSectionPos;
import org.jetbrains.annotations.Nullable;

/**
 * Pure per-section delivery bookkeeping of the live fake looks of one world, generic over the player type so it stays
 * testable. It mirrors the cells the sync layer has shown (and not yet sent back to real) grouped by chunk section, and
 * remembers, per section, which trackers hold that section's looks. A tracker the section has not seen yet (late
 * joiner, re-tracked chunk, dimension return) gets the whole section at once and stays pending: the server cannot see
 * whether its chunk data had already landed, so it gets the section again on every confirm tick (the heal sweep) and
 * becomes a holder only at the first confirm tick at least {@code confirmDelayTicks} after its first send. A section
 * that gained cells, or a periodic full resend, goes to every tracker. A holder or pending tracker that stops tracking
 * is forgotten. Every send carries only the cells whose world state still matches their landing snapshot. Players are
 * compared by identity: a reconnect or respawn is a new player object.
 * 单个世界存活假外观的按区块段投递记录（纯逻辑，对玩家类型泛型以便测试）。它镜像同步层已显示（且尚未发回真实状态）的格子，
 * 按区块段分组，并按区块段记住哪些追踪者持有该段外观。该段尚未见过的追踪者（后加入者、重新追踪的区块、回到本维度者）会立即
 * 收到整段并处于待确认状态：服务端无法得知其区块数据是否已先到达，因此它在每个确认刻（补发扫描）都会再次收到该段，并在首次发送
 * 至少 {@code confirmDelayTicks} 刻后的第一个确认刻才成为持有者。新增格子的区块段或周期性全量补发会发给所有追踪者。停止追踪的
 * 持有者或待确认者会被遗忘。每次发送只携带世界状态仍与落地快照一致的格子。玩家按对象身份比较：重连或重生会得到新的玩家对象。
 */
final class DeepDarkZoneDelivery<P> {
    private final Long2ObjectOpenHashMap<Section<P>> sections = new Long2ObjectOpenHashMap<>();
    private final int confirmDelayTicks;

    DeepDarkZoneDelivery(int confirmDelayTicks) {
        this.confirmDelayTicks = confirmDelayTicks;
    }

    /** Receives one section's fake look for some players. / 接收某些玩家应收到的整段假外观。 */
    @FunctionalInterface
    interface Sender<P> {
        void send(long section, LongList positions, Collection<P> recipients);
    }

    private static final class Section<P> {
        private final LongOpenHashSet shown = new LongOpenHashSet();
        private final ReferenceOpenHashSet<P> held = new ReferenceOpenHashSet<>();
        /** Pending trackers and the tick of their first send. / 待确认的追踪者及其首次发送的时刻。 */
        private final Reference2LongOpenHashMap<P> pending = new Reference2LongOpenHashMap<>();
        private boolean changed;
    }

    /** Cells now shown as fake (marks their sections for every tracker). / 现在显示为假外观的格子。 */
    void show(LongList positions) {
        for (int i = 0; i < positions.size(); i++) {
            long pos = positions.getLong(i);
            Section<P> section = sections.computeIfAbsent(ChunkSectionPos.fromBlockPos(pos), ignored -> new Section<>());
            section.shown.add(pos);
            section.changed = true;
        }
    }

    /** Cells sent back to their real state (restored, dropped, stale). / 已发回真实状态的格子（恢复、丢弃、过期）。 */
    void hide(LongList positions) {
        for (int i = 0; i < positions.size(); i++) {
            long pos = positions.getLong(i);
            long key = ChunkSectionPos.fromBlockPos(pos);
            Section<P> section = sections.get(key);
            if (section != null && section.shown.remove(pos) && section.shown.isEmpty()) {
                sections.remove(key);
            }
        }
    }

    boolean isEmpty() {
        return sections.isEmpty();
    }

    /**
     * Sends each section's look to the trackers that need it: newcomers, pending trackers on a {@code confirmTick},
     * and every tracker when the section gained cells or on {@code resendAll} (client-side overwrites such as
     * block-break prediction or interact resends). {@code stillValid} filters out cells whose real block changed since
     * landing and that the heal sweep has not dropped yet.
     * 把每个区块段的外观发给需要的追踪者：新追踪者、确认刻时的待确认者，以及区块段新增格子或 {@code resendAll} 时的所有追踪者
     * （覆盖客户端自行改写，例如破坏预测或交互回发）。{@code stillValid} 会过滤掉落地后真实方块已改变、但补发扫描尚未丢弃的格子。
     */
    void deliver(long now, boolean confirmTick, boolean resendAll, LongFunction<? extends Collection<P>> trackers,
                 LongPredicate stillValid, Sender<P> sender) {
        ObjectIterator<Long2ObjectMap.Entry<Section<P>>> iterator = sections.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<Section<P>> entry = iterator.next();
            Section<P> section = entry.getValue();
            Collection<P> current = trackers.apply(entry.getLongKey());
            List<P> recipients = null;
            int present = 0;
            for (P player : current) {
                present++;
                if (section.held.contains(player)) {
                    continue;
                }
                if (!section.pending.containsKey(player)) {
                    section.pending.put(player, now);
                    recipients = add(recipients, player);
                } else if (confirmTick) {
                    recipients = add(recipients, player);
                    if (now - section.pending.getLong(player) >= confirmDelayTicks) {
                        section.pending.removeLong(player);
                        section.held.add(player);
                    }
                }
            }
            // Holders and pending trackers that stopped tracking are forgotten, so a re-tracked chunk starts over.
            // 已停止追踪的持有者与待确认者会被遗忘，因此重新追踪的区块会从头开始。
            if (present != section.held.size() + section.pending.size()) {
                ReferenceOpenHashSet<P> tracking = new ReferenceOpenHashSet<>(current);
                section.held.retainAll(tracking);
                section.pending.keySet().retainAll(tracking);
            }
            Collection<P> targets = resendAll || section.changed ? current : recipients;
            section.changed = false;
            if (targets == null || targets.isEmpty()) {
                continue;
            }
            LongArrayList positions = new LongArrayList(section.shown.size());
            LongIterator cells = section.shown.iterator();
            while (cells.hasNext()) {
                long pos = cells.nextLong();
                if (stillValid.test(pos)) {
                    positions.add(pos);
                }
            }
            if (!positions.isEmpty()) {
                sender.send(entry.getLongKey(), positions, targets);
            }
        }
    }

    private static <P> List<P> add(@Nullable List<P> list, P player) {
        List<P> result = list == null ? new ArrayList<>(2) : list;
        result.add(player);
        return result;
    }
}
