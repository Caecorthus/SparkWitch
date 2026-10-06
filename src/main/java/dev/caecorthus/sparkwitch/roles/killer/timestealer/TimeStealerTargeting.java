package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.NoellesSilenceBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.function.BooleanSupplier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side Clock gates: whether the user may use the Clock now, and whether a candidate may be affected.
 * Participant, Last Escape and Vendetta semantics follow the Control Expert's targeting, kept role-owned so neither
 * module depends on the other.
 * 服务端时钟判定：使用者当前能否使用时钟，以及候选者能否被影响。参与者、最后逃脱与复仇者隔离的语义与控场专家一致，
 * 但由本职业自行持有，两个模块互不依赖。
 */
public final class TimeStealerTargeting {
    private TimeStealerTargeting() {
    }

    /**
     * Use gate (plan §2): the round is exactly ACTIVE (not merely running), the user is exactly the Time Stealer
     * whatever their effective faction (owner decision 2026-10-05 replaces Q10: a Conscience Time Stealer uses the
     * Clock too), alive and not a spectator, the authoritative {@code ClockReadyAt} tick has passed, not silenced by
     * NoellesRoles or SparkTraits, and SparkTraits allows the weapon action. Absent optional providers add no block.
     * 使用判定（计划 §2）：对局恰为 ACTIVE（而非仅在进行中）；使用者精确为窃时者，不论有效阵营（所有者决定 2026-10-05
     * 取代 Q10：善良窃时者同样能使用时钟）；存活且非旁观；权威的 {@code ClockReadyAt} tick 已到；未被 NoellesRoles 或
     * SparkTraits 沉默；SparkTraits 允许该武器动作。可选提供方缺失时不附加限制。
     */
    public static boolean canUse(ServerPlayerEntity user, ItemStack clock) {
        return user != null && clock != null && isClockReady(user) && canUseWhenReady(user, clock);
    }

