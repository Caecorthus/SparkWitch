package dev.caecorthus.sparkwitch.roles.civilian.blind;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.UUID;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Stable shared predicates for every Blind service. The role check reads Wathe's REAL role ({@code getRole}), never
 * the Black Raven acting overlay ({@code isRole}); the Blind is never a disguise target anyway (DENYLIST).
 * 所有盲人服务共用的稳定谓词。职业判断读取 Wathe 的真实职业（{@code getRole}），从不读取黑羽鸦扮演覆盖层
 * （{@code isRole}）；盲人本就不能被伪装（DENYLIST）。
 */
public final class BlindParticipants {
    private BlindParticipants() {
    }

    /**
     * Online server player whose real role is the Blind, alive and playing in a running round.
     * 在线的服务端玩家，真实职业为盲人，且在进行中的对局里存活参与。
     */
    public static boolean isActiveBlind(@Nullable ServerPlayerEntity player) {
        if (player == null || player.isDisconnected()) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.isRunning()
                && BlindRules.isBlind(game.getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player);
    }

    /**
     * Wathe psycho mode, which SparkTraits' Depression psycho also starts: the player holds only the bat and hears no
     * voice (SparkTraits mutes a Depression-psycho listener), so the cane and VOICE pulses pause. Server read.
     * Wathe 疯魔模式（SparkTraits 抑郁发疯也会开启）：玩家只持有球棒且听不到语音（SparkTraits 会屏蔽抑郁发疯者收听），
     * 因此盲杖与 VOICE 脉冲暂停。服务端读取。
     */
    public static boolean isInPsychoMode(@Nullable PlayerEntity player) {
        return player != null && PlayerPsychoComponent.KEY.get(player).getPsychoTicks() > 0;
    }

    /** Side-neutral: the head equipment slot holds the ComTac VIII. / 两端通用：头部装备槽是 ComTac VIII。 */
    public static boolean wearsComTac(@Nullable PlayerEntity player) {
        return player != null && player.getEquippedStack(EquipmentSlot.HEAD).isOf(SparkWitchItems.comTac());
    }

    /**
     * Current maximum perception range in blocks for this Blind (10 / 30 / 50 / 150); see
     * {@link BlindRules#perceptionRange}. Server time decides whether Attune is active.
     * 该盲人当前的最大感知距离（格）；凝神是否生效以服务端时间为准。
     */
    public static int perceptionRange(ServerPlayerEntity blind) {
        boolean attuneActive = BlindComponent.KEY.get(blind).isAttuneActive(blind.getServerWorld().getTime());
        return BlindRules.perceptionRange(wearsComTac(blind), attuneActive);
    }

    /**
     * Wathe's current replay match id, or null when no match is active. Server only.
     * Wathe 当前回放对局 id；无进行中对局时为 null。仅服务端。
     */
    public static @Nullable UUID currentMatchId() {
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return GameRecordManager.hasActiveMatch() && match != null ? match.getMatchId() : null;
    }
}
