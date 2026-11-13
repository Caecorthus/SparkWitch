package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.util.TickTime;
import dev.doctor4t.wathe.api.event.CanTargetBody;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of Koko's two streams and of every timer in {@link KokoComponent}:
 * <ul>
 *   <li>{@link #tryGoLive}: gates the {@code sparkwitch:use_koko_stream} request ({@link KokoRules#streamVerdict}),
 *   spends the mana, starts the 20 s stream, pays the first second of traffic and puts the vanilla Glowing effect on
 *   her for 10 s, which is how every other player sees her through walls. A refused request changes nothing.</li>
 *   <li>The corpse stream is a vanilla right-click on a Wathe body ({@code UseEntityCallback}, main hand): no mana,
 *   Glowing on the body for 50 s, +100 traffic, once per dead player, and the shared cooldown. Her own client answers
 *   SUCCESS for any body so the held item is never used by the same click; the server alone decides.</li>
 *   <li>The world tick pays a live second's traffic, ends a stream that ran out or that her death, a role change or
 *   the round's end cut short, and renews Speed III while she holds 1000 traffic.</li>
 * </ul>
 * Client/server authority: the client only sends the empty request or the vanilla interaction; role, round, control
 * states, cooldown, Fear and mana are all checked here.
 * 可可两种直播与 {@link KokoComponent} 中全部计时的服务端部分：
 * {@link #tryGoLive} 判定 {@code sparkwitch:use_koko_stream} 请求（{@link KokoRules#streamVerdict}）、扣除魔力、开始 20 秒
 * 直播、结算第一秒流量，并给她挂上 10 秒原版发光效果——其他玩家正是靠它隔墙看到她；被拒绝的请求不改变任何东西。
 * 尸体直播是对 Wathe 尸体的一次原版右键（{@code UseEntityCallback}，主手）：不耗魔力，尸体发光 50 秒，+100 流量，每名死者
 * 一次，并进入共用冷却。她自己的客户端对任何尸体都回答 SUCCESS，使同一次点击不会再使用手中物品；是否生效只由服务端决定。
 * 世界刻负责结算直播每秒的流量，结束已到时或因死亡、换职业、对局结束而中断的直播，并在流量达到 1000 时续上速度 III。
 * 客户端/服务端权威：客户端只发送空请求或原版交互；职业、回合、受控状态、冷却、恐惧与魔力全部在这里校验。
 */
public final class KokoStreamService {
    /**
     * Runs after the default phase of {@code UseEntityCallback}, so every lock guard (stun, hypnosis, Rift Gate,
     * Kidnapper control, Knight duel seal, opening safe time, Taotie daze) has already refused a locked player's click
     * before the corpse stream sees it.
     * 排在 {@code UseEntityCallback} 默认阶段之后，因此所有锁定拦截（眩晕、催眠、裂隙门、绑匪控制、骑士决斗封印、开局安全
     * 时间、饕餮眩晕）都已先行拒绝被锁定玩家的点击，尸体直播才会看到它。
     */
    static final Identifier CORPSE_PHASE = SparkWitch.id("koko_corpse_stream");

    private KokoStreamService() {
    }

    static void register() {
        UseEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, CORPSE_PHASE);
        UseEntityCallback.EVENT.register(CORPSE_PHASE, (player, world, hand, entity, hit) ->
                onUseEntity(player, world, hand, entity));
        ServerTickEvents.END_WORLD_TICK.register(KokoStreamService::tick);
    }

    /**
     * Called on the server thread for every go-live request. The payload policy guards already drop most refused
     * requests; this re-checks every gate against server state.
     * 每个开播请求都在服务端线程调用。数据包策略拦截已丢弃大多数被拒请求；此处按服务端状态重新检查每个门槛。
     */
    public static void tryGoLive(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        KokoComponent state = KokoComponent.KEY.get(player);
        WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(player);
        KokoRules.StreamVerdict verdict = KokoRules.streamVerdict(
                KokoRules.isKoko(game.getRole(player)),
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator(),
                isControlled(player),
                state.isLive(),
                state.cooldownRemaining(now) <= 0,
                GrandWitchFearService.isPlayerFeared(player),
                mana.hasManaSystem() && mana.getMana() >= KokoRules.STREAM_MANA);
        switch (verdict) {
            case USE -> {
                // spendMana re-checks the balance; a false answer is the same refusal as NO_MANA.
                // spendMana 会再次核对余额；返回 false 与 NO_MANA 是同一种拒绝。
                if (mana.spendMana(KokoRules.STREAM_MANA)) {
                    goLive(player, state, now);
                } else {
                    tellNoMana(player);
                }
            }
            case NO_MANA -> tellNoMana(player);
            case FEARED -> GrandWitchFearService.sendSkillBlocked(player);
            case REFUSE -> {
            }
        }
    }

    private static void goLive(ServerPlayerEntity player, KokoComponent state, long now) {
        state.startStream(now);
        // The first live second (see KokoRules#pulseDue). / 直播的第一秒（见 KokoRules#pulseDue）。
        state.addTraffic(KokoRules.trafficPerSecond(state.isCheering(now)));
        // Everyone sees her through walls for 10 s: vanilla Glowing, the Nikaido Hiro precedent. The icon stays on so
        // she knows she is exposed. / 所有人隔墙看到她 10 秒：原版发光，沿用二阶堂希罗的先例。保留图标，让她知道自己已暴露。
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, KokoRules.EXPOSED_TICKS, 0,
                false, false, true));
        cue(player, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F);
        KokoReplay.recordStreamStart(player);
    }

    private static void tellNoMana(ServerPlayerEntity player) {
        player.sendMessage(Text.translatable("message.sparkwitch.koko.no_mana", KokoRules.STREAM_MANA)
                .formatted(Formatting.RED), true);
    }

    /**
     * Swallowed by a Taotie, stunned by a Control Expert, or role-skill-blocked by SparkTraits (Last Escape, a silenced
     * effective killer). The other locks are payload or interaction guards that run before this code.
     * 被饕餮吞下、被控场专家眩晕，或被 SparkTraits 禁用职业技能（脱险、被沉默的有效杀手）。其余锁定是先于本代码运行的数据包
     * 或交互拦截。
     */
    private static boolean isControlled(PlayerEntity player) {
        return NoellesTaotieSeekerBridge.isSwallowed(player)
                || ControlExpertStun.isStunned(player)
                || SparkTraitsKillerBridge.isRoleSkillBlocked(player);
    }

    /**
     * Both sides. Client: a living Koko's main-hand click on a body answers SUCCESS, so Fabric sends the interaction
     * and vanilla does not go on to use the held item (a lightstick would otherwise fire from the same click); it
     * predicts nothing else. Server: {@link #streamCorpse}.
     * 两端都会执行。客户端：存活的可可用主手点击尸体时回答 SUCCESS，于是 Fabric 发送该交互，而原版不会继续使用手中物品
     * （否则同一次点击还会用掉应援棒）；除此之外不做任何预测。服务端：见 {@link #streamCorpse}。
     */
    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity) {
        if (hand != Hand.MAIN_HAND || !(entity instanceof PlayerBodyEntity body) || player.isSpectator()) {
            return ActionResult.PASS;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!KokoRules.isKoko(game.getRole(player))) {
            return ActionResult.PASS;
        }
        if (world.isClient()) {
            return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                    && GameFunctions.isPlayerPlayingAndAlive(player) ? ActionResult.SUCCESS : ActionResult.PASS;
        }
        return player instanceof ServerPlayerEntity serverPlayer
                ? streamCorpse(serverPlayer, body, game) : ActionResult.PASS;
    }

    /**
     * Server thread. Vanilla has already checked the interaction range; the body must also be hittable (NoellesRoles
     * makes a Scavenger-hidden body unhittable, which is what keeps a crosshair off it; a forged packet is refused the
     * same way), targetable ({@code CanTargetBody}) and in her line of sight. A fake body (a feigned death) is
     * streamed like a real one, so the stream never tells her which bodies are fake; "once" is counted per dead
     * player ({@link #corpseKey}), so a player who keeps feigning death pays her only once.
     * 服务端线程。原版已检查交互距离；尸体还必须可被命中（NoellesRoles 让被清道夫藏起的尸体不可命中，准星因此对不上它；
     * 伪造的数据包同样被拒绝）、可被瞄准（{@code CanTargetBody}）且在她的视线内。假尸体（假死）与真尸体一样可以直播，
     * 因此直播不会告诉她哪些尸体是假的；“一次”按死者计（{@link #corpseKey}），反复假死的玩家只会让她得到一次流量。
     */
    private static ActionResult streamCorpse(ServerPlayerEntity player, PlayerBodyEntity body, GameWorldComponent game) {
        long now = player.getServerWorld().getTime();
        KokoComponent state = KokoComponent.KEY.get(player);
        KokoRules.CorpseVerdict verdict = KokoRules.corpseVerdict(
                true,
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player),
                isControlled(player),
                body.canHit() && CanTargetBody.EVENT.invoker().canTarget(player, body) && player.canSee(body),
                GrandWitchFearService.isPlayerFeared(player),
                state.isLive(),
                state.cooldownRemaining(now) <= 0,
                state.hasStreamed(corpseKey(body)));
        switch (verdict) {
            case PASS -> {
                return ActionResult.PASS;
            }
            case FEARED -> GrandWitchFearService.sendSkillBlocked(player);
            case LIVE -> tell(player, Text.translatable("message.sparkwitch.koko.corpse.live"));
            case COOLING -> tell(player, Text.translatable("message.sparkwitch.koko.corpse.cooldown",
                    TickTime.ceilSeconds(state.cooldownRemaining(now))));
            case ALREADY_STREAMED -> tell(player, Text.translatable("message.sparkwitch.koko.corpse.already"));
            case USE -> {
                // Everyone sees the body through walls for 50 s: vanilla Glowing, as the NoellesRoles Noisemaker does.
                // 所有人隔墙看到这具尸体 50 秒：原版发光，与 NoellesRoles 大嗓门的做法相同。
                body.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, KokoRules.CORPSE_GLOW_TICKS, 0,
                        false, false));
                state.recordCorpseStream(corpseKey(body), now);
                cue(player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 0.8F);
                player.sendMessage(Text.translatable("message.sparkwitch.koko.corpse.streamed",
                        KokoRules.CORPSE_TRAFFIC), true);
                KokoReplay.recordCorpseStream(player, body.getPlayerUuid());
                return ActionResult.SUCCESS;
            }
        }
        // A refusal still consumes the click, as her client predicted. / 被拒绝的点击同样被吞掉，与她客户端的预测一致。
        return ActionResult.FAIL;
    }

    /**
     * What "each corpse only once" counts: the dead player the body belongs to, or the body entity itself when it has
     * no owner. A second body of the same player (a repeated feigned death, a fake body and then the real one) is the
     * same corpse to her.
     * “每具尸体只能一次”的计数对象：尸体所属的死者；尸体没有主人时退回到尸体实体本身。同一名玩家的第二具尸体（反复假死，或先
     * 假尸体后真尸体）对她来说是同一具。
     */
    static UUID corpseKey(PlayerBodyEntity body) {
        UUID owner = body.getPlayerUuid();
        return owner != null ? owner : body.getUuid();
    }

    private static void tell(ServerPlayerEntity player, Text message) {
        player.sendMessage(message.copy().formatted(Formatting.RED), true);
    }

    /** Every cue is heard by the players around her. / 每个提示音附近的玩家都能听到。 */
    static void cue(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS,
                volume, pitch);
    }

    /**
     * Every world tick; a player with no Koko state costs one component lookup. Game state is read only for players
     * that hold some.
     * 每个世界刻执行；没有可可状态的玩家只花一次组件查询。只有持有状态的玩家才会读取对局状态。
     */
    private static void tick(ServerWorld world) {
        long now = world.getTime();
        GameWorldComponent game = null;
        for (ServerPlayerEntity player : world.getPlayers()) {
            KokoComponent state = KokoComponent.KEY.get(player);
            if (state.idle()) {
                continue;
            }
            if (game == null) {
                game = GameWorldComponent.KEY.get(world);
            }
            boolean livingKoko = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                    && GameFunctions.isPlayerPlayingAndAlive(player)
                    && KokoRules.isKoko(game.getRole(player));
            tickStream(player, state, now, livingKoko);
            if (livingKoko && now % KokoRules.SPEED_REFRESH_TICKS == 0L && KokoRules.hasSpeed(state.traffic())) {
                // Renewed once per second and gone within 2.5 s of dropping under 1000 (a lightstick purchase), her
                // death or the round's end. Ambient, no particles, icon on.
                // 每秒续一次；流量跌破 1000（买应援棒）、死亡或对局结束后 2.5 秒内消失。环境效果、无粒子、显示图标。
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, KokoRules.SPEED_EFFECT_TICKS,
                        KokoRules.SPEED_AMPLIFIER, true, false, true));
            }
        }
    }

    private static void tickStream(ServerPlayerEntity player, KokoComponent state, long now, boolean livingKoko) {
        if (state.streamEndTick() <= 0L) {
            return;
        }
        if (!livingKoko) {
            // Death, a role change or the round's end: the stream stops and earns nothing more.
            // 死亡、换职业或对局结束：直播停止，不再获得流量。
            if (state.endStream(now) | state.expireStream(now)) {
                KokoReplay.recordStreamEnd(player);
            }
            return;
        }
        // A swallowed Koko or one inside a Rift Gate (an alive spectator) earns nothing; the stream clock keeps running.
        // 被吞下或身处裂隙门内（存活的旁观者）的可可不获得流量；直播计时照常进行。
        if (KokoRules.pulseDue(now, state.streamEndTick(), KokoRules.STREAM_TICKS)
                && !player.isSpectator() && !NoellesTaotieSeekerBridge.isSwallowed(player)) {
            state.addTraffic(KokoRules.trafficPerSecond(state.isCheering(now)));
        }
        if (state.expireStream(now)) {
            KokoReplay.recordStreamEnd(player);
        }
    }

    /**
     * Role assignment and Wathe's {@code ResetPlayer}: clears every timer and her traffic.
     * 身份分配与 Wathe 的 {@code ResetPlayer}：清空全部计时与流量。
     */
    static void clear(ServerPlayerEntity player) {
        KokoComponent.KEY.get(player).clear();
    }

    /** A living, non-spectator participant of an ACTIVE round; safe on both sides. / ACTIVE 回合中存活且非旁观的参与者；两端均可调用。 */
    static boolean inActiveRound(@Nullable PlayerEntity player) {
        return player != null
                && GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator();
    }
}
