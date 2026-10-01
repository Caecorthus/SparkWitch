package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * Pure grouping of changed cells by 16³ chunk section, so each wave sends one vanilla
 * {@code ChunkDeltaUpdateS2CPacket} per section (and to that section's chunk trackers only).
 * 按 16³ 区块段对变化的格子进行纯分组，使每一波对每个区块段只发送一个原版 {@code ChunkDeltaUpdateS2CPacket}
 * （且只发给追踪该区块的玩家）。
 */
public final class DeepDarkZoneSections {
    private DeepDarkZoneSections() {
    }

    /**
     * Packed block positions grouped by packed section position, in first-seen order; the packet's local-position
     * set removes any duplicate. / 按区块段分组，保持首次出现顺序；数据包的段内坐标集合会去除重复。
     */
    public static Long2ObjectLinkedOpenHashMap<LongArrayList> groupBySection(LongList positions) {
        Long2ObjectLinkedOpenHashMap<LongArrayList> groups = new Long2ObjectLinkedOpenHashMap<>();
        for (int i = 0; i < positions.size(); i++) {
            long pos = positions.getLong(i);
            long section = ChunkSectionPos.fromBlockPos(pos);
            LongArrayList group = groups.get(section);
            if (group == null) {
                group = new LongArrayList();
                groups.put(section, group);
            }
            group.add(pos);
        }
        return groups;
    }
}
