package dev.caecorthus.sparkwitch.roles.witch.abysslistener.shriek;

import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssSuppression;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Warden's Shriek (监守之啸) use handler on the shared Witch-skill path (registered in SparkWitchBuiltInSkills with an
 * exact-role selector). Server-authoritative: the shared path already checked the SparkWitch role, life, Fear and the
 * shared cooldown, but it neither spends {@code manaCost} nor asks SparkTraits, so this handler re-checks the exact
 * role, the running game and {@code isRoleSkillBlocked}, then spends {@link AbyssListenerRules#SHRIEK_MANA_COST}
 * itself (insufficient mana refuses at no cost, like the Death Ray). A paid cast always returns success so the shared
 * cooldown starts even when nobody is in range (no free probing), and never tells the caster how many were hit.
 * 共享魔女技能路径上的监守之啸处理器（在 SparkWitchBuiltInSkills 中以精确职业选择器注册）。由服务端权威决定：共享路径已检查
 * SparkWitch 职业、存活、恐惧与共享冷却，但既不扣除 {@code manaCost}，也不询问 SparkTraits；因此本处理器重新检查精确职业、
 * 对局进行中与 {@code isRoleSkillBlocked}，再自行扣除 {@link AbyssListenerRules#SHRIEK_MANA_COST}（魔力不足时不扣费直接拒绝，
 * 与死亡射线一致）。付费施放一律返回成功，即使范围内无人也进入共享冷却（防止免费探测），且从不告知施放者命中人数。
 */
public final class WardensShriekService {
    /** Low Warden roar layered over the vanilla shrieker event. / 叠加在原版尖啸事件上的低音量监守者咆哮。 */
    private static final float ROAR_VOLUME = 0.6F;
    private static final float ROAR_PITCH = 0.9F;
    private static final double RADIUS_SQUARED = AbyssListenerRules.SHRIEK_RADIUS * AbyssListenerRules.SHRIEK_RADIUS;

    private WardensShriekService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        ServerPlayerEntity caster = context.player();
        GameWorldComponent game = context.gameComponent();
        // SparkWitch packets are not covered by SparkTraits' own packet gate, so ask the facade here.
        // SparkWitch 自有数据包不在 SparkTraits 的数据包拦截范围内，因此在此主动查询公共门面。
        String refusal = refusal(
                AbyssListenerRules.isAbyssListener(context.role()),
                game.isRunning() && GameFunctions.isPlayerPlayingAndAlive(caster) && !caster.isSpectator(),
                SparkTraitsKillerBridge.isRoleSkillBlocked(caster));
        if (refusal != null) {
            return WitchSkillUseResult.fail(refusal);
        }
        if (!WitchPlayerComponent.KEY.get(caster).spendMana(AbyssListenerRules.SHRIEK_MANA_COST)) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.not_enough_mana");
        }
        int affected = shriek(context.world(), caster);
        record(caster, affected);
        return castResult();
    }

    /**
     * Applies the shriek to every eligible non-ally in range and plays the presentation; returns the number affected
     * (replay only). Distance is the 3D distance between entity positions (feet to feet), inclusive at the radius; no
     * line of sight is needed (N7).
     * 对范围内所有符合条件的非队友施加尖啸并播放表现；返回受影响人数（仅用于回放）。距离为实体位置（脚底到脚底）的三维距离，
     * 恰在半径上也算在内；无需视线（N7）。
     */
    private static int shriek(ServerWorld world, ServerPlayerEntity caster) {
        List<ServerPlayerEntity> targets = selectTargets(
                List.copyOf(world.getPlayers()),
                player -> player == caster || player.getUuid().equals(caster.getUuid()),
                player -> player.squaredDistanceTo(caster),
                player -> AbyssSuppression.canAffect(caster, player, AbyssListenerRules.SHRIEK_ACTION_ID),
                AbyssSuppression::isAlly);
        for (ServerPlayerEntity target : targets) {
            AbyssSuppression.addEffect(target, StatusEffects.SLOWNESS, AbyssListenerRules.SHRIEK_SLOWNESS_TICKS,
                    AbyssListenerRules.SHRIEK_SLOWNESS_AMPLIFIER, caster);
            AbyssSuppression.drainSanity(target, AbyssListenerRules.SHRIEK_SANITY_LOSS);
            AbyssSuppression.forceCooldowns(target, AbyssListenerRules.SHRIEK_FORCED_COOLDOWN_TICKS);
        }
        // Vanilla shrieker event (shriek sound + shriek particles) for nearby clients, plus a low Warden roar.
        // 原版尖啸体世界事件（尖啸声与尖啸粒子）发给附近客户端，再叠加一声低音量监守者咆哮。
        world.syncWorldEvent(null, WorldEvents.SCULK_SHRIEKS, caster.getBlockPos(), 0);
        world.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENTITY_WARDEN_ROAR,
                SoundCategory.PLAYERS, ROAR_VOLUME, ROAR_PITCH);
        return targets.size();
    }

    private static void record(ServerPlayerEntity caster, int affected) {
        NbtCompound extra = new NbtCompound();
        extra.putInt("targets", affected);
        GameRecordManager.recordSkillUse(caster, AbyssListenerRules.SHRIEK_SKILL_ID, null, extra);
    }

    /**
     * Pure refusal order before any cost: exact role, then a living participant of a running game, then the
     * SparkTraits role-skill block. Null means the cast may pay and proceed.
     * 扣费前的纯拒绝顺序：精确职业，然后是进行中对局的存活参与者，最后是 SparkTraits 职业技能封锁。null 表示可以付费并施放。
     */
    static @Nullable String refusal(boolean abyssListener, boolean livingParticipant, boolean roleSkillBlocked) {
        if (!abyssListener || !livingParticipant || roleSkillBlocked) {
            return "message.sparkwitch.skill.unavailable";
        }
        return null;
    }

    /**
     * Result of every paid cast, independent of how many players were hit: the shared path then starts the 60 s
     * cooldown. / 每次付费施放的结果，与命中人数无关：共享路径随后开始 60 秒冷却。
     */
    static WitchSkillUseResult castResult() {
        return WitchSkillUseResult.success(AbyssListenerRules.SHRIEK_COOLDOWN_TICKS);
    }

    /**
     * Pure target selection, in candidate order: never the caster, within the radius (inclusive), eligible, and not an
     * ally. Later predicates run only after earlier ones passed.
     * 纯目标选择，按候选顺序：永不包括施放者，位于半径内（含边界），符合资格，且不是队友。前序条件通过后才计算后续谓词。
     */
    static <P> List<P> selectTargets(List<P> candidates, Predicate<P> isCaster, ToDoubleFunction<P> squaredDistance,
                                     Predicate<P> eligible, Predicate<P> ally) {
        List<P> targets = new ArrayList<>();
        for (P candidate : candidates) {
            if (!isCaster.test(candidate)
                    && squaredDistance.applyAsDouble(candidate) <= RADIUS_SQUARED
                    && eligible.test(candidate)
                    && !ally.test(candidate)) {
                targets.add(candidate);
            }
        }
        return targets;
    }
}
