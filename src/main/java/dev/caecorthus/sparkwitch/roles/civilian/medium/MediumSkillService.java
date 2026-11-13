package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.compat.NoellesSilenceBridge;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaGrappleService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通灵 (the Medium's shared ability-key skill), server-authoritative, plus the parts both vote-window kinds share (the
 * vote entry, the window-end dispatch and the caster gate). The press only asks the server to open the question box;
 * the paid act is the Enter submit, which re-checks every gate, charges the live price, starts both cooldowns and
 * opens the world vote window. The dead then answer 是 / 否 from their vote screen
 * ({@link SubmitMediumVoteC2SPacket}) for 20 s; the tally ({@link MediumTally}) keeps each voter's last choice, weighs
 * a 招魂-bound voter {@link MediumRules#BIND_VOTE_WEIGHT} votes, resolves a tie with the last vote cast, and sends the
 * answer privately to the caster as {@link MediumSeanceResultS2CPacket} (a HUD card on the client, never a vanilla
 * title). When the death count is over half, the tie note and each bound dead target's own answer are included as
 * well. A 幽灵的加护 window ends in {@link MediumBlessingService}.
 * 通灵（通灵师共享技能键技能），服务端权威，并包含两种投票窗口共用的部分（投票入口、窗口结束分发与施放者门槛）。按键只请求
 * 服务端打开提问框；付费动作是回车提交，它会重新复核所有门槛、扣除实时价格、同时进入两个冷却并开启世界级投票窗口。随后死者在
 * 投票界面（{@link SubmitMediumVoteC2SPacket}）回答「是」/「否」，持续 20 秒；计票（{@link MediumTally}）保留每人的
 * 最后选择，被招魂绑定的投票记 {@link MediumRules#BIND_VOTE_WEIGHT} 票，平票以最后投出的一票定夺，答案以
 * {@link MediumSeanceResultS2CPacket} 私下发给通灵师（客户端绘制为 HUD 卡片，绝不使用原版标题）。当局死者过半时，还会附带
 * 平票提示与每个绑定死者的个人答案。幽灵的加护窗口的收尾在 {@link MediumBlessingService}。
 */
public final class MediumSkillService {
    private static boolean registered;

    private MediumSkillService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(MediumRules.SEANCE_SKILL_ID, MediumSkillService::formatSeance);
    }

    /**
     * Server entry of the 通灵 key: opens the question box with the live price. Nothing is charged here.
     * 通灵键的服务端入口：以实时价格打开提问框。此处不扣任何费用。
     */
    public static void use(ServerPlayerEntity caster) {
        if (caster == null || !mayUse(caster)) {
            return;
        }
        if (MediumSeanceComponent.KEY.get(caster.getWorld()).isActive()) {
            return;
        }
        MediumPlayerComponent component = MediumPlayerComponent.KEY.get(caster);
        if (component.getSeanceCooldownTicks() > 0) {
            return;
        }
        int price = MediumRules.seancePrice(MediumRules.currentTier(GameWorldComponent.KEY.get(caster.getWorld())));
        if (PlayerShopComponent.KEY.get(caster).getBalance() < price) {
            return;
        }
        if (deniedByFear(caster) || !ServerPlayNetworking.canSend(caster, OpenMediumSeanceInputS2CPacket.ID)) {
            return;
        }
        ServerPlayNetworking.send(caster, new OpenMediumSeanceInputS2CPacket(price));
    }

    /**
     * The Enter submit: every gate is re-checked before the charge; an empty, over-length or formatting-only question
     * is dropped without charging or starting anything.
     * 回车提交：扣费前重新复核所有门槛；空文本、超长文本或只有格式代码的提问直接丢弃，不扣费也不开启任何状态。
     */
    public static void submit(ServerPlayerEntity caster, @Nullable String rawQuestion) {
        if (caster == null || !mayUse(caster)) {
            return;
        }
        String question = MediumRules.sanitizeQuestion(rawQuestion);
        if (question == null) {
            return;
        }
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(caster.getWorld());
        if (seance.isActive()) {
            return;
        }
        MediumPlayerComponent component = MediumPlayerComponent.KEY.get(caster);
        if (component.getSeanceCooldownTicks() > 0) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        int tier = MediumRules.currentTier(game);
        int price = MediumRules.seancePrice(tier);
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(caster);
        if (shop.getBalance() < price || deniedByFear(caster)) {
            return;
        }
        if (price > 0) {
            shop.addToBalance(-price);
        }
        // A paid 通灵 starts the 加护 cooldown too (and the other way round). / 付费通灵同时开始加护冷却（反之亦然）。
        component.startCooldowns(tier);
        seance.startQuestion(caster.getUuid(), question, component.getBoundTargets());
        NbtCompound extra = new NbtCompound();
        extra.putString("question", question);
        GameRecordManager.recordSkillUse(caster, MediumRules.SEANCE_SKILL_ID, null, extra);
    }

    /**
     * A vote from the vote screen, for either window kind: accepted only inside a live window, only from a dead round
     * member who is not a Wraith ({@link MediumRules#mayVote(net.minecraft.entity.player.PlayerEntity)}), and only for
     * a choice that window offers ({@link MediumSeanceComponent#acceptsChoice}: yes/no for a question, a known and not
     * disabled blessing index for a blessing); the latest vote replaces the earlier one. Anything else is dropped
     * silently.
     * 来自投票界面的一票，两种窗口通用：只在窗口进行中、发送者是不属于冤魂的本局死者
     * （{@link MediumRules#mayVote(net.minecraft.entity.player.PlayerEntity)}）、且该窗口提供此选项
     * （{@link MediumSeanceComponent#acceptsChoice}：提问为是/否，加护为已知且未禁用的加护序号）时接受；后投的票覆盖先前的票。
     * 其余情况一律静默丢弃。
     */
    public static void vote(ServerPlayerEntity sender, int windowId, int choice) {
        if (sender == null) {
            return;
        }
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(sender.getWorld());
        // The serial pins the answer to the window its screen showed. / 序号把回答固定在界面所展示的那个窗口。
        if (!seance.isActive() || seance.getWindowId() != windowId || !MediumRules.mayVote(sender)) {
            return;
        }
        seance.recordVote(sender.getUuid(), choice);
    }

    /**
     * Window end, called by {@link MediumSeanceComponent#serverTick()} after it zeroes the timer: tally by the
     * window's kind, deliver the private result, then clear the window. The component still holds the kind, question,
     * caster and votes here.
     * 窗口结束，由 {@link MediumSeanceComponent#serverTick()} 清零计时后调用：按窗口种类计票、私下发送结果，然后清空窗口。
     * 此时组件仍持有种类、提问、施放者与票数。
     */
    static void onSeanceEnded(ServerWorld world) {
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(world);
        UUID casterId = seance.getCasterUuid();
        Set<UUID> bound = new LinkedHashSet<>(seance.getBoundTargets());
        ServerPlayerEntity caster = casterId == null
                ? null
                : world.getServer().getPlayerManager().getPlayer(casterId);
        try {
            if (caster == null) {
                return;
            }
            // A 招魂 bought while the window is open still applies to it.
            // 窗口开着时买的招魂同样对本次投票生效。
            bound.addAll(MediumPlayerComponent.KEY.get(caster).getBoundTargets());
            if (seance.getKind() == MediumWindowKind.BLESSING) {
                MediumBlessingService.onWindowEnded(world, seance, caster, bound);
            } else {
                onQuestionEnded(world, seance, caster, bound);
            }
        } finally {
            seance.clear();
        }
    }

    private static void onQuestionEnded(ServerWorld world, MediumSeanceComponent seance, ServerPlayerEntity caster,
                                        Set<UUID> bound) {
        if (!ServerPlayNetworking.canSend(caster, MediumSeanceResultS2CPacket.ID)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        String question = seance.getQuestion();
        Map<UUID, Integer> votes = seance.getVotes();
        // A voter who is no longer in the dead set at the tally is dropped, so nobody alive ever decides; the last
        // remaining vote breaks a tie.
        // 计票时已不在死亡集合中的投票者被剔除，因此活人绝不参与裁决；平票由剩余的最后一票定夺。
        MediumTally tally = MediumTally.count(votes, game::isPlayerDead, bound::contains, 2);
        if (!tally.hasWinner()) {
            ServerPlayNetworking.send(caster, MediumSeanceResultS2CPacket.ofQuestion(
                    question, MediumSeanceResultS2CPacket.OUTCOME_NO_RESPONSE, false, List.of()));
            return;
        }
        // The answer and the caster's own question always go out. The tie note and the bound targets' own answers
        // are extra knowledge, sent only once deaths are over half; bound targets who did not vote are omitted.
        // 答案与通灵师自己的提问始终发送。平票提示与绑定目标的个人答案属于额外情报，只有死者过半时才发送；
        // 没投票的招魂者不列出。
        boolean detail = MediumRules.deathsOverHalf(game);
        ServerPlayNetworking.send(caster, MediumSeanceResultS2CPacket.ofQuestion(
                question,
                tally.winner() == MediumRules.VOTE_YES
                        ? MediumSeanceResultS2CPacket.OUTCOME_YES
                        : MediumSeanceResultS2CPacket.OUTCOME_NO,
                detail && tally.tie(),
                detail ? boundAnswers(game, bound, votes) : List.of()));
    }

    /**
     * The 招魂-bound dead targets' own votes in binding order, capped for the wire; a bound target who did not vote or
     * is no longer dead is omitted. Callers send it only when deaths are over half.
     * 按绑定先后列出被招魂绑定的死者各自的投票，并按线上上限截断；没投票或已不在死亡集合中的绑定目标不列出。调用方只在
     * 死者过半时发送。
     */
    static List<MediumSeanceResultS2CPacket.BoundAnswer> boundAnswers(GameWorldComponent game, Set<UUID> bound,
                                                                     Map<UUID, Integer> votes) {
        List<MediumSeanceResultS2CPacket.BoundAnswer> answers = new ArrayList<>();
        for (UUID target : bound) {
            if (answers.size() >= MediumSeanceResultS2CPacket.MAX_BOUND_ANSWERS) {
                break;
            }
            Integer choice = votes.get(target);
            if (choice != null && game.isPlayerDead(target)) {
                answers.add(new MediumSeanceResultS2CPacket.BoundAnswer(playerName(game, target), choice));
            }
        }
        return answers;
    }

    /**
     * Shared caster gate of all three skills and their paid confirmations (the Specter/Dominator {@code mayCast}
     * set): an ACTIVE round, the raw Medium role, playing and alive, never an alive spectator (Taotie-swallowed, Rift
     * occupant) or creative, not held by a Ninja grapple, not silenced and not role-skill blocked by SparkTraits.
     * Refusals are silent. Grand Witch Fear is checked last by each entry ({@link #deniedByFear}) because it tells the
     * player why.
     * 三个技能及其付费确认共用的施放者门槛（幽魂/支配者 {@code mayCast} 的同一组条件）：对局 ACTIVE、真实职业为通灵师、
     * 参与且存活、不是存活旁观者（被饕餮吞下、裂隙门内）或创造模式、未被忍者钩爪拉住、未被沉默、未被 SparkTraits 禁用职业
     * 技能。拒绝时不提示。大魔女恐惧由各入口最后检查（{@link #deniedByFear}），因为它会提示原因。
     */
    static boolean mayUse(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && MediumRules.isMedium(game.getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator()
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && !NoellesTaotieSeekerBridge.isSwallowed(player)
                && !NinjaGrappleService.isHeld(player)
                && !NoellesSilenceBridge.isSilenced(player)
                && !SparkTraitsKillerBridge.isRoleSkillBlocked(player);
    }

    /**
     * Grand Witch Fear refusal, with the shared "skill blocked" message; call it after every silent gate.
     * 大魔女恐惧拒绝，附带共用的「技能被封锁」提示；在所有静默门槛之后调用。
     */
    static boolean deniedByFear(ServerPlayerEntity player) {
        return GrandWitchFearService.denyRoleSkillIfFeared(player);
    }

    private static String playerName(GameWorldComponent game, UUID uuid) {
        var profile = game.getGameProfiles().get(uuid);
        return profile == null ? "??" : profile.getName();
    }

    private static @Nullable Text formatSeance(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        String question = data.getString("question");
        return Text.translatable(
                "replay.skill.sparkwitch.medium_seance_skill",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match)),
                question
        );
    }
}
