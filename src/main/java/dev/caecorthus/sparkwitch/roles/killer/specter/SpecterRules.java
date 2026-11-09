package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesSilenceBridge;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import dev.caecorthus.sparkwitch.compat.SparkTraitsAbyssListenerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaGrappleService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssSuppression;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Specter (幽魂) tuning, role predicate, the shared caster gate and the side-neutral mark target filter.
 * 幽魂的数值、职业判定、共用的施放者门槛，以及两端通用的标记目标过滤。
 */
public final class SpecterRules {
    public static final Identifier ROLE_ID = SparkWitch.id("specter");
    // Dried blood: dark red with reduced saturation. / 干涸血迹：低饱和暗红。
    public static final int COLOR = 0x6B2F2F;
    // Owner-provided outline colours: 25.png is #485FAD, 50.png is #05AC99.
    // 所有者提供的描边颜色：25.png 为 #485FAD，50.png 为 #05AC99。
    public static final float LOW_SANITY_THRESHOLD = 25.0F;
    public static final float MID_SANITY_THRESHOLD = 50.0F;
    public static final int LOW_SANITY_COLOR = 0x485FAD;
    public static final int MID_SANITY_COLOR = 0x05AC99;
    public static final int OUTLINE_PRIORITY = 300;
    // Low-sanity players see the invisible Specter in the role colour; outrank generic faction outlines.
    // 低理智玩家看到的隐身幽魂使用职业色描边，优先级高于通用阵营描边。
    public static final int LOW_SANITY_SPECTER_OUTLINE_PRIORITY = 900;
    // Cowardice: blue-purple outline in the Specter's view, between the mark and the sanity bands.
    // 怯懦：幽魂视角的蓝紫色描边，优先级在标记与理智区间之间。
    public static final int COWARDICE_COLOR = 0x6A5ACD;
    public static final int COWARDICE_OUTLINE_PRIORITY = 500;
    // The marked target's outline uses the role colour and outranks the sanity bands.
    // 被标记者描边使用职业色，优先级高于理智区间。
    public static final int MARK_OUTLINE_PRIORITY = 1_000;
    public static final double MARK_RANGE = 3.0D;
    // Owner decision: 10 s mark, 45 s initial cooldown, 45 s cooldown after the shriek; an expired mark costs nothing.
    // 所有者决定：标记 10 秒，开局冷却 45 秒，尖啸后冷却 45 秒；标记自然过期不进入冷却。
    public static final int MARK_DURATION_TICKS = 10 * 20;
    public static final int INITIAL_COOLDOWN_TICKS = 45 * 20;
    public static final int SHRIEK_COOLDOWN_TICKS = 45 * 20;
    /**
     * Replay skill ids, also the SparkFactionAPI {@code canAffectPlayer} action ids of the mark (manual or by a ghost
     * trigger) and the shriek, so Wraith, Vendetta, Rift occupant and Knight duel policies answer them.
     * 回放技能 id，同时也是标记（手动或经鬼魂触发器）与尖啸的 SparkFactionAPI {@code canAffectPlayer} 行为 id，
     * 使冤魂、仇杀客、裂隙门内与骑士决斗策略可以作答。
     */
    public static final Identifier MARK_SKILL_ID = SparkWitch.id("specter_mark");
    public static final Identifier SHRIEK_SKILL_ID = SparkWitch.id("specter_shriek");
    /**
     * SparkFactionAPI {@code canAffectPlayer} action id for the fear levels a shriek spreads around the marked player.
     * 尖啸向被标记者周围施加恐惧层数时使用的 SparkFactionAPI {@code canAffectPlayer} 行为 id。
     */
    public static final Identifier FEAR_ACTION_ID = SparkWitch.id("specter_fear");
    public static final Identifier OTHERWORLD_SKILL_ID = SparkWitch.id("specter_otherworld_skill");
    // Fear: up to 3 levels; level 1/2 drain every 10 s, level 3 every 5 s; one level decays every 60 s; sanity floor 5.
    // 恐惧：最多 3 层；1/2 层每 10 秒扣 san，3 层每 5 秒；每 60 秒掉 1 层；理智最低扣到 5。
    public static final int FEAR_MAX_LEVEL = 3;
    // Ghost trigger: -30 sanity, then cowardice for non-killer fake-mood roles and the Savior.
    // 鬼魂触发器：扣 30 理智；非杀手队的虚假理智职业与救世主获得怯懦。
    public static final float TRIGGER_SANITY_DRAIN = 30.0F;
    public static final int FEAR_DECAY_TICKS = 60 * 20;
    public static final float FEAR_MIN_SANITY = 5.0F;
    public static final double FEAR_SHRIEK_RADIUS = 5.0D;
    // Skill two (Otherworld): unlock 120 s after role assignment, 30 s active, 120 s cooldown counted from the end.
    // 技能二（里世界）：分配职业后 120 秒解锁，持续 30 秒，冷却 120 秒从结束时开始算。
    public static final int SECOND_SKILL_UNLOCK_TICKS = 120 * 20;
    public static final int SECOND_SKILL_DURATION_TICKS = 30 * 20;
    public static final int SECOND_SKILL_COOLDOWN_TICKS = 120 * 20;
    // Speed II equivalent as a movement-speed attribute modifier (+40%), removed when the window ends.
    // 速度 II 用移速属性修饰符实现（+40%），窗口结束时移除。
    public static final Identifier SECOND_SKILL_SPEED_MODIFIER_ID = SparkWitch.id("specter_otherworld_speed");
    public static final double SECOND_SKILL_SPEED_BONUS = 0.40D;
    // Opening shields: 3 layers per alive real-mood player below 25 sanity. Shields clear with the window.
    // 开启护盾：每个存活的真实理智低于 25 的玩家提供 3 层，窗口结束时清空。
    public static final int SECOND_SKILL_SHIELD_PER_LOW_SANITY = 3;
    // HUD colours blend 1:1 to COLOR: (0xA72F2F + 0x2F2F2F) / 2 = 0x6B2F2F.
    // HUD 颜色按 1:1 平均正好是职业色 0x6B2F2F。
    public static final int PRIMARY_HUD_COLOR = 0xA72F2F;
    public static final int SECONDARY_HUD_COLOR = 0x2F2F2F;

