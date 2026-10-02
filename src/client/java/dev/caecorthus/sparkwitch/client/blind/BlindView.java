package dev.caecorthus.sparkwitch.client.blind;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Stable client contract: the single predicate every Blind client feature (black screen, line art, HUD gates, sound
 * capture, keys) gates on. True when, on a confirmed SparkWitch server, the local player's REAL synced Wathe role
 * ({@code getRole}, never the Black Raven {@code isRole} overlay) is the Blind in a running round and the player is
 * either playing and alive in Wathe's sense (not marked dead) or swallowed by a Taotie (C7). The camera is NOT part of
 * the rule: a living Blind whose camera sits elsewhere (SparkTraits Last Stand pending or Depression fake death make it
 * a spectator watching its body) keeps the view and every gate on, and the echo view paints it black. Only a real
 * Wathe death turns it off. Re-evaluated every call, so recruitment, death and round end turn the view off without a
 * listener; the checks run cheapest first, so every non-Blind client leaves at the role lookup.
 * 稳定客户端契约：所有盲人客户端功能（黑屏、线稿、HUD 闸门、声音采集、按键）共用的唯一判定。在已确认的 SparkWitch
 * 服务器上，本地玩家同步的真实 Wathe 职业（{@code getRole}，从不使用黑羽鸦 {@code isRole} 覆盖层）为盲人且对局进行中，
 * 并且玩家按 Wathe 的定义仍在参与且存活（未被标记死亡），或已被饕餮吞下（C7）时为真。镜头不属于该规则：
 * 镜头在别处的存活盲人（SparkTraits 最后一搏待定或抑郁假死会让其成为看着自己身体的旁观者）仍保持视图与所有闸门开启，
 * 回声视图将其画成全黑。只有 Wathe 的真实死亡才会关闭它。每次调用都重新计算，因此招募、死亡与回合结束都会自动关闭视图，
 * 无需监听器；判断按开销从低到高执行，所有非盲人客户端在职业查询处即返回。
 */
public final class BlindView {
    private BlindView() {
    }

    public static boolean isActive(@Nullable MinecraftClient client) {
        // Gates call this hundreds of times per frame: bail out on the cheapest facts first.
        // 闸门每帧调用数百次：先用开销最小的条件提前返回。
        if (client == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (!game.isRunning() || !BlindRules.isBlind(game.getRole(player))) {
            return false;
        }
        boolean playingAndAlive = GameFunctions.isPlayerPlayingAndAlive(player);
        // The swallow lookup only matters for a Blind that is not playing and alive. / 只有不在参与存活状态时才查询是否被吞。
        return shouldBeBlind(true, true, playingAndAlive,
                !playingAndAlive && NoellesTaotieSeekerBridge.isSwallowed(player));
    }

    /** Pure rule behind {@link #isActive}. / {@link #isActive} 背后的纯规则。 */
    public static boolean shouldBeBlind(boolean running, boolean realBlind, boolean playingAndAlive,
                                        boolean swallowed) {
        return running && realBlind && (swallowed || playingAndAlive);
    }
}
