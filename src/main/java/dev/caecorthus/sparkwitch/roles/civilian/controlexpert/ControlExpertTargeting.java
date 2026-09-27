package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Control Expert participant and targeting predicates. Same participant semantics as the Bell Ringer's,
 * kept role-owned so neither module depends on the other.
 * 控场专家的参与者与目标判定。参与者语义与敲钟人一致，但由本职业自行持有，两个模块互不依赖。
 */
public final class ControlExpertTargeting {
    private ControlExpertTargeting() {
    }

    /**
     * Living, non-spectator, non-creative, non-active-Wraith player with a role in a running game.
     * 在进行中的对局里存活、非旁观、非创造、非激活冤魂且拥有职业的玩家。
     */
    public static boolean isParticipant(@Nullable PlayerEntity player) {
        if (player == null
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || player.isSpectator()
                || player.isCreative()
                || WraithStateService.isActive(player)) {
            return false;
        }
        return GameWorldComponent.KEY.get(player.getWorld()).getRole(player) != null;
    }

    /**
     * Server only. Whether {@code ce}'s {@code actionId} may affect {@code target}: never the user, only
     * participants, honouring SparkFactionAPI's structural veto, SparkTraits Last Escape and Vendetta isolation.
     * Players who fail it are treated as absent by every item (no hit, no detonation, no notice).
     * 仅服务端。{@code ce} 的 {@code actionId} 能否影响 {@code target}：永不影响使用者本人，只影响参与者，
     * 并遵守 SparkFactionAPI 的结构性否决、SparkTraits 最后逃脱与复仇者隔离。未通过者对所有道具而言视同不存在
     * （不被命中、不触发引爆、不收到提示）。
     */
    public static boolean canAffect(@Nullable ServerPlayerEntity ce, @Nullable ServerPlayerEntity target,
                                    Identifier actionId) {
        if (ce == null || target == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(ce.getWorld());
        return affects(
                isSamePlayer(ce, target),
                () -> isParticipant(target),
                () -> SparkFactionApi.canAffectPlayer(ce, target, actionId, game),
                () -> SparkTraitsKillerBridge.isLastEscapeActive(target),
                () -> vendettaAllows(ce, target));
    }

    /**
     * Server only. The user must be a Control Expert participant in a running game, not stunned, and not under a
     * SparkTraits role-skill block.
     * 仅服务端。使用者必须是进行中对局里的控场专家参与者，未被眩晕，且未被 SparkTraits 封锁职业技能。
     */
    public static boolean canUse(@Nullable ServerPlayerEntity ce) {
        if (ce == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(ce.getWorld());
        return usable(
                game.isRunning(),
                ControlExpertRules.isControlExpert(game.getRole(ce)),
                () -> isParticipant(ce),
                () -> ControlExpertStun.isStunned(ce),
                () -> SparkTraitsKillerBridge.isRoleSkillBlocked(ce));
    }

    /** Pure gate; later seams are consulted only after every earlier one passed. / 纯判定；前序条件全部通过后才查询后续接缝。 */
    static boolean affects(boolean samePlayer, BooleanSupplier participant, BooleanSupplier factionAllows,
                           BooleanSupplier lastEscape, BooleanSupplier vendettaAllows) {
        return !samePlayer
                && participant.getAsBoolean()
                && factionAllows.getAsBoolean()
                && !lastEscape.getAsBoolean()
                && vendettaAllows.getAsBoolean();
    }

    static boolean usable(boolean running, boolean controlExpert, BooleanSupplier participant,
                          BooleanSupplier stunned, BooleanSupplier roleSkillBlocked) {
        return running
                && controlExpert
                && participant.getAsBoolean()
                && !stunned.getAsBoolean()
                && !roleSkillBlocked.getAsBoolean();
    }

    /**
     * Mirrors the Vendetta packet guard: an active Vendetta endpoint is reachable only inside its exact pair.
     * 与复仇者数据包拦截一致：处于激活状态的复仇者一端只能在其精确配对内被影响。
     */
    static boolean vendettaAllows(PlayerEntity ce, PlayerEntity target) {
        return vendettaAllows(
                VendettaInteractionService.isActiveVendetta(ce),
                VendettaInteractionService.isActiveVendetta(target),
                VendettaInteractionService.isExactPair(ce, target));
    }

    static boolean vendettaAllows(boolean ceVendetta, boolean targetVendetta, boolean exactPair) {
        return !(ceVendetta || targetVendetta) || exactPair;
    }

    private static boolean isSamePlayer(PlayerEntity ce, PlayerEntity target) {
        return ce == target || ce.getUuid().equals(target.getUuid());
    }
}
