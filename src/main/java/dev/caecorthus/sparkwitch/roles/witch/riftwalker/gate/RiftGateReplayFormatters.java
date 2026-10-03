package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Wathe replay line for Rift Gate placements ({@code GameRecordManager.recordItemUse} with item
 * {@code sparkwitch:rift_gate}); Wathe drops item uses without a registered formatter. The line names the placer and
 * the gate number and appears only in the post-round replay. Malformed records return {@code null}, which Wathe skips.
 * 裂隙门放置的 Wathe 回放文本（物品 {@code sparkwitch:rift_gate} 的 {@code GameRecordManager.recordItemUse}）；
 * 未注册格式化器的道具使用会被 Wathe 丢弃。该行写出放置者与门编号，只在赛后回放中出现。数据不完整的记录返回 {@code null}，
 * Wathe 会跳过该行。
 */
public final class RiftGateReplayFormatters {
    /** Data keys written by {@link RiftGatePlacementService}. / 放置服务写入的数据键。 */
    static final String ACTION_KEY = "action";
    static final String PLACE_ACTION = "place";
    static final String GATE_NUMBER_KEY = "gate";
    static final String PLACE_KEY = "replay.item_use.sparkwitch.rift_gate.place";
    /** Wathe's own key for the recording player. / Wathe 自身写入的记录玩家键。 */
    static final String WATHE_ACTOR_KEY = "actor";

    private static boolean registered;

    private RiftGateReplayFormatters() {
    }

    /** Called once from {@link RiftGateLifecycle#register()}. / 仅由 {@link RiftGateLifecycle#register()} 调用一次。 */
    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(RiftwalkerRules.GATE_ITEM_ID, RiftGateReplayFormatters::formatGate);
    }

    /** (placer, gate number); only the place action renders. / （放置者、门编号）；只渲染放置动作。 */
    static @Nullable Text formatGate(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                     @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        if (!PLACE_ACTION.equals(data.getString(ACTION_KEY)) || !data.containsUuid(WATHE_ACTOR_KEY)
                || data.getInt(GATE_NUMBER_KEY) <= 0) {
            return null;
        }
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid(WATHE_ACTOR_KEY),
                ReplayGenerator.getPlayerInfoCache(match));
        return Text.translatable(PLACE_KEY, actor, data.getInt(GATE_NUMBER_KEY));
    }
}
