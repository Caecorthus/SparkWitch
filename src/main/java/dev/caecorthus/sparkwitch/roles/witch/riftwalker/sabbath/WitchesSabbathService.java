package dev.caecorthus.sparkwitch.roles.witch.riftwalker.sabbath;

import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordDashService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerStatusProbes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Witches' Sabbath (魔女集会, plan §10): the Riftwalker's 150-mana, instant, 30 s-cooldown skill on the shared witch-skill
 * path ({@code SparkWitchBuiltInSkills}, exact-role selector). The handler validates the caster, finds free living
 * witch-faction teammates (C6; never those inside a gate, D6), spends the mana only when at least one can be pulled, and
 * places each on its own safe spot around the caster. Server only. Owned by P5.
 * 魔女集会（plan §10）：隙行者在共享魔女技能路径上的 150 魔力、瞬发、冷却 30 秒的技能（{@code SparkWitchBuiltInSkills}，
 * 精确职业选择器）。处理器校验施法者，找出可召集的存活魔女阵营队友（C6；门内的除外，D6），至少能拉到一人时才扣魔力，
 * 并把每人放到施法者周围各自的安全落点。仅服务端。归属 P5。
 */
public final class WitchesSabbathService {
    /** Replay payload: the pulled players' UUIDs (int arrays). / 回放数据：被召集玩家的 UUID（int 数组）。 */
    static final String REPLAY_PULLED = "pulled";
    private static boolean registered;

