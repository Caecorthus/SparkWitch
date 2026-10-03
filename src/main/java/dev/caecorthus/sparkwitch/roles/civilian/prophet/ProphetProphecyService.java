package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.net.OpenProphecyS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.ProphecyRecord;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetProphecyRules.Verdict;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.skill.WitchSkillUseService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative Prophecy flow: request → one-shot session → priced confirmation. The client only ever sees the
 * dead players' names and its own owner-synced results; the ledger's cause group and killer never leave the server
 * except as the single name revealed by a correct guess. Prophecy is a role-owned skill and never appears in the
 * Witch inventory skill panel.
 * 服务端权威的预言流程：请求 → 一次性会话 → 付费确认。客户端只会看到死者名字与自己仅同步给拥有者的结果；
 * 账本中的死因分组与凶手除猜中时揭示的那个名字外绝不离开服务端。预言是职业自有技能，绝不出现在魔女背包技能面板。
 */
public final class ProphetProphecyService {
    private static final ProphetProphecySessions SESSIONS = new ProphetProphecySessions();
    private static boolean registered;

    private ProphetProphecyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> SESSIONS.clear());
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> SESSIONS.clear());
        RoleAssigned.EVENT.register((player, role) -> SESSIONS.invalidate(player.getUuid()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SESSIONS.invalidate(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SESSIONS.clear());
    }

    /** A living, playing, non-spectating Prophet in an active round. / 处于进行中对局、存活、参与且非旁观的先知。 */
    public static boolean canProphesy(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && ProphetRules.isProphet(game.getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && !SparkWitchApi.isWraithRestricted(player);
    }

    /**
     * Opens a session listing every dead participant but the Prophet (owner decision Q3). Opening and cancelling are
     * free: nothing is charged and no cooldown starts here.
     * 打开会话，列出除先知本人外的所有死亡参与者（所有者决定 Q3）。打开与取消都免费：此处不扣费也不进入冷却。
     */
    public static void requestSession(ServerPlayerEntity player) {
        if (!canProphesy(player)) {
            actionBar(player, "unavailable");
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(player)) {
            return;
        }
        if (!WitchSkillUseService.checkDedicatedSkillReady(player, ProphetRules.PROPHECY_ID)) {
            return;
        }
        UUID match = currentMatchId();
        if (match == null || !ServerPlayNetworking.canSend(player, OpenProphecyS2CPacket.ID)) {
            actionBar(player, "unavailable");
            return;
        }
        ServerWorld world = player.getServerWorld();
        List<ProphetDeathRecord> dead = ProphetDeathLedger.deadCandidates(world).stream()
                .filter(record -> !record.victim().equals(player.getUuid()))
                .limit(OpenProphecyS2CPacket.MAX_CANDIDATES)
                .toList();
        if (dead.isEmpty()) {
            actionBar(player, "no_dead");
            return;
        }
        Optional<ProphetProphecySessions.Session> session = SESSIONS.open(
                player.getUuid(), match, dead.stream().map(ProphetDeathRecord::victim).toList(), now(player));
        if (session.isEmpty()) {
            return;
        }
        // Names only: the cause group and killer stay in the server ledger. / 只发送名字：死因分组与凶手留在服务端账本。
        List<OpenProphecyS2CPacket.Candidate> candidates = dead.stream()
                .map(record -> new OpenProphecyS2CPacket.Candidate(record.victim(), packetName(record)))
                .toList();
        try {
            ServerPlayNetworking.send(player, new OpenProphecyS2CPacket(session.get().nonce(), candidates));
        } catch (RuntimeException error) {
            SESSIONS.invalidate(player.getUuid());
            throw error;
        }
    }

    /**
     * Consumes the session nonce, re-checks everything the request checked plus the guess itself, then charges 50
     * coins, records the outcome on the owner-synced component and starts the shared 30 s cooldown. Any refusal
     * charges nothing and starts no cooldown.
     * 消费会话标识，重新检查请求时的全部条件以及猜测本身，然后扣除 50 金币、把结果写入仅同步给拥有者的组件，
     * 并启动共享的 30 秒冷却。任何拒绝都不扣费、不进入冷却。
     */
    public static void confirmGuess(ServerPlayerEntity player, UUID nonce, UUID victim, String groupId) {
        Optional<ProphetProphecySessions.Session> session = SESSIONS.consume(
                player.getUuid(), nonce, currentMatchId(), now(player));
        if (session.isEmpty()) {
            actionBar(player, "no_pending");
            return;
        }
        if (!canProphesy(player)) {
            actionBar(player, "unavailable");
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(player)) {
            return;
        }
        if (!WitchSkillUseService.checkDedicatedSkillReady(player, ProphetRules.PROPHECY_ID)) {
            return;
        }
        if (victim == null || !session.get().victims().contains(victim)) {
            actionBar(player, "invalid_target");
            return;
        }
        ServerWorld world = player.getServerWorld();
        // Still dead and still in the ledger right now, not just when the session opened.
        // 必须此刻仍处于死亡状态且仍在账本中，而不仅是会话开启时。
        ProphetDeathRecord death = ProphetDeathLedger.deadCandidates(world).stream()
                .filter(record -> record.victim().equals(victim))
                .findFirst()
                .orElse(null);
        ProphetPlayerComponent component = ProphetPlayerComponent.KEY.get(player);
        ProphecyRecord existing = component.prophecy(victim).orElse(null);
        ProphetDeathCauseGroup guess = ProphetDeathCauseGroup.byId(groupId).orElse(null);
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        Verdict verdict = ProphetProphecyRules.evaluate(player.getUuid(), death, existing, guess, shop.getBalance());
        if (!verdict.isGuess()) {
            if (verdict == Verdict.NOT_ENOUGH_MONEY) {
                actionBar(player, verdict.messageKey(), ProphetRules.PROPHECY_COIN_COST);
            } else {
                actionBar(player, verdict.messageKey());
            }
            return;
        }

        shop.addToBalance(-ProphetRules.PROPHECY_COIN_COST);
        Text cause = Text.translatable(guess.translationKey());
        Text result;
        if (verdict == Verdict.WRONG) {
            component.recordWrongGuess(victim, death.victimName(), guess);
            result = Text.translatable(verdict.messageKey(), death.victimName(), cause);
        } else {
            String killer = ProphetProphecyRules.revealedKiller(death);
            component.recordCorrectGuess(victim, death.victimName(), killer);
            result = killer == null
                    ? Text.translatable(verdict.messageKey(), death.victimName(), cause)
                    : Text.translatable(verdict.messageKey(), death.victimName(), cause, killer);
        }
        WitchSkillUseService.startDedicatedSkillCooldown(player, ProphetRules.PROPHECY_ID);
        // Private chat line and sound: nobody else learns that a Prophecy happened. / 私有聊天与音效：其他人不会得知发生了预言。
        player.sendMessage(result, false);
        if (verdict.isCorrect()) {
            player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        } else {
            player.playSoundToPlayer(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6F, 0.8F);
        }
    }

    private static String packetName(ProphetDeathRecord record) {
        String name = record.victimName();
        if (name == null || name.isBlank()) {
            name = record.victim().toString().substring(0, 8);
        }
        return name.length() > OpenProphecyS2CPacket.MAX_NAME_LENGTH
                ? name.substring(0, OpenProphecyS2CPacket.MAX_NAME_LENGTH) : name;
    }

    private static long now(ServerPlayerEntity player) {
        return player.getServerWorld().getServer().getTicks();
    }

    private static @Nullable UUID currentMatchId() {
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return GameRecordManager.hasActiveMatch() && match != null ? match.getMatchId() : null;
    }

    private static void actionBar(ServerPlayerEntity player, String key, Object... arguments) {
        String translationKey = key.startsWith(ProphetProphecyRules.MESSAGE_PREFIX)
                ? key : ProphetProphecyRules.MESSAGE_PREFIX + key;
        player.sendMessage(Text.translatable(translationKey, arguments), true);
    }
}
