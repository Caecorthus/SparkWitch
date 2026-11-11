package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of Hanna's float ({@code sparkwitch:use_hanna_float}) and of every timer in {@link HannaComponent}:
 * <ul>
 *   <li>{@link #tryUse}: gates the request ({@link HannaRules#floatVerdict}), spends the mana, starts the float and
 *   puts on the transient Speed II modifier. A refused request changes nothing and costs nothing.</li>
 *   <li>{@link #touch}: the single interruption entry. Wathe's {@code KillPlayer.BEFORE} (observed only, never
 *   answered), Fabric's attack and use-entity callbacks (always PASS) and the projectile mixin all end here.</li>
 *   <li>The world tick: reconciles the speed modifier with the running float (a relog loses the transient modifier
 *   while the saved float still runs), forgets a float that ran out, and pulses the interruption penalty and the
 *   handkerchief's regeneration once per second. A float or a penalty never outlives her death, role or round.</li>
 * </ul>
 * The speed is an attribute modifier, not a status effect, so ending the float never strips a Speed effect that came
 * from somewhere else. Silence and the instinct veil are presentation: they read {@link HannaComponent#isFloating()}.
 * 汉娜浮游（{@code sparkwitch:use_hanna_float}）与 {@link HannaComponent} 中全部计时的服务端部分：
 * {@link #tryUse} 判定请求（{@link HannaRules#floatVerdict}）、扣除魔力、开始浮游并挂上临时的速度 II 修饰符；被拒绝的请求不改变
 * 任何东西，也不消耗任何东西。{@link #touch} 是唯一的打断入口：Wathe 的 {@code KillPlayer.BEFORE}（只观察、从不作答）、Fabric
 * 的攻击与实体交互回调（始终 PASS）以及投掷物 mixin 都汇入这里。世界刻：让速度修饰符与进行中的浮游保持一致（重新登录会丢失
 * 临时修饰符，而已保存的浮游仍在进行），清掉已自然结束的浮游，并每秒结算一次被打断惩罚与手帕回理智。浮游与惩罚都不会在她死亡、
 * 换职业或换局后继续。
 * 速度用属性修饰符而非状态效果实现，因此结束浮游不会移除来自别处的速度效果。静音与本能遮蔽属于表现层：它们读取
 * {@link HannaComponent#isFloating()}。
 */
public final class HannaFloatService {
    /**
     * Runs before the default phase of {@code KillPlayer.BEFORE}: Wathe's invoker stops at the first non-null answer,
     * so a default-phase observer would miss every attempt that an earlier listener cancels (a shield, a parry). This
     * listener always answers null, so nothing after it changes.
     * 排在 {@code KillPlayer.BEFORE} 默认阶段之前：Wathe 的调用器在第一个非 null 结果处停止，默认阶段的观察者会漏掉被更早的
     * 监听器取消的击杀尝试（护盾、格挡）。本监听器始终返回 null，因此其后的一切都不变。
     */
    static final Identifier OBSERVE_PHASE = SparkWitch.id("hanna_float_observe");

    private HannaFloatService() {
    }

    static void register() {
        KillPlayer.BEFORE.addPhaseOrdering(OBSERVE_PHASE, Event.DEFAULT_PHASE);
        KillPlayer.BEFORE.register(OBSERVE_PHASE, (victim, killer, deathReason) -> {
            touch(victim, killer);
            return null;
        });
        // Both callbacks also fire on the client and for spectators; only a server-side living participant counts,
        // and neither ever consumes the click. / 两个回调在客户端与旁观者身上同样会触发；只认服务端的存活参与者，且从不吞掉点击。
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient() && entity instanceof ServerPlayerEntity target) {
                touch(target, player);
            }
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient() && entity instanceof ServerPlayerEntity target) {
                touch(target, player);
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_WORLD_TICK.register(HannaFloatService::tick);
    }

    /** True while {@code player} floats; safe on both sides. / {@code player} 是否正在浮游；两端均可调用。 */
    public static boolean isFloating(@Nullable PlayerEntity player) {
        return player != null && HannaComponent.KEY.get(player).isFloating();
    }

    /**
     * Called on the server thread for every float request. The stun, Seeker-session, Riftwalker-session, Knight duel
     * and Fear payload guards already drop most refused requests; this re-checks every gate against server state.
     * 每个浮游请求都在服务端线程调用。眩晕、搜寻者会话、隙行者会话、骑士决斗与恐惧的数据包拦截已丢弃大多数被拒请求；此处按
     * 服务端状态重新检查每个门槛。
     */
    public static void tryUse(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        HannaComponent state = HannaComponent.KEY.get(player);
        WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(player);
        HannaRules.FloatVerdict verdict = HannaRules.floatVerdict(
                HannaRules.isHanna(game.getRole(player)),
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator(),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                ControlExpertStun.isStunned(player),
                SparkTraitsKillerBridge.isRoleSkillBlocked(player),
                state.isFloating(),
                state.floatCooldownRemaining(now) <= 0,
                GrandWitchFearService.isPlayerFeared(player),
                mana.hasManaSystem() && mana.getMana() >= HannaRules.FLOAT_MANA);
        switch (verdict) {
            case USE -> {
                // spendMana re-checks the balance; a false answer is the same refusal as NO_MANA.
                // spendMana 会再次核对余额；返回 false 与 NO_MANA 是同一种拒绝。
                if (mana.spendMana(HannaRules.FLOAT_MANA)) {
                    start(player, state, now);
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

    private static void start(ServerPlayerEntity player, HannaComponent state, long now) {
        state.startFloat(now);
        applySpeed(player, state);
        // Private cue: the float is silent for everyone else. / 私有提示音：浮游对其他人是无声的。
        player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6F, 1.6F);
        HannaReplay.recordFloat(player);
    }

    private static void tellNoMana(ServerPlayerEntity player) {
        player.sendMessage(Text.translatable("message.sparkwitch.hanna.no_mana", HannaRules.FLOAT_MANA)
                .formatted(Formatting.RED), true);
    }

    /**
     * The single interruption entry (server thread): {@code toucher} attacked, right-clicked, shot or tried to kill
     * {@code target}. A running float ends at once and the penalty starts when {@link HannaRules#interrupts} agrees:
     * an unknown toucher ({@code null}: an ownerless projectile, a kill without a killer) interrupts, she never
     * interrupts herself, and anything that is not a living, non-spectator round participant (a dead spectator
     * clicking through her, a non-player entity) never does. Does nothing unless {@code target} is floating.
     * 唯一的打断入口（服务端线程）：{@code toucher} 攻击、右键、射中或试图击杀了 {@code target}。当 {@link HannaRules#interrupts}
     * 认可时，进行中的浮游立刻结束并开始惩罚：未知的接触者（{@code null}：无归属的投掷物、没有凶手的击杀）会打断，她自己不会
     * 打断自己，而不是局内存活且非旁观的参与者（点穿她的死亡旁观者、非玩家实体）则永远不会。{@code target} 未在浮游时什么都不做。
     */
    public static void touch(ServerPlayerEntity target, @Nullable Entity toucher) {
        if (target == null) {
            return;
        }
        HannaComponent state = HannaComponent.KEY.get(target);
        if (!state.isFloating()) {
            return;
        }
        boolean aliveParticipant = toucher instanceof PlayerEntity other
                && GameFunctions.isPlayerPlayingAndAlive(other) && !other.isSpectator();
        if (!HannaRules.interrupts(true, toucher != null, toucher == target, aliveParticipant)) {
            return;
        }
        long now = target.getServerWorld().getTime();
        if (!state.endFloat(now)) {
            return;
        }
        removeSpeed(target, state);
        state.startPenalty(now);
        target.sendMessage(Text.translatable("message.sparkwitch.hanna.interrupted")
                .formatted(Formatting.RED), true);
        HannaReplay.recordInterrupted(target, toucher instanceof ServerPlayerEntity interrupter ? interrupter : null);
    }

    /**
     * Every world tick; a player with nothing timed costs one component lookup. Game state is read only for the few
     * players that have a timer running.
     * 每个世界刻执行；没有任何计时的玩家只花一次组件查询。只有少数正在计时的玩家才会读取对局状态。
     */
    private static void tick(ServerWorld world) {
        long now = world.getTime();
        GameWorldComponent game = null;
        for (ServerPlayerEntity player : world.getPlayers()) {
            HannaComponent state = HannaComponent.KEY.get(player);
            if (state.idle()) {
                continue;
            }
            if (game == null) {
                game = GameWorldComponent.KEY.get(world);
            }
            boolean inRound = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                    && GameFunctions.isPlayerPlayingAndAlive(player);
            Role role = game.getRole(player);
            boolean livingHanna = inRound && HannaRules.isHanna(role);
            tickFloat(player, state, now, livingHanna);
            tickPenalty(player, state, now, livingHanna);
            tickSoothe(player, state, now, inRound && role != null && role.getMoodType() == Role.MoodType.REAL,
                    inRound);
        }
    }

    private static void tickFloat(ServerPlayerEntity player, HannaComponent state, long now, boolean livingHanna) {
        if (state.floatEndTick() > 0L) {
            if (livingHanna) {
                state.expireFloat(now);
            } else {
                // Death, a role change or the round's end: the float stops without a penalty.
                // 死亡、换职业或对局结束：浮游直接停止，没有惩罚。
                state.endFloat(now);
                state.expireFloat(now);
            }
        }
        if (state.isFloating()) {
            // Also the relog path: the saved float still runs but the transient modifier is gone.
            // 同时覆盖重新登录：已保存的浮游仍在进行，但临时修饰符已经丢失。
            applySpeed(player, state);
        } else if (state.speedApplied()) {
            removeSpeed(player, state);
        }
    }

    /**
     * One sanity loss per second, the last one on the tick the penalty ends; the action bar counts the seconds left.
     * Wathe's own breakdown check still applies to the lowered value on its next tick.
     * 每秒流失一次理智，最后一次落在惩罚结束的那一刻；动作栏显示剩余秒数。降低后的数值在下一刻照常接受 Wathe 自己的精神崩溃判定。
     */
    private static void tickPenalty(ServerPlayerEntity player, HannaComponent state, long now, boolean livingHanna) {
        long end = state.penaltyEndTick();
        if (end <= 0L) {
            return;
        }
        if (!livingHanna) {
            state.clearPenalty();
            return;
        }
        if (HannaRules.pulseDue(now, end, HannaRules.PENALTY_TICKS)) {
            PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
            mood.setMood(Math.max(-1F, mood.getMood() - HannaRules.PENALTY_SANITY_PER_SECOND));
            int secondsLeft = (int) ((end - now) / 20L);
            if (secondsLeft > 0) {
                player.sendMessage(Text.translatable("message.sparkwitch.hanna.penalty", secondsLeft)
                        .formatted(Formatting.RED), true);
            }
        }
        if (now >= end) {
            state.clearPenalty();
        }
    }

    /**
     * The handkerchief works for whoever used it: any living round participant, but only a role with real sanity
     * gains any. It stops when the holder dies or the round ends.
     * 手帕对使用它的任何人生效：任何局内存活的参与者，但只有拥有真实理智的职业才会回复。持有者死亡或对局结束时停止。
     */
    private static void tickSoothe(ServerPlayerEntity player, HannaComponent state, long now, boolean realMood,
                                   boolean inRound) {
        long end = state.sootheEndTick();
        if (end <= 0L) {
            return;
        }
        if (!inRound) {
            state.clearSoothe();
            return;
        }
        if (realMood && HannaRules.pulseDue(now, end, HannaRules.HANDKERCHIEF_TICKS)) {
            PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
            mood.setMood(Math.min(1F, mood.getMood() + HannaRules.HANDKERCHIEF_SANITY_PER_SECOND));
        }
        if (now >= end) {
            state.clearSoothe();
        }
    }

    /**
     * Speed II as a transient modifier (the Specter's second-skill precedent): never saved, so a crash or relog
     * cannot leave it behind, and removing it touches no status effect.
     * 以临时修饰符实现速度 II（沿用幽魂二技能的先例）：从不保存，因此崩溃或重新登录不会残留，移除它也不会碰任何状态效果。
     */
    private static void applySpeed(ServerPlayerEntity player, HannaComponent state) {
        var movement = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movement != null && !movement.hasModifier(HannaRules.FLOAT_SPEED_MODIFIER_ID)) {
            movement.addTemporaryModifier(new EntityAttributeModifier(
                    HannaRules.FLOAT_SPEED_MODIFIER_ID,
                    HannaRules.FLOAT_SPEED_BONUS,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
        state.setSpeedApplied(true);
    }

    private static void removeSpeed(ServerPlayerEntity player, HannaComponent state) {
        var movement = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movement != null && movement.hasModifier(HannaRules.FLOAT_SPEED_MODIFIER_ID)) {
            movement.removeModifier(HannaRules.FLOAT_SPEED_MODIFIER_ID);
        }
        state.setSpeedApplied(false);
    }

    /**
     * Role assignment and Wathe's {@code ResetPlayer}: drops the modifier together with the timers, without waiting
     * for the next world tick.
     * 身份分配与 Wathe 的 {@code ResetPlayer}：连同计时一起卸下修饰符，不等到下一个世界刻。
     */
    static void clear(ServerPlayerEntity player) {
        HannaComponent state = HannaComponent.KEY.get(player);
        state.clear();
        removeSpeed(player, state);
    }
}