    private WitchesSabbathService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(RiftwalkerRules.SABBATH_SKILL_ID, WitchesSabbathService::formatReplay);
    }

    /**
     * Frozen skill handler ({@code WitchSkillDefinition.useHandler}). The shared path already checked the SparkWitch
     * role, life, Fear and readiness, but it neither spends {@code manaCost} nor asks SparkTraits, so this handler
     * re-validates everything server-side, in this order and with no cost on any refusal: caster state → at least one
     * teammate → enough mana → at least one safe spot → spend 150 → teleport. Success returns
     * {@code success(SABBATH_COOLDOWN_TICKS)} (30 s); a refusal starts no cooldown.
     * 冻结的技能处理器（{@code WitchSkillDefinition.useHandler}）。共享路径已检查 SparkWitch 职业、存活、恐惧与就绪，但既不扣除
     * {@code manaCost} 也不询问 SparkTraits，因此本处理器在服务端按以下顺序重新校验，任何拒绝都不产生代价：施放者状态 →
     * 至少一名队友 → 魔力足够 → 至少一个安全落点 → 扣除 150 → 传送。成功返回 {@code success(SABBATH_COOLDOWN_TICKS)}（30 秒）；任何拒绝都不进入冷却。
     */
    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        ServerPlayerEntity caster = context.player();
        ServerWorld world = context.world();
        GameWorldComponent game = context.gameComponent();
        WitchesSabbathRules.CasterRefusal refusal =
                WitchesSabbathRules.casterRefusal(WitchesSabbathTargets.caster(caster, context.role(), game));
        if (refusal != null) {
            return WitchSkillUseResult.fail(refusal.messageKey());
        }
        List<ServerPlayerEntity> targets = WitchesSabbathTargets.collect(world, caster, game);
        if (targets.isEmpty()) {
            return WitchSkillUseResult.fail(WitchesSabbathRules.NO_TARGETS);
        }
        WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(caster);
        if (!WitchesSabbathRules.canAfford(mana.hasManaSystem(), mana.getMana())) {
            return WitchSkillUseResult.fail(WitchesSabbathRules.NOT_ENOUGH_MANA);
        }
        List<WitchesSabbathLandingPlan.Landing<ServerPlayerEntity>> landings =
                WitchesSabbathLanding.plan(world, caster, targets);
        if (landings.isEmpty()) {
            return WitchSkillUseResult.fail(WitchesSabbathLanding.castsFromDoorway(world, caster)
                    ? WitchesSabbathRules.IN_DOORWAY
                    : WitchesSabbathRules.NO_SPACE);
        }
        if (!mana.spendMana(RiftwalkerRules.SABBATH_MANA_COST)) {
            return WitchSkillUseResult.fail(WitchesSabbathRules.NOT_ENOUGH_MANA);
        }
        List<ServerPlayerEntity> pulled = new ArrayList<>();
        try {
            pull(world, caster, landings, pulled);
        } finally {
            if (pulled.isEmpty()) {
                // Nobody actually moved (no spot held, or a third-party teleport hook threw before anyone arrived):
                // refund, as if no spot had been found. An exception still propagates after the refund.
                // 实际无人移动（落点失效，或第三方传送钩子在任何人到达前抛出异常）：退还魔力，等同于没有落点。
                // 异常在退还后照常抛出。
                mana.addMana(RiftwalkerRules.SABBATH_MANA_COST);
            }
        }
        if (pulled.isEmpty()) {
            return WitchSkillUseResult.fail(WitchesSabbathRules.NO_SPACE);
        }
        WitchesSabbathCues.circle(world, caster.getPos());
        WitchesSabbathCues.notifyCaster(caster, pulled.size(), targets.size() - pulled.size());
        record(caster, pulled);
        return WitchSkillUseResult.success(RiftwalkerRules.SABBATH_COOLDOWN_TICKS);
    }

    /**
     * Teleports each planned teammate with the 7-arg {@code ServerPlayerEntity.teleport} (it dismounts and wakes the
     * player and moves the body at once; the 6-arg overload would reset the camera holder), re-locks a capture stun at
     * the landing spot (D19), then clears velocity and fall distance and faces the caster. Each arrival is appended to
     * {@code pulled} as soon as it happens, so the caller's refund sees exactly who moved even if a later teleport
     * throws.
     * 以 7 参数的 {@code ServerPlayerEntity.teleport} 传送每名已规划的队友（会先下坐骑、叫醒，并立即移动本体；6 参数重载会
     * 重置镜头持有者），在落点重新锁定捕捉眩晕（D19），随后清除速度与摔落距离，并面向施放者。每名到达者立即加入
     * {@code pulled}，即使之后的传送抛出异常，调用方的退还判断也能准确知道谁已移动。
     */
    private static void pull(ServerWorld world, ServerPlayerEntity caster,
                             List<WitchesSabbathLandingPlan.Landing<ServerPlayerEntity>> landings,
                             List<ServerPlayerEntity> pulled) {
        Vec3d casterPos = caster.getPos();
        for (WitchesSabbathLandingPlan.Landing<ServerPlayerEntity> landing : landings) {
            ServerPlayerEntity target = landing.target();
            Box departure = target.getBoundingBox();
            Vec3d feet = landing.feet();
            float yaw = WitchesSabbathLandingPlan.yawToward(feet, casterPos);
            if (!target.teleport(world, feet.x, feet.y, feet.z, Set.of(), yaw, 0.0F)) {
                continue;
            }
            pulled.add(target);
            // D19: a capture-stunned teammate stays held, now at the landing spot (a free one is untouched).
            // D19：被捕捉眩晕的队友保持定身，锁定点移到落点（未眩晕的队友不受影响）。
            RiftwalkerStatusProbes.relockCaptureStun(target);
            // A Grand Witch pulled mid-dash must not keep dashing from the landing spot. / 被召集的大魔女不得从落点继续冲刺。
            CeremonialSwordDashService.cancel(target);
            target.setVelocity(Vec3d.ZERO);
            target.velocityModified = true;
            target.fallDistance = 0.0F;
            WitchesSabbathCues.departure(world, departure);
            WitchesSabbathCues.arrival(world, landing.body());
            WitchesSabbathCues.notifySummoned(target, caster);
        }
    }

    /** One replay line per cast with the pulled names; never a global announcement. / 每次施放一行回放，含被召集者名单；从不全局公告。 */
    private static void record(ServerPlayerEntity caster, List<ServerPlayerEntity> pulled) {
        NbtList uuids = new NbtList();
        for (ServerPlayerEntity player : pulled) {
            uuids.add(NbtHelper.fromUuid(player.getUuid()));
        }
        NbtCompound extra = new NbtCompound();
        extra.put(REPLAY_PULLED, uuids);
        GameRecordManager.recordSkillUse(caster, RiftwalkerRules.SABBATH_SKILL_ID, null, extra);
    }

    private static @Nullable Text formatReplay(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                               ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        List<Text> names = new ArrayList<>();
        for (NbtElement element : data.getList(REPLAY_PULLED, NbtElement.INT_ARRAY_TYPE)) {
            try {
                names.add(ReplayGenerator.formatPlayerName(NbtHelper.toUuid(element), playerInfo));
            } catch (IllegalArgumentException ignored) {
                // A malformed entry is dropped, never the whole line. / 丢弃格式错误的条目，而不是整行。
            }
        }
        return Text.translatable(WitchesSabbathRules.REPLAY,
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                Texts.join(names, Text.literal(", ")));
    }
}
