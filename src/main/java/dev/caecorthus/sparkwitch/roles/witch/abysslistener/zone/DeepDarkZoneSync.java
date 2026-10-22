package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import java.util.Collection;
import java.util.List;
import java.util.function.LongFunction;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import org.jetbrains.annotations.Nullable;

/**
 * Sends Deep Dark Zone looks to clients with plain vanilla {@code ChunkDeltaUpdateS2CPacket}s, one per chunk section,
 * to the players tracking that chunk. Client authority: these packets only change what clients draw; the server world
 * and every server reader keep the real blocks, and no client code is involved.
 * 用原版 {@code ChunkDeltaUpdateS2CPacket} 把深暗领域外观发给客户端：每个区块段一个包，只发给追踪该区块的玩家。
 * 客户端权限：这些包只改变客户端绘制的内容；服务端世界及所有服务端读取方仍看到真实方块，也不涉及任何客户端代码。
 */
final class DeepDarkZoneSync {
    private DeepDarkZoneSync() {
    }

    /** Sends each position's current real state (restore, drop, clear). / 发送每个位置当前的真实状态（恢复、丢弃、清空）。 */
    static void sendReal(ServerWorld world, LongList positions) {
        send(world, positions, pos -> world.getBlockState(BlockPos.fromLong(pos)));
    }

    /**
     * Players tracking the chunk of a packed section position; nobody before the chunk is post-processed. Vanilla
     * counts a player in range as tracking unless the chunk is queued for it, and queues a chunk only once it is
     * post-processed, so before that a "tracker" holds no chunk data and a delta would be dropped.
     * 追踪该区块段所在区块的玩家；区块完成后处理之前视为无人追踪。原版只要玩家在范围内且该区块不在其发送队列中就算追踪，而区块
     * 只有在完成后处理后才会入队，因此在那之前的“追踪者”并没有区块数据，增量包会被丢弃。
     */
    static Collection<ServerPlayerEntity> trackers(ServerWorld world, long section) {
        ChunkPos chunk = ChunkSectionPos.from(section).toChunkPos();
        if (world.getChunkManager().chunkLoadingManager.getPostProcessedChunk(chunk.toLong()) == null) {
            return List.of();
        }
        return PlayerLookup.tracking(world, chunk);
    }

    /**
     * Sends one section's fake looks to the given players only (the delivery bookkeeping picks them among the
     * section's trackers). / 只把一个区块段的假外观发给给定玩家（由投递记录从该段追踪者中挑选）。
     */
    static void sendFakeTo(ServerWorld world, long section, LongList positions, LongFunction<BlockState> fakeState,
                           Collection<ServerPlayerEntity> recipients) {
        ChunkDeltaUpdateS2CPacket packet = packet(world, ChunkSectionPos.from(section), positions, fakeState);
        if (packet == null) {
            return;
        }
        for (ServerPlayerEntity player : recipients) {
            player.networkHandler.sendPacket(packet);
        }
    }

    private static void send(ServerWorld world, LongList positions, LongFunction<BlockState> stateOf) {
        if (positions.isEmpty()) {
            return;
        }
        for (Long2ObjectMap.Entry<LongArrayList> group
                : DeepDarkZoneSections.groupBySection(positions).long2ObjectEntrySet()) {
            ChunkSectionPos sectionPos = ChunkSectionPos.from(group.getLongKey());
            // Tracked chunks are loaded, so the state reads below never load a chunk.
            // 被追踪的区块必然已加载，因此下面的状态读取不会加载区块。
            Collection<ServerPlayerEntity> trackers = PlayerLookup.tracking(world, sectionPos.toChunkPos());
            if (trackers.isEmpty()) {
                continue;
            }
            ChunkDeltaUpdateS2CPacket packet = packet(world, sectionPos, group.getValue(), stateOf);
            if (packet == null) {
                continue;
            }
            for (ServerPlayerEntity player : trackers) {
                player.networkHandler.sendPacket(packet);
            }
        }
    }

    /**
     * The vanilla packet reads its states from a section, so a scratch section carries only the changed cells; the
     * packet copies them at construction.
     * 原版数据包从区块段读取状态，因此用一个临时区块段只承载变化的格子；数据包在构造时就会复制它们。
     */
    private static @Nullable ChunkDeltaUpdateS2CPacket packet(ServerWorld world, ChunkSectionPos sectionPos,
                                                              LongList positions, LongFunction<BlockState> stateOf) {
        ChunkSection scratch = new ChunkSection(world.getRegistryManager().get(RegistryKeys.BIOME));
        ShortOpenHashSet locals = new ShortOpenHashSet();
        for (int i = 0; i < positions.size(); i++) {
            long pos = positions.getLong(i);
            BlockState state = stateOf.apply(pos);
            if (state == null) {
                continue;
            }
            short local = ChunkSectionPos.packLocal(BlockPos.fromLong(pos));
            locals.add(local);
            scratch.setBlockState(ChunkSectionPos.unpackLocalX(local), ChunkSectionPos.unpackLocalY(local),
                    ChunkSectionPos.unpackLocalZ(local), state, false);
        }
        return locals.isEmpty() ? null : new ChunkDeltaUpdateS2CPacket(sectionPos, locals, scratch);
    }
}