    /**
     * Target veto (plan N5); ineligible candidates are transparent to the ray. Never the user; only living,
     * non-spectator participants of any faction (owner decision 2026-10-05: the Clock ignores factions, so fellow
     * killers are no longer see-through); not in SparkTraits Last Escape; Vendetta exact-pair isolation;
     * SparkFactionAPI's structural veto for {@code sparkwitch:time_stolen}; and not already stolen (N7). Kill
     * protections are never evaluated here; they resolve at the curse's death.
     * 目标否决（计划 N5）；不合格者对射线透明。永不影响使用者本人；影响任何阵营的存活且非旁观参与者（所有者决定
     * 2026-10-05：时钟无视阵营，同阵营杀手不再被射线穿过）；不处于 SparkTraits 最后逃脱；遵守复仇者精确配对隔离；遵守
     * SparkFactionAPI 对 {@code sparkwitch:time_stolen} 的结构性否决；且目标当前未被窃（N7）。这里从不评估击杀保护，
     * 它们在诅咒致死时结算。
     */
    public static boolean canAffect(ServerPlayerEntity user, ServerPlayerEntity target) {
        if (user == null || target == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(user.getWorld());
        return affects(
                isSamePlayer(user, target),
                () -> isParticipant(target, game),
                () -> SparkTraitsKillerBridge.isLastEscapeActive(target),
                () -> vendettaAllows(user, target),
                () -> SparkFactionApi.canAffectPlayer(user, target, SparkWitchDeathReasons.TIME_STOLEN, game),
                () -> TimeTheftPlayerComponent.KEY.get(target).isStolen());
    }

    /**
     * Gift Watch use gate (owner decision 2026-10-05): the Clock's gates with its own {@code GiftReadyAt} cooldown, plus
     * a Conscience holder ({@link TimeStealerRules#holdsGiftWatch}), so a stray copy in anyone else's hands does nothing.
     * 赠时怀表使用判定（所有者决定 2026-10-05）：时钟的各项判定，使用其独立的 {@code GiftReadyAt} 冷却，并要求持有者为
     * 善良窃时者（{@link TimeStealerRules#holdsGiftWatch}），因此落到其他人手里的残留副本不起作用。
     */
    public static boolean canUseGift(ServerPlayerEntity user, ItemStack watch) {
        return user != null && watch != null && isGiftReady(user) && canUseGiftWhenReady(user, watch);
    }

    /**
     * Gift target veto: the Clock's veto ({@link #affects}) with the Gift Watch's own action id and "already gifted" in
     * place of "already stolen"; a curse on the same player does not matter.
     * 赠时目标否决：与时钟相同的否决（{@link #affects}），但使用赠时怀表自己的动作 id，并以“已在赠时中”代替“已被窃”；
     * 同一玩家身上的诅咒不影响赠时。
     */
    public static boolean canGift(ServerPlayerEntity user, ServerPlayerEntity target) {
        if (user == null || target == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(user.getWorld());
        return affects(
                isSamePlayer(user, target),
                () -> isParticipant(target, game),
                () -> SparkTraitsKillerBridge.isLastEscapeActive(target),
                () -> vendettaAllows(user, target),
                () -> SparkFactionApi.canAffectPlayer(user, target, TimeStealerRules.GIFT_ACTION_ID, game),
                () -> TimeGiftPlayerComponent.KEY.get(target).isGifted());
    }

    /** Authoritative Gift Watch cooldown gate. / 赠时怀表的权威冷却判定。 */
    static boolean isGiftReady(ServerPlayerEntity user) {
        return user.getWorld().getTime() >= TimeStealerPlayerComponent.KEY.get(user).giftReadyAt();
    }

    /** Every Gift Watch use condition except {@code GiftReadyAt}. / 除 {@code GiftReadyAt} 外的全部赠时怀表使用条件。 */
    static boolean canUseGiftWhenReady(ServerPlayerEntity user, ItemStack watch) {
        GameWorldComponent game = GameWorldComponent.KEY.get(user.getWorld());
        return usable(
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                TimeStealerRules.holdsGiftWatch(TimeStealerRules.isTimeStealer(game.getRole(user)),
                        SparkFactionApi.resolveEffectiveFaction(user, game)),
                () -> GameFunctions.isPlayerPlayingAndAlive(user) && !user.isSpectator(),
                () -> NoellesSilenceBridge.isSilenced(user),
                () -> SparkTraitsKillerBridge.isRoleSkillBlocked(user),
                () -> SparkTraitsKillerBridge.blocksWeaponAction(user, watch));
    }

    /** Authoritative cooldown gate: world time has reached {@code ClockReadyAt}. / 权威冷却判定：世界时间已到 {@code ClockReadyAt}。 */
    static boolean isClockReady(ServerPlayerEntity user) {
        return user.getWorld().getTime() >= TimeStealerPlayerComponent.KEY.get(user).clockReadyAt();
    }

    /**
     * Every use condition except {@code ClockReadyAt}; lets the service tell a cooldown-only refusal apart so it can
     * restore a display cooldown that something else shortened.
     * 除 {@code ClockReadyAt} 外的全部使用条件；使服务能识别“仅因冷却被拒”，从而恢复被其他机制缩短的显示冷却。
     */
    static boolean canUseWhenReady(ServerPlayerEntity user, ItemStack clock) {
        GameWorldComponent game = GameWorldComponent.KEY.get(user.getWorld());
        return usable(
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                TimeStealerRules.isTimeStealer(game.getRole(user)),
                () -> GameFunctions.isPlayerPlayingAndAlive(user) && !user.isSpectator(),
                () -> NoellesSilenceBridge.isSilenced(user),
                () -> SparkTraitsKillerBridge.isRoleSkillBlocked(user),
                () -> SparkTraitsKillerBridge.blocksWeaponAction(user, clock));
    }

    /** Pure gate; later seams are consulted only after every earlier one passed. / 纯判定；前序条件全部通过后才查询后续接缝。 */
    static boolean usable(boolean active, boolean timeStealer, BooleanSupplier aliveParticipant,
                          BooleanSupplier noellesSilenced, BooleanSupplier roleSkillBlocked,
                          BooleanSupplier weaponActionBlocked) {
        return active
                && timeStealer
                && aliveParticipant.getAsBoolean()
                && !noellesSilenced.getAsBoolean()
                && !roleSkillBlocked.getAsBoolean()
                && !weaponActionBlocked.getAsBoolean();
    }

    /** Pure gate; later seams are consulted only after every earlier one passed. / 纯判定；前序条件全部通过后才查询后续接缝。 */
    static boolean affects(boolean samePlayer, BooleanSupplier participant, BooleanSupplier lastEscape,
                           BooleanSupplier vendettaAllows, BooleanSupplier factionAllows,
                           BooleanSupplier alreadyStolen) {
        return !samePlayer
                && participant.getAsBoolean()
                && !lastEscape.getAsBoolean()
                && vendettaAllows.getAsBoolean()
                && factionAllows.getAsBoolean()
                && !alreadyStolen.getAsBoolean();
    }

    static boolean vendettaAllows(boolean userVendetta, boolean targetVendetta, boolean exactPair) {
        return !(userVendetta || targetVendetta) || exactPair;
    }

    /**
     * Living, non-spectator, non-creative, non-active-Wraith player with a role.
     * 存活、非旁观、非创造、非激活冤魂且拥有职业的玩家。
     */
    private static boolean isParticipant(PlayerEntity player, GameWorldComponent game) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator()
                && !player.isCreative()
                && !WraithStateService.isActive(player)
                && game.getRole(player) != null;
    }

    /**
     * Mirrors the Vendetta packet guard: an active Vendetta endpoint is reachable only inside its exact pair.
     * 与复仇者数据包拦截一致：处于激活状态的复仇者一端只能在其精确配对内被影响。
     */
    private static boolean vendettaAllows(PlayerEntity user, PlayerEntity target) {
        return vendettaAllows(
                VendettaInteractionService.isActiveVendetta(user),
                VendettaInteractionService.isActiveVendetta(target),
                VendettaInteractionService.isExactPair(user, target));
    }

    private static boolean isSamePlayer(PlayerEntity user, PlayerEntity target) {
        return user == target || user.getUuid().equals(target.getUuid());
    }
}