    private SpecterRules() {
    }

    public static boolean isSpecter(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Server caster gate shared by skill one, the Otherworld and the ghost trigger (Dominator {@code mayCast}
     * pattern): an ACTIVE round, the raw Specter role, alive and playing, not spectating/creative, not swallowed, not
     * held (Ninja hook, Kidnapper, stun, Last Escape/Stand, Hunter root, Seeker session), not silenced, and not
     * role-skill-blocked by SparkTraits. Callers then apply the Grand Witch Fear re-check themselves.
     * 技能一、里世界与鬼魂触发器共用的服务端施放门槛（支配者 {@code mayCast} 模式）：回合处于 ACTIVE、真实身份为幽魂、
     * 存活且参与对局、非旁观/创造、未被吞下、未被控制（忍者钩爪、绑匪、眩晕、脱险/背水、猎人定身、搜寻者会话）、未被沉默，
     * 且未被 SparkTraits 封锁职业技能。调用方随后自行再检查大魔女恐惧。
     */
    public static boolean mayCast(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && isSpecter(game.getRole(caster))
                && GameFunctions.isPlayerPlayingAndAlive(caster)
                && !caster.isSpectator()
                && !GameFunctions.isPlayerSpectatingOrCreative(caster)
                && !NoellesTaotieSeekerBridge.isSwallowed(caster)
                && !NinjaGrappleService.isHeld(caster)
                && !NoellesSilenceBridge.isSilenced(caster)
                && !SparkTraitsKillerBridge.isRoleSkillBlocked(caster);
    }

    /**
     * Effective "has a real sanity bar" for every Specter sanity rule: SparkTraits' effective mood type (Conscience
     * makes a killer REAL, Impostor makes a civilian FAKE, an active Wraith never counts), via the shared
     * {@link AbyssSuppression#hasRealSanity} rule rather than the raw {@code Role.getMoodType()}.
     * 幽魂所有理智规则使用的“拥有真实理智条”判定：SparkTraits 的有效理智类型（善良让杀手变为真实，内鬼让平民变为虚假，
     * 活跃冤魂从不计入），复用共享的 {@link AbyssSuppression#hasRealSanity} 规则，而非原始 {@code Role.getMoodType()}。
     */
    public static boolean hasRealSanity(ServerPlayerEntity player) {
        return AbyssSuppression.hasRealSanity(player);
    }

    /**
     * Side-neutral copy of {@link #hasRealSanity} for client presentation (the low-sanity viewer outline): the same
     * public reads ({@link SparkTraitsAbyssListenerBridge} Conscience/Impostor facade, raw role mood type,
     * {@link WraithStateService#isActive}) and the same combination as {@code AbyssSuppression.effectiveRealSanity}.
     * On a client it is only meaningful for the local player, whose own traits and mood are synced to it. An unknown
     * trait answer fails closed (no real sanity). Server rules must keep using {@link #hasRealSanity}.
     * {@link #hasRealSanity} 的两端通用副本，供客户端表现使用（低理智观察者描边）：读取相同的公共数据（
     * {@link SparkTraitsAbyssListenerBridge} 的良知/内鬼门面、原始职业理智类型、{@link WraithStateService#isActive}），
     * 组合方式与 {@code AbyssSuppression.effectiveRealSanity} 相同。在客户端只对本地玩家有意义，因为只有本人的词条与理智会同步给
     * 本人。词条查询结果未知时失败关闭（视为无真实理智）。服务端规则必须继续使用 {@link #hasRealSanity}。
     */
    public static boolean hasRealSanityEitherSide(@Nullable PlayerEntity player) {
        if (player == null || WraithStateService.isActive(player)) {
            return false;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return hasRealSanity(serverPlayer);
        }
        if (Boolean.TRUE.equals(SparkTraitsAbyssListenerBridge.isConscienceActive(player))) {
            return true;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null
                && role.getMoodType() == Role.MoodType.REAL
                && Boolean.FALSE.equals(SparkTraitsAbyssListenerBridge.isImpostorActive(player));
    }

    /** Effective real sanity at or below {@link #LOW_SANITY_THRESHOLD}. / 有效真实理智且不高于 {@link #LOW_SANITY_THRESHOLD}。 */
    public static boolean isLowSanity(ServerPlayerEntity player) {
        return hasRealSanity(player)
                && PlayerMoodComponent.KEY.get(player).getMood() * 100.0F <= LOW_SANITY_THRESHOLD;
    }

    /** SparkFactionAPI effective killer faction (non-Conscience killers, Impostors). / SFA 有效杀手阵营（非善良杀手、内鬼）。 */
    public static boolean isEffectiveKiller(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return FactionIds.KILLER.equals(SparkFactionApi.resolveEffectiveFaction(player, game));
    }

    /** Ticks between two sanity drains at the given fear level. / 指定恐惧层数的扣 san 间隔（tick）。 */
    public static int fearDrainIntervalTicks(int level) {
        return level >= FEAR_MAX_LEVEL ? 5 * 20 : 10 * 20;
    }

    /** Sanity lost per drain at the given fear level. / 指定恐惧层数每次扣除的 san。 */
    public static float fearDrainSanity(int level) {
        return switch (level) {
            case 1 -> 5.0F;
            case 2 -> 10.0F;
            default -> level >= FEAR_MAX_LEVEL ? 20.0F : 0.0F;
        };
    }

    /** Sanity band colour: {@code <=25} uses the 25.png colour, {@code 25 < san <= 50} the 50.png colour.
     *  理智区间颜色：{@code <=25} 用 25.png 的颜色，{@code 25 < san <= 50} 用 50.png 的颜色。 */
    public static @Nullable Integer sanityHighlightColor(float sanityPercent) {
        if (sanityPercent <= LOW_SANITY_THRESHOLD) {
            return LOW_SANITY_COLOR;
        }
        if (sanityPercent <= MID_SANITY_THRESHOLD) {
            return MID_SANITY_COLOR;
        }
        return null;
    }

    /**
     * Side-neutral mark filter, shared by the client HUD and the server ({@code DominatorRules.isMarkable} pattern):
     * never the caster; a living, non-spectator, non-creative Wathe participant (so never a Rift Gate occupant or a
     * swallowed player, both living spectators); not invisible (the flag also covers SparkTraits Last Escape and Last
     * Stand pending) and not invisible to the caster; not swallowed; not an active Wraith; not hidden from the caster's
     * instinct by SparkTraits ({@link #IS_INSTINCT_HIDDEN}). The server additionally asks SparkFactionAPI
     * {@code canAffectPlayer} in {@code SpecterSkillService}; that check is server-only and never runs here.
     * 两端通用的标记过滤，客户端 HUD 与服务端共用（{@code DominatorRules.isMarkable} 模式）：不是施放者本人；在 Wathe 中存活、
     * 非旁观、非创造的参与者（因此不会是裂隙门内或被吞下的玩家，二者都是活体旁观者）；未隐身（隐身标记也覆盖 SparkTraits 脱险与
     * 背水一战待定），且对施放者不隐身；未被吞下；不是激活冤魂；未被 SparkTraits 对施放者本能隐藏。服务端在
     * {@code SpecterSkillService} 中另行询问 SparkFactionAPI {@code canAffectPlayer}；该检查仅在服务端进行，从不在此运行。
     */
    public static boolean isMarkable(PlayerEntity caster, @Nullable PlayerEntity target) {
        return target != null
                && target != caster
                && !target.isSpectator()
                && !target.isCreative()
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && !target.isInvisible()
                && !target.isInvisibleTo(caster)
                && !NoellesTaotieSeekerBridge.isSwallowed(target)
                && !WraithStateService.isActive(target)
                && !IS_INSTINCT_HIDDEN.callBoolean(false, false, false, caster, target);
    }

    /**
     * External seam (optional SparkTraits): the Specter's own copy of the public, side-neutral
     * {@code SparkTraitsApi.isInstinctHidden(viewer, target)} facade (Going Dark and other instinct hiding), as the
     * Dominator keeps one. The marked body is outlined through walls, an instinct-like sight, so a target hidden from
     * the caster's instinct is neither marked by the server nor named on the HUD. An absent mod, older build, wrong
     * type or throwing call hides nothing (fail closed).
     * 外部接缝（可选 SparkTraits）：幽魂自有的一份公共、两端通用的 {@code SparkTraitsApi.isInstinctHidden(viewer, target)}
     * 门面读取（隐匿等本能隐藏），与支配者各自保留一份相同。被标记者会被穿墙描边，属于类本能视野，因此对施放者本能隐藏的目标
     * 既不会被服务端标记，也不会在 HUD 上被点名。模组缺失、旧版本、类型不符或调用抛异常时都不隐藏任何人（失败关闭）。
     */
    private static final OptionalStaticMethod IS_INSTINCT_HIDDEN = OptionalStaticMethod.of("sparktraits",
            "dev.caecorthus.sparktraits.api.SparkTraitsApi", "isInstinctHidden",
            PlayerEntity.class, PlayerEntity.class).returning(boolean.class);

    /**
     * Nearest {@link #isMarkable} player hit by the crosshair ray within {@link #MARK_RANGE} blocks, requiring line of
     * sight. Side-neutral: the HUD names exactly this player, and the server marks exactly this player after its own
     * SparkFactionAPI check (a refused target is not skipped for the one behind it, so the HUD never names someone the
     * server would not pick).
     * 准星射线 {@link #MARK_RANGE} 格内最近的 {@link #isMarkable} 玩家，并要求视线可见。两端通用：HUD 点名的正是该玩家，
     * 服务端在自身的 SparkFactionAPI 检查后标记的也正是该玩家（被拒绝的目标不会让射线改选其身后的玩家，因此 HUD 不会点名服务端
     * 不会选中的人）。
     */
    public static @Nullable PlayerEntity findMarkTarget(PlayerEntity caster) {
        Vec3d start = caster.getEyePos();
        Vec3d end = start.add(caster.getRotationVec(1.0F).multiply(MARK_RANGE));
        PlayerEntity nearest = null;
        double nearestSquared = MARK_RANGE * MARK_RANGE;
        for (PlayerEntity target : caster.getWorld().getPlayers()) {
            if (!isMarkable(caster, target) || !caster.canSee(target)) {
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
