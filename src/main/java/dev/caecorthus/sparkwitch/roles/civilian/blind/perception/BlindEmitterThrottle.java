package dev.caecorthus.sparkwitch.roles.civilian.blind.perception;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/**
 * One Blind's per-emitter pulse throttle: at most one pulse per emitter in {@link BlindRules#EMITTER_THROTTLE_TICKS}.
 * An emitter is an entity id (a player, or a moving object such as a projectile or car) or a packed block position (a
 * positional sound with nobody near it). One-shots pass and still stamp the emitter. Times are the Blind's world ticks;
 * a clock that ran backwards (world change) never blocks. Server thread only; pure.
 * 单个盲人的发声者脉冲节流：每个发声者在 {@link BlindRules#EMITTER_THROTTLE_TICKS} 内至多一个脉冲。发声者为实体 id
 * （玩家，或投射物、小车等移动物体）或打包的方块坐标（附近无人的定点声音）。一次性声音直接放行，但仍记录时间。时间为
 * 盲人所在世界的刻；时钟倒退（换世界）时从不阻挡。仅服务端线程；纯状态。
 */
public final class BlindEmitterThrottle {
    private static final long NEVER = Long.MIN_VALUE;
    private final Int2LongOpenHashMap entities = new Int2LongOpenHashMap();
    private final Long2LongOpenHashMap blocks = new Long2LongOpenHashMap();

    public BlindEmitterThrottle() {
        entities.defaultReturnValue(NEVER);
        blocks.defaultReturnValue(NEVER);
    }

    public boolean tryEntity(int entityId, long now, boolean oneShot) {
        long last = entities.get(entityId);
        if (!oneShot && !elapsed(last, now)) {
            return false;
        }
        entities.put(entityId, now);
        return true;
    }

    public boolean tryBlock(long packedPos, long now, boolean oneShot) {
        long last = blocks.get(packedPos);
        if (!oneShot && !elapsed(last, now)) {
            return false;
        }
        blocks.put(packedPos, now);
        return true;
    }

    /** Forgets emitters whose window has passed. / 移除窗口已过的发声者。 */
    public void prune(long now) {
        for (var iterator = entities.int2LongEntrySet().fastIterator(); iterator.hasNext(); ) {
            if (elapsed(iterator.next().getLongValue(), now)) {
                iterator.remove();
            }
        }
        for (var iterator = blocks.long2LongEntrySet().fastIterator(); iterator.hasNext(); ) {
            if (elapsed(iterator.next().getLongValue(), now)) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        entities.clear();
        blocks.clear();
    }

    public int size() {
        return entities.size() + blocks.size();
    }

    static boolean elapsed(long last, long now) {
        return last == NEVER || now < last || now - last >= BlindRules.EMITTER_THROTTLE_TICKS;
    }
}
