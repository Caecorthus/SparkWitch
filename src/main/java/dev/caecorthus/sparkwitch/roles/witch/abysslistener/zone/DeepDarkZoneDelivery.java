package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.LongFunction;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * Pure per-section delivery bookkeeping of the live fake looks of one world, generic over the player type so it stays
 * testable. It mirrors the cells the sync layer has shown (and not yet sent back to real) grouped by chunk section, and
 * remembers, per section, which trackers already hold that section's looks. Each tick a section goes in full to its
 * newcomers only (late joiners, re-tracked chunks, dimension returns); a section that gained cells this tick, or a
 * periodic full resend, goes to every tracker. A holder that stops tracking is forgotten, so it gets the section again
 * when it tracks it again. Players are compared by identity: a reconnect or respawn is a new player object.
 * 单个世界存活假外观的按区块段投递记录（纯逻辑，对玩家类型泛型以便测试）。它镜像同步层已显示（且尚未发回真实状态）的格子，
 * 按区块段分组，并按区块段记住哪些追踪者已持有该段外观。每刻只把整段发给新追踪者（后加入者、重新追踪的区块、回到本维度者）；
 * 本刻新增格子的区块段或周期性全量补发会发给所有追踪者。不再追踪的持有者会被遗忘，再次追踪时会重新收到该段。
 * 玩家按对象身份比较：重连或重生会得到新的玩家对象。
 */
final class DeepDarkZoneDelivery<P> {
    private final Long2ObjectOpenHashMap<Section<P>> sections = new Long2ObjectOpenHashMap<>();

    /** Receives one section's full fake look for some players. / 接收某些玩家应收到的整段假外观。 */
    @FunctionalInterface
    interface Sender<P> {
        void send(long section, LongList positions, Collection<P> recipients);
    }

    private static final class Section<P> {
        private final LongOpenHashSet shown = new LongOpenHashSet();
        private final ReferenceOpenHashSet<P> delivered = new ReferenceOpenHashSet<>();
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
     * Sends each section's full look to the trackers that need it and records the current trackers as holders.
     * {@code resendAll} sends every section to all its trackers (client-side overwrites such as block-break
     * prediction or interact resends).
     * 把每个区块段的完整外观发给需要的追踪者，并把当前追踪者记为持有者。{@code resendAll} 会把每段发给所有追踪者
     * （覆盖客户端自行改写，例如破坏预测或交互回发）。
     */
    void deliver(LongFunction<? extends Collection<P>> trackers, boolean resendAll, Sender<P> sender) {
        ObjectIterator<Long2ObjectMap.Entry<Section<P>>> iterator = sections.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<Section<P>> entry = iterator.next();
            Section<P> section = entry.getValue();
            Collection<P> current = trackers.apply(entry.getLongKey());
            List<P> newcomers = null;
            int kept = 0;
            for (P player : current) {
                if (section.delivered.contains(player)) {
                    kept++;
                } else {
                    if (newcomers == null) {
                        newcomers = new ArrayList<>(2);
                    }
                    newcomers.add(player);
                }
            }
            // Holders that stopped tracking are forgotten, so a re-tracked chunk gets its look again.
            // 已停止追踪的持有者会被遗忘，因此重新追踪的区块会再次收到外观。
            if (newcomers != null || kept != section.delivered.size()) {
                section.delivered.clear();
                section.delivered.addAll(current);
            }
            Collection<P> recipients = resendAll || section.changed ? current : newcomers;
            section.changed = false;
            if (recipients != null && !recipients.isEmpty()) {
                sender.send(entry.getLongKey(), new LongArrayList(section.shown), recipients);
            }
        }
    }
}
