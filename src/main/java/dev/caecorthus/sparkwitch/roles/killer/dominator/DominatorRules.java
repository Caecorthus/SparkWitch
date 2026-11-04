package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Dominator tuning and the shared crosshair target resolver.
 * 支配者的数值与客户端/服务端共用的准星目标解析。
 */
public final class DominatorRules {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "dominator");
    public static final Identifier FORCED_SHOT_ID = Identifier.of("sparkwitch", "dominator_forced_shot");
    /**
     * Replay skill ids, formatted by Wathe's default skill formatter as
     * {@code replay.skill.sparkwitch.<path>.target} (actor, target).
     * 回放技能 id，由 Wathe 默认技能格式化器按 {@code replay.skill.sparkwitch.<path>.target}（施放者、目标）显示。
     */
    public static final Identifier MARK_SKILL_ID = Identifier.of("sparkwitch", "dominator_mark");
    public static final Identifier RELEASE_SKILL_ID = Identifier.of("sparkwitch", "dominator_release");
    /**
     * Action-bar hint when the marked player cannot be forced to shoot right now; the mark, the use and the cooldown
     * are untouched. / 被标记者此刻无法被强制开枪时的动作栏提示；标记、次数与冷却均不变。
     */
    public static final String TARGET_UNFORCEABLE_KEY = "message.sparkwitch.dominator.target_unforceable";
    // Dark green as requested by the owner. / 所有者要求的深绿色。
    public static final int COLOR = 0x006400;
    // Starts empty; every personal kill grants one use, capped at two. / 开局为 0；亲手击杀 +1，上限 2。
    public static final int INITIAL_USES = 0;
    public static final int MAX_USES = 2;
    public static final int MARK_DURATION_TICKS = 30 * 20;
    // Paid to the Dominator when the forced shot kills a player; the forced shooter's own kill money stays suppressed.
    // 强制开枪打死玩家时发给支配者的奖金；被强制开枪者自己的击杀奖金仍被压制。
    public static final int FORCED_SHOT_KILL_REWARD = 100;
    // G stays on cooldown after the mark ends (expired, invalidated or released) and at round start.
    // 标记结束（到期/目标失效/释放）后以及开局时，G 进入冷却。
    public static final int MARK_END_COOLDOWN_TICKS = 10 * 20;
    public static final int INITIAL_COOLDOWN_TICKS = 45 * 20;
    // Forced-shot ray: Wathe's revolver ray is 30 blocks; a range trait multiplies it.
    // 强制开枪射线：Wathe 左轮射线为 30 格，射程词条会按倍率延长。
    public static final double FORCED_SHOT_RANGE = 30.0D;
    // Owner decision: ignore distance and line of sight; 150 blocks is the practical cap.
    // 所有者决定：无视距离与视线遮挡，150 格作为实际上限。
    public static final double TARGET_RANGE = 150.0D;
    public static final double TARGET_RANGE_SQUARED = TARGET_RANGE * TARGET_RANGE;

    private DominatorRules() {
    }

    public static boolean isDominator(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Side-neutral part of the caster gate, shared by the client G branch and the server: alive in Wathe, not in
     * spectator mode and not swallowed by a Taotie. Wathe's liveness ignores the game mode, so a spectator-mode or
     * swallowed (living spectator) Dominator would otherwise still mark and force shots. The server adds its own
     * server-only gates in {@code DominatorSkillService}.
     * 施放者门槛中两端通用的部分，客户端 G 键分支与服务端共用：在 Wathe 中存活、不处于旁观模式、未被饕餮吞下。Wathe 的存活判定
     * 不看游戏模式，否则旁观模式或被吞下（活体旁观者）的支配者仍能标记并强制开枪。服务端在 {@code DominatorSkillService} 中
     * 另加仅服务端的门槛。
     */
    public static boolean isActiveCaster(@Nullable PlayerEntity caster) {
        return caster != null
                && GameFunctions.isPlayerPlayingAndAlive(caster)
                && !caster.isSpectator()
                && !NoellesTaotieSeekerBridge.isSwallowed(caster);
    }

    /**
     * Common (both sides) mark filter, owner decision "按别人看到的来" (go by what others see): never the caster; a
     * living, non-spectator Wathe participant (so never a Rift Gate occupant or a swallowed player, both living
     * spectators); not invisible (the invisibility flag also covers SparkTraits Last Escape and Last Stand pending) and
     * not invisible to the caster; not swallowed; not hidden from the caster's instinct by SparkTraits
     * ({@link #IS_INSTINCT_HIDDEN}, e.g. Going Dark). Disguises are not filtered: the player is marked and the HUD shows
     * the disguised name. Client-only hidden states go through {@link #findAimedPlayer(PlayerEntity, Predicate)}.
     * 两端通用的标记过滤，所有者决定“按别人看到的来”：不是施放者本人；在 Wathe 中存活且非旁观的参与者（因此不会是裂隙门内的玩家
     * 或被吞下的玩家，二者都是活体旁观者）；未隐身（隐身标记也覆盖 SparkTraits 脱险与背水一战待定），且对施放者不隐身；未被吞下；未被 SparkTraits 对施放者本能隐藏（如隐匿）。
     * 伪装不过滤：玩家照常可被标记，HUD 显示伪装后的名字。仅客户端的隐藏状态经 {@link #findAimedPlayer(PlayerEntity, Predicate)}
     * 传入。
     */
    public static boolean isMarkable(PlayerEntity caster, @Nullable PlayerEntity target) {
        return target != null
                && target != caster
                && !target.isSpectator()
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && !target.isInvisible()
                && !target.isInvisibleTo(caster)
                && !NoellesTaotieSeekerBridge.isSwallowed(target)
                && !IS_INSTINCT_HIDDEN.callBoolean(false, false, false, caster, target);
    }

    /**
     * External seam (optional SparkTraits): the Dominator's own copy of the public, side-neutral
     * {@code SparkTraitsApi.isInstinctHidden(viewer, target)} facade (Going Dark and other instinct hiding). The
     * through-wall mark is an instinct-like sight, so a target hidden from the caster's instinct is neither marked by
     * the server nor named or outlined on the client. An absent mod, older build, wrong type or throwing call hides
     * nothing (fail closed).
     * 外部接缝（可选 SparkTraits）：支配者自有的一份公共、两端通用的 {@code SparkTraitsApi.isInstinctHidden(viewer, target)}
     * 门面读取（隐匿等本能隐藏）。穿墙标记属于类本能视野，因此对施放者本能隐藏的目标既不会被服务端标记，也不会在客户端被点名
     * 或描边。模组缺失、旧版本、类型不符或调用抛异常时都不隐藏任何人（失败关闭）。
     */
    private static final OptionalStaticMethod IS_INSTINCT_HIDDEN = OptionalStaticMethod.of("sparktraits",
            "dev.caecorthus.sparktraits.api.SparkTraitsApi", "isInstinctHidden",
            PlayerEntity.class, PlayerEntity.class).returning(boolean.class);

    /**
     * Nearest markable player ({@link #isMarkable}) hit by the crosshair ray. Equivalent to
     * {@code findAimedPlayer(caster, player -> true)}; the server marks with exactly this.
     * 准星射线命中的最近可标记玩家（{@link #isMarkable}）。等同于 {@code findAimedPlayer(caster, player -> true)}；服务端
     * 标记时使用的正是它。
     */
    public static @Nullable PlayerEntity findAimedPlayer(PlayerEntity caster) {
        return findAimedPlayer(caster, player -> true);
    }

    /**
     * Nearest player hit by the crosshair ray that passes {@link #isMarkable} and {@code extraFilter}. Entity-only
     * raycast: blocks never block the ray and line of sight ({@code canSee}) is deliberately not checked. A candidate
     * that fails either filter is transparent, so the ray reaches the visible player behind it. Works on both sides;
     * {@code extraFilter} (true keeps the candidate) is for client-only hidden states the HUD knows about, and must
     * never be needed for server authority.
     * 准星射线命中、且通过 {@link #isMarkable} 与 {@code extraFilter} 的最近玩家。只做实体射线：方块不遮挡，且刻意不检查视线
     * （{@code canSee}）。任一过滤不通过的候选者是透明的，射线会命中其身后可见的玩家。两端通用；{@code extraFilter}（返回 true
     * 表示保留候选者）用于 HUD 才知道的仅客户端隐藏状态，服务端权威绝不依赖它。
     */
    public static @Nullable PlayerEntity findAimedPlayer(
            PlayerEntity caster,
            Predicate<PlayerEntity> extraFilter
    ) {
        Vec3d start = caster.getEyePos();
        Vec3d end = start.add(caster.getRotationVec(1.0F).multiply(TARGET_RANGE));
        PlayerEntity nearest = null;
        double nearestSquared = TARGET_RANGE_SQUARED;
        // Only players are candidates, so this is O(roster) rather than scanning every entity within 150 blocks.
        // 候选只有玩家，因此复杂度是玩家数，而不是扫描 150 格内的所有实体。
        for (PlayerEntity target : caster.getWorld().getPlayers()) {
            if (!isMarkable(caster, target) || !extraFilter.test(target)) {
                continue;
            }
            Optional<Vec3d> hit = target.getBoundingBox()
                    .expand(target.getTargetingMargin())
                    .raycast(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double hitSquared = hit.get().squaredDistanceTo(start);
            if (hitSquared <= nearestSquared) {
                nearestSquared = hitSquared;
                nearest = target;
            }
        }
        return nearest;
    }
}
