package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.NoellesHiddenBodiesBridge;
import dev.caecorthus.sparkwitch.compat.NoellesSilenceBridge;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkStrengthPartygoerCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.net.OpenSherryDeductionS2CPacket;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaGrappleService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 推演 (橘雪莉's shared ability-key skill), server-authoritative. The press only asks the server to open the candidate
 * panel; the paid act is the confirmed pick, which re-checks every gate, charges {@link SherryRules#DEDUCTION_COST}
 * and starts the cooldown whatever the outcome. A target on the killer faction is a correct deduction and is revealed
 * to her alone for {@link SherryRules#REVEAL_TICKS} every {@link SherryRules#DEDUCTION_REVEAL_INTERVAL_TICKS}; anyone
 * else is a failure (owner rule 2026-11-11) that costs sanity plus Blindness and Slowness II. Nobody is ever killed and
 * the picked player is never told.
 * 推演（橘雪莉的共享技能键技能），服务端权威。按键只请求服务端打开候选面板；付费动作是确认选择，它会重新复核所有门槛、
 * 扣除 {@link SherryRules#DEDUCTION_COST} 金币，并且无论结果如何都进入冷却。选中杀手阵营的目标为推演正确，该目标每
 * {@link SherryRules#DEDUCTION_REVEAL_INTERVAL_TICKS} 刻只对她高亮 {@link SherryRules#REVEAL_TICKS} 刻；其余目标一律失败
 * （所有者规则 2026-11-11），扣理智并附加失明与缓慢 II。推演从不击杀任何人，被选玩家不会收到任何提示。
 */
public final class SherryDeductionService {
    private static final String OUTCOME_KEY = "outcome";
    private static final String OUTCOME_RIGHT = "right";
    private static final String OUTCOME_WRONG = "wrong";

    private static boolean registered;

    private SherryDeductionService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(SherryRules.DEDUCTION_SKILL_ID, SherryDeductionService::formatDeduction);
    }

    /**
     * Server entry of the shared ability key: opens the candidate panel. Nothing is charged here.
     * 共享技能键的服务端入口：打开候选面板。此处不扣任何费用。
     */
    public static void requestOpen(ServerPlayerEntity detective) {
        if (detective == null || !mayUse(detective)) {
            return;
        }
        if (!passesToldGates(detective) || GrandWitchFearService.denyRoleSkillIfFeared(detective)) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity other : detective.getServerWorld().getPlayers()) {
            if (ids.size() >= SherryRules.MAX_CANDIDATES) {
                break;
            }
            if (isCandidate(detective, other)) {
                ids.add(other.getUuid());
                names.add(other.getGameProfile().getName());
            }
        }
        if (ids.isEmpty()) {
            tell(detective, Text.translatable("message.sparkwitch.sherry.deduction.no_target"), Formatting.GRAY);
            return;
        }
        if (ServerPlayNetworking.canSend(detective, OpenSherryDeductionS2CPacket.ID)) {
            ServerPlayNetworking.send(detective,
                    new OpenSherryDeductionS2CPacket(ids, names, SherryRules.DEDUCTION_COST));
        }
    }

    /**
     * The confirmed pick: every gate is re-checked before the charge, so a stale panel can never buy a deduction the
     * key itself would refuse.
     * 确认选择：扣费前重新复核所有门槛，因此过期的面板无法买到按键本身会拒绝的推演。
     */
    public static void confirm(ServerPlayerEntity detective, @Nullable UUID targetUuid) {
        if (detective == null || targetUuid == null || !mayUse(detective)) {
            return;
        }
        if (!passesToldGates(detective)) {
            return;
        }
        ServerPlayerEntity target = detective.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target == null || !isCandidate(detective, target)) {
            tell(detective, Text.translatable("message.sparkwitch.sherry.deduction.no_target"), Formatting.GRAY);
            return;
        }
        SherryComponent component = SherryComponent.KEY.get(detective);
        // A target already on the 20 s cycle (or faster) has nothing left to buy: refuse before the charge, so a
        // misclick on a known killer costs nothing. A badge killer on the 40 s cycle may still be deduced.
        // 已处于 20 秒周期（或更快）的目标没有可再购买的内容：扣费前拒绝，误点已知的杀手不花钱。处于 40 秒周期的徽章凶手
        // 仍可被推演。
        int trackedInterval = component.getTrackedIntervalTicks(target.getUuid());
        if (trackedInterval > 0 && trackedInterval <= SherryRules.DEDUCTION_REVEAL_INTERVAL_TICKS) {
            tell(detective, Text.translatable("message.sparkwitch.sherry.deduction.tracked",
                    target.getGameProfile().getName()), Formatting.GRAY);
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(detective)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(detective.getWorld());
        boolean correct = SherryRules.isCorrectDeduction(SparkFactionApi.resolveEffectiveFaction(target, game));

        PlayerShopComponent.KEY.get(detective).addToBalance(-SherryRules.DEDUCTION_COST);
        component.startDeductionCooldown(SherryRules.DEDUCTION_COOLDOWN_TICKS);

        if (correct) {
            component.track(target.getUuid(), SherryRules.DEDUCTION_REVEAL_INTERVAL_TICKS);
            tell(detective, Text.translatable("message.sparkwitch.sherry.deduction.right",
                    target.getGameProfile().getName()), Formatting.AQUA);
        } else {
            PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(detective);
            mood.setMood(mood.getMood() - SherryRules.WRONG_TARGET_MOOD_LOSS);
            detective.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,
                    SherryRules.WRONG_TARGET_EFFECT_TICKS, 0, false, false, false));
            detective.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                    SherryRules.WRONG_TARGET_EFFECT_TICKS, SherryRules.WRONG_TARGET_SLOWNESS_AMPLIFIER,
                    false, false, false));
            tell(detective, Text.translatable("message.sparkwitch.sherry.deduction.wrong",
                    target.getGameProfile().getName()), Formatting.RED);
        }
        NbtCompound extra = new NbtCompound();
        extra.putString(OUTCOME_KEY, correct ? OUTCOME_RIGHT : OUTCOME_WRONG);
        GameRecordManager.recordSkillUse(detective, SherryRules.DEDUCTION_SKILL_ID, target, extra);
    }

    /**
     * Shared caster gate of 推演, 巨力魔法 and the badge hand-over (the Medium/Specter {@code mayUse} set): an ACTIVE
     * round, the raw role, playing and alive, never an alive spectator (Taotie-swallowed, Rift occupant) or creative,
     * not held by a Ninja grapple, not silenced and not role-skill blocked by SparkTraits. Refusals are silent; Grand
     * Witch Fear is checked last by each entry because it tells the player why.
     * 推演、巨力魔法与赠送徽章共用的施放者门槛（通灵师/幽魂 {@code mayUse} 的同一组条件）：对局 ACTIVE、真实职业、参与且存活、
     * 不是存活旁观者（被饕餮吞下、裂隙门内）或创造模式、未被忍者钩爪拉住、未被沉默、未被 SparkTraits 禁用职业技能。拒绝时不提示；
     * 大魔女恐惧由各入口最后检查，因为它会提示原因。
     */
    static boolean mayUse(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && SherryRules.isSherry(game.getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator()
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && !NoellesTaotieSeekerBridge.isSwallowed(player)
                && !NinjaGrappleService.isHeld(player)
                && !NoellesSilenceBridge.isSilenced(player)
                && !SparkTraitsKillerBridge.isRoleSkillBlocked(player);
    }

    /**
     * The three refusals she is told about, in order: no corpse, cooldown, coins. Returns true when none applies.
     * 会告知原因的三种拒绝，依次为：没有尸体、冷却中、金币不足。都不成立时返回 true。
     */
    private static boolean passesToldGates(ServerPlayerEntity detective) {
        if (!aimsAtCorpse(detective)) {
            tell(detective, Text.translatable("message.sparkwitch.sherry.deduction.no_body"), Formatting.GRAY);
            return false;
        }
        int cooldown = SherryComponent.KEY.get(detective).getDeductionCooldownTicks();
        if (cooldown > 0) {
            tell(detective, Text.translatable("message.sparkwitch.sherry.deduction.cooldown",
                    (cooldown + 19) / 20), Formatting.GRAY);
            return false;
        }
        if (PlayerShopComponent.KEY.get(detective).getBalance() < SherryRules.DEDUCTION_COST) {
            tell(detective, Text.translatable("message.sparkwitch.sherry.deduction.no_coins",
                    SherryRules.DEDUCTION_COST), Formatting.RED);
            return false;
        }
        return true;
    }

    /**
     * A candidate is another living round player in this world inside reach who is physically present: an alive
     * spectator (Taotie-swallowed, Rift occupant) is not on the list, so the panel never tells where nobody stands.
     * 候选是本世界射程内另一名存活的本局玩家，且必须确实在场：存活旁观者（被饕餮吞下、裂隙门内）不在名单上，
     * 面板不会透露并不在场的人。
     */
    static boolean isCandidate(ServerPlayerEntity detective, ServerPlayerEntity other) {
        return other != detective
                && other.getServerWorld() == detective.getServerWorld()
                && GameFunctions.isPlayerPlayingAndAlive(other)
                && !other.isSpectator()
                && !GameFunctions.isPlayerSpectatingOrCreative(other)
                && detective.squaredDistanceTo(other) <= SherryRules.DEDUCTION_RANGE * SherryRules.DEDUCTION_RANGE;
    }

    /**
     * A Wathe corpse in front of her (see {@link SherryRules#aimsAtCorpse}) that she can actually see: no block may
     * stand between her eyes and the body, so a corpse behind a wall never counts. Hidden bodies and a Partygoer's
     * corpse never count either (Perfumer precedent).
     * 她面前有一具她确实看得到的 Wathe 尸体（见 {@link SherryRules#aimsAtCorpse}）：眼睛与尸体之间不能有方块阻挡，
     * 因此隔墙的尸体不算。被隐藏的尸体与派对客的尸体同样不算（调香师先例）。
     */
    static boolean aimsAtCorpse(ServerPlayerEntity detective) {
        ServerWorld world = detective.getServerWorld();
        Vec3d eye = detective.getEyePos();
        Vec3d look = detective.getRotationVec(1.0F);
        Vec3d feet = detective.getPos();
        return !world.getEntitiesByClass(
                PlayerBodyEntity.class,
                detective.getBoundingBox().expand(SherryRules.CORPSE_REACH + 1.0D),
                body -> !NoellesHiddenBodiesBridge.isHidden(world, body.getPlayerUuid())
                        && !SparkStrengthPartygoerCompat.isPartygoerCorpse(body)
                        && SherryRules.aimsAtCorpse(eye, look, feet, body.getBoundingBox().getCenter())
                        && hasLineOfSight(detective, eye, body)
        ).isEmpty();
    }

    /**
     * Two block rays from her eyes, to the body's centre and to a point just above it, so floor trim, a carpet or a
     * bed edge under the corpse does not hide it; either clear ray is enough.
     * 从她的眼睛发出两条方块射线，分别指向尸体中心与其正上方一点，这样尸体下的地板边缘、地毯或床沿不会把它挡住；
     * 任意一条畅通即可。
     */
    private static boolean hasLineOfSight(ServerPlayerEntity detective, Vec3d eye, PlayerBodyEntity body) {
        Vec3d center = body.getBoundingBox().getCenter();
        return isClear(detective, eye, center) || isClear(detective, eye, center.add(0.0D, 0.5D, 0.0D));
    }

    private static boolean isClear(ServerPlayerEntity detective, Vec3d from, Vec3d to) {
        return detective.getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, detective)).getType() == HitResult.Type.MISS;
    }

    private static void tell(ServerPlayerEntity player, Text text, Formatting formatting) {
        player.sendMessage(text.copy().formatted(formatting), true);
    }

    private static @Nullable Text formatDeduction(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(
                OUTCOME_RIGHT.equals(data.getString(OUTCOME_KEY))
                        ? "replay.skill.sparkwitch.sherry_deduction.right"
                        : "replay.skill.sparkwitch.sherry_deduction.wrong",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo)
        );
    }
}
