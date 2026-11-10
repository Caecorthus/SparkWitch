package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通灵 (the Medium's shared ability-key skill), server-authoritative. The press only asks the server to open the
 * question box; the paid act is the Enter submit, which re-checks every gate, charges the live price, starts the
 * cooldown and opens the world vote window. The dead then answer 1 (是) / 2 (否) in chat for 20 s; the tally keeps each
 * voter's last choice, weighs a 招魂-bound voter {@link MediumRules#BIND_VOTE_WEIGHT} votes, resolves a tie with the
 * last vote cast, and sends the answer privately to the caster. When the death count is over half, each bound dead
 * target's own answer is revealed to the caster as well.
 * 通灵（通灵师共享技能键技能），服务端权威。按键只请求服务端打开提问框；付费动作是回车提交，它会重新复核所有门槛、
 * 扣除实时价格、进入冷却并开启世界级投票窗口。随后死者在聊天中发送 1（是）/ 2（否）投票 20 秒；计票保留每人的最后
 * 选择，被招魂绑定的投票记 {@link MediumRules#BIND_VOTE_WEIGHT} 票，平票以最后投出的一票定夺，答案私下发给通灵师。
 * 当局死者过半时，还会额外把每个绑定死者的个人答案单独给通灵师看。
 */
public final class MediumSkillService {
    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_STAY = 100;
    private static final int TITLE_FADE_OUT = 20;
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
        if (shop.getBalance() < price) {
            return;
        }
        if (price > 0) {
            shop.addToBalance(-price);
        }
        component.startSeanceCooldown(MediumRules.cooldownTicks(MediumRules.CD_AFTER_USE_TICKS, tier));
        seance.start(caster.getUuid(), question, component.getBoundTargets());
        NbtCompound extra = new NbtCompound();
        extra.putString("question", question);
        GameRecordManager.recordSkillUse(caster, MediumRules.SEANCE_SKILL_ID, null, extra);
    }

    /**
     * Server-only chat vote: a dead round member's latest "1"/"2" inside the window. Living players and strangers are
     * ignored; the message itself is not cancelled.
     * 仅服务端的聊天投票：窗口内死者发送的最新的「1」/「2」。存活玩家与局外玩家忽略；聊天消息本身不拦截。
     */
    public static void onChatMessage(ServerPlayerEntity sender, @Nullable String content) {
        if (sender == null || content == null) {
            return;
        }
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(sender.getWorld());
        if (!seance.isActive()) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(sender.getWorld());
        if (!game.isRunning() || !game.hasAnyRole(sender) || !game.isPlayerDead(sender.getUuid())) {
            return;
        }
        String vote = content.strip();
        if ("1".equals(vote)) {
            seance.recordVote(sender.getUuid(), true);
        } else if ("2".equals(vote)) {
            seance.recordVote(sender.getUuid(), false);
        }
    }

    /**
     * Window end, called by {@link MediumSeanceComponent#serverTick()} after it zeroes the timer: tally, deliver the
     * private answer, then clear the window. The component still holds the question, caster and votes here.
     * 窗口结束，由 {@link MediumSeanceComponent#serverTick()} 清零计时后调用：计票、私下发送答案，然后清空窗口。
     * 此时组件仍持有提问、施放者与票数。
     */
    static void onSeanceEnded(ServerWorld world) {
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(world);
        UUID casterId = seance.getCasterUuid();
        String question = seance.getQuestion();
        Map<UUID, Boolean> votes = seance.getVotes();
        UUID lastVoter = seance.getLastVoter();
        Set<UUID> bound = new HashSet<>(seance.getBoundTargets());
        ServerPlayerEntity caster = casterId == null
                ? null
                : world.getServer().getPlayerManager().getPlayer(casterId);
        try {
            if (caster == null) {
                return;
            }
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            // A 招魂 bought while the window is open still applies to it.
            // 窗口开着时买的招魂同样对本次通灵生效。
            bound.addAll(MediumPlayerComponent.KEY.get(caster).getBoundTargets());
            Text questionLine = Text.translatable("message.sparkwitch.medium.seance.title", question)
                    .formatted(Formatting.GRAY);
            if (votes.isEmpty()) {
                sendTitle(caster,
                        Text.translatable("message.sparkwitch.medium.seance.no_response.title"),
                        null);
                return;
            }
            int yes = 0;
            int no = 0;
            for (Map.Entry<UUID, Boolean> entry : votes.entrySet()) {
                int weight = bound.contains(entry.getKey()) ? MediumRules.BIND_VOTE_WEIGHT : 1;
                if (entry.getValue()) {
                    yes += weight;
                } else {
                    no += weight;
                }
            }
            boolean tie = yes == no;
            Boolean lastChoice = lastVoter == null ? null : votes.get(lastVoter);
            boolean answer = tie ? Boolean.TRUE.equals(lastChoice) : yes > no;
            // Players in a round cannot see chat, so the answer arrives as a title: the big word is the answer.
            // The subtitle (question, tie note, bound-target answers) only appears once deaths are over half;
            // bound targets who did not vote are simply omitted.
            // 对局中玩家看不到聊天栏，因此答案以大标题呈现。只有死者过半时才显示副标题（问题、平票提示、
            // 绑定目标的个人答案）；没投票的招魂者不显示。
            MutableText subtitle = null;
            if (MediumRules.deathsOverHalf(game)) {
                subtitle = questionLine.copy();
                if (tie) {
                    subtitle.append(Text.literal(" ")).append(Text.translatable("message.sparkwitch.medium.seance.tie"));
                }
                for (UUID target : bound) {
                    if (game.isPlayerDead(target) && votes.containsKey(target)) {
                        subtitle.append(Text.literal(" ｜ ")).append(Text.translatable(
                                "message.sparkwitch.medium.seance.bound",
                                playerName(game, target),
                                answerText(votes.get(target))));
                    }
                }
            }
            sendTitle(caster, answerText(answer), subtitle);
        } finally {
            seance.clear();
        }
    }

    /**
     * Shared caster gate: an ACTIVE round, playing and alive, exactly the Medium.
     * 共用施放者门槛：对局 ACTIVE、参与且存活、职业恰为通灵师。
     */
    static boolean mayUse(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && MediumRules.isMedium(game.getRole(player));
    }

    private static Text answerText(boolean yes) {
        return Text.translatable(yes
                ? "message.sparkwitch.medium.answer.yes"
                : "message.sparkwitch.medium.answer.no");
    }

    private static String playerName(GameWorldComponent game, UUID uuid) {
        var profile = game.getGameProfiles().get(uuid);
        return profile == null ? "??" : profile.getName();
    }

    /** Private title delivery (round players cannot read chat). / 私发标题（对局中玩家看不到聊天栏）。 */
    private static void sendTitle(ServerPlayerEntity player, Text title, @Nullable Text subtitle) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT));
        if (subtitle != null) {
            player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        }
        player.networkHandler.sendPacket(new TitleS2CPacket(title));
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
