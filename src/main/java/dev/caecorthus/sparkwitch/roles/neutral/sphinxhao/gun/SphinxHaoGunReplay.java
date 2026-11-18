package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Wathe replay line of a 「“手”枪」 shot; Wathe drops item uses without a registered formatter. The line names the
 * target only when a player took the fist; the kill itself is Wathe's own death line
 * ({@code replay.death.sparkwitch.sphinx_hao_fist.*}).
 * 「“手”枪」射击的 Wathe 回放文本；未注册格式化器的道具使用会被 Wathe 忽略。只有玩家挨了拳头时才写出目标；击杀本身是 Wathe
 * 自己的死亡文本（{@code replay.death.sparkwitch.sphinx_hao_fist.*}）。
 */
final class SphinxHaoGunReplay {
    static final String HIT_KEY = "replay.item_use.sparkwitch.sphinx_hao_hand_gun.hit";
    static final String MISS_KEY = "replay.item_use.sparkwitch.sphinx_hao_hand_gun.miss";
    private static boolean registered;

    private SphinxHaoGunReplay() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(SphinxHaoRules.HAND_GUN_ID, SphinxHaoGunReplay::format);
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                         ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), players);
        if (!data.getBoolean(SphinxHaoGunService.RECORD_HIT) || !data.containsUuid("target")) {
            return Text.translatable(MISS_KEY, actor);
        }
        return Text.translatable(HIT_KEY, actor, ReplayGenerator.formatPlayerName(data.getUuid("target"), players));
    }
}
