package dev.caecorthus.sparkwitch.item.rope;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSeekerBridge;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroReturnService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.ShouldPiercePsychoArmour;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative Rope strangle (owner spec 2026-11-11; numbers and pure decisions in {@link RopeRules}).
 * <ul>
 *   <li><b>Start</b> ({@link #tryStart}, from {@code RopeItem.use}): the user gate, the Taser-style aimed target, the
 *       back check and SparkTraits' melee seams. A refusal costs nothing (private action-bar line, no cooldown).</li>
 *   <li><b>Hold</b>: both players are held for {@link RopeRules#CHANNEL_TICKS}. {@link RopeStrangleHold} plugs the
 *       session table into {@code ControlExpertStun.isStunned}, so every stun guard, payload deny-list and the client
 *       key lock apply to both; the owner-synced {@link RopeHoldComponent} drives the client. Each player is snapped to
 *       an anchor at the start and pulled back when drifting (rotation left relative, so cameras stay free).</li>
 *   <li><b>Hits</b>: at ticks 20, 40 and 60 the victim shows one vanilla hurt (the vanilla hurt packet with an
 *       attacker-less source) and a short choke is heard within {@link RopeRules#CHOKE_SOUND_RANGE} blocks. The server
 *       damage pipeline is deliberately not entered: {@code damage()} would end a NoellesRoles spirit projection, wake
 *       and move a sleeper, be swallowed by invulnerable states and leave real invulnerability frames.</li>
 *   <li><b>Kill</b>: at tick 60 a normal (non-forced) {@code killPlayer} with {@code sparkwitch:strangled}; shields
 *       step aside without being spent ({@link RopeShieldPolicy}), traits and role immunities still apply. The rope is
 *       consumed only when the kill succeeded ({@link RopeRules#outcome}); otherwise it is kept and cools down.</li>
 *   <li><b>Interrupt</b> ({@link RopeRules.Interrupt}): a kill attempt on the user by another player
 *       ({@code mixin/rope/GameFunctionsRopeStrangleInterruptMixin}, also when a shield blocks it), any control over
 *       the user, a push or teleport of either player off the anchor, the rope leaving the user's hands, either player
 *       dying or leaving, or {@link #interruptUser}. The victim is released unharmed, the rope is kept and cools down
 *       for {@link RopeRules#INTERRUPT_COOLDOWN_TICKS}.</li>
 *   <li><b>Lifecycle</b>: sessions live in memory only and always release (death, disconnect, player reset, round
 *       end, server stop). Every rope a player carries is deleted when that player dies (Ninja item precedent).</li>
 * </ul>
 * Server thread only, except the two session lookups behind {@link #isStrangling} / {@link #isStrangled}, which payload
 * guards may read from a network thread (hence the concurrent maps).
 * 服务端权威的绳索勒杀（所有者规格 2026-11-11；数值与纯判定见 {@link RopeRules}）。开始：使用者门槛、电击枪式的瞄准目标、背后判定与
 * SparkTraits 近战接缝；被拒绝没有任何代价（仅私有提示，无冷却）。定身：双方被定身 {@link RopeRules#CHANNEL_TICKS}；
 * {@link RopeStrangleHold} 把会话表接入 {@code ControlExpertStun.isStunned}，因此所有眩晕拦截、数据包拦截名单与客户端按键锁都对双方
 * 生效，仅同步给本人的 {@link RopeHoldComponent} 驱动客户端；开始时把双方对齐到锚点，漂移时拉回（旋转保持相对值，视角自由）。受击：
 * 第 20、40、60 刻目标各显示一次原版受击（无攻击者来源的原版受击数据包），{@link RopeRules#CHOKE_SOUND_RANGE} 格内可听到短促的窒息声；
 * 有意不进入服务端伤害流程：{@code damage()} 会终止 NoellesRoles 的灵魂出窍、唤醒并移动睡眠者、被无敌状态吞掉，并留下真实的无敌帧。
 * 击杀：第 60 刻以 {@code sparkwitch:strangled} 发起普通（非强制）{@code killPlayer}；护盾让开且不被消耗（{@link RopeShieldPolicy}），
 * 词条与职业免疫照常生效；只有击杀成功才消耗绳索（{@link RopeRules#outcome}），否则保留并进入冷却。打断
 * （{@link RopeRules.Interrupt}）：他人对使用者的击杀尝试（{@code mixin/rope/GameFunctionsRopeStrangleInterruptMixin}，被护盾挡下时
 * 同样触发）、使用者受到任何控制、任一方被推离或传送离开锚点、绳索离手、任一方死亡或离开，或调用 {@link #interruptUser}；目标毫发无伤地
 * 被放开，绳索保留并冷却 {@link RopeRules#INTERRUPT_COOLDOWN_TICKS}。生命周期：会话只存在于内存且必定释放（死亡、断线、玩家重置、
 * 回合结束、服务器停止）；玩家死亡时其携带的所有绳索都会被删除（忍者道具先例）。除 {@link #isStrangling} / {@link #isStrangled} 背后的
 * 两个会话查询可能被数据包拦截从网络线程读取（因此使用并发映射）外，仅在服务端线程运行。
 */
public final class RopeStrangleService {
    static final String BLOCKED_KEY = "message.sparkwitch.rope.blocked";
    static final String NO_TARGET_KEY = "message.sparkwitch.rope.no_target";
    static final String NOT_BEHIND_KEY = "message.sparkwitch.rope.not_behind";
    static final String STRANGLING_KEY = "message.sparkwitch.rope.strangling";
    static final String STRANGLED_KEY = "message.sparkwitch.rope.strangled";
    static final String INTERRUPTED_KEY = "message.sparkwitch.rope.interrupted";
    static final String RELEASED_KEY = "message.sparkwitch.rope.released";
    static final String PROTECTED_KEY = "message.sparkwitch.rope.protected";

    private static final float START_VOLUME = 0.8F;
    private static final float START_PITCH = 0.7F;
    private static final float CHOKE_VOLUME = 1.0F;
    private static final float CHOKE_PITCH = 0.75F;
    /**
     * Fixed-range copies of two vanilla sounds: the server only sends them within
     * {@link RopeRules#CHOKE_SOUND_RANGE} blocks and the client fades them out at that distance. No new asset or
     * registry entry. / 两个原版音效的固定距离副本：服务端只发送给 {@link RopeRules#CHOKE_SOUND_RANGE} 格内的玩家，客户端也在该距离
     * 衰减为零。不新增资源或注册项。
     */
    private static final RegistryEntry<SoundEvent> START_SOUND = RegistryEntry.of(
            SoundEvent.of(SoundEvents.ENTITY_LEASH_KNOT_PLACE.getId(), RopeRules.CHOKE_SOUND_RANGE));
    private static final RegistryEntry<SoundEvent> CHOKE_SOUND = RegistryEntry.of(
            SoundEvent.of(SoundEvents.ENTITY_PLAYER_HURT_DROWN.getId(), RopeRules.CHOKE_SOUND_RANGE));

    /** Strangler uuid -> session and strangled uuid -> the same session. / 勒杀者 uuid -> 会话；被勒者 uuid -> 同一会话。 */
    private static final Map<UUID, Session> BY_USER = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> BY_VICTIM = new ConcurrentHashMap<>();

    private static boolean registered;

    private RopeStrangleService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Shield pass, psycho armour: Wathe's own event (Savior precedent). The Specter's Otherworld shield sits behind
        // the same answer, so it steps aside too; neither spends a layer.
        // 穿盾之疯魔护甲：使用 Wathe 自带事件（救世主先例）。幽魂里世界护盾依赖同一答案，因此同样让开；二者都不消耗层数。
        ShouldPiercePsychoArmour.EVENT.register((victim, reason) -> RopeShieldPolicy.passesShields(reason));
        ServerTickEvents.END_SERVER_TICK.register(RopeStrangleService::tick);
        KillPlayer.AFTER.register((victim, killer, reason) -> onDeath(victim));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onGone(handler.player));
        ResetPlayer.EVENT.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                releaseSilently(serverPlayer);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    releaseSilently(player);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BY_USER.clear();
            BY_VICTIM.clear();
        });
    }

    /**
     * Cross-feature seam (stable signature): aborts the strangle {@code user} is performing, exactly like an attack on
     * the user would. Returns whether one was running. 死亡回归 calls this when Hiro returns to her record point.
     * 跨功能接缝（签名稳定）：中止 {@code user} 正在进行的勒杀，效果等同于使用者受到攻击；返回是否确有勒杀在进行。
     * 希罗触发死亡回归、传送回记录点时调用。
     */
    public static boolean interruptUser(ServerPlayerEntity user) {
        Session session = user == null ? null : BY_USER.get(user.getUuid());
        if (session == null) {
            return false;
        }
        end(user.getServer(), session, RopeRules.Interrupt.USER_ATTACKED);
        return true;
    }

    /**
     * Whether {@code player} is the one strangling right now. Both sides; a client only knows its own player.
     * {@code player} 此刻是否正在勒杀他人。两端可用；客户端只知道本地玩家自己的状态。
     */
    public static boolean isStrangling(PlayerEntity player) {
        return RopeStrangleHold.side(player) == RopeHoldComponent.Side.STRANGLER;
    }

    /**
     * Whether {@code player} is being strangled right now. Both sides; a client only knows its own player.
     * {@code player} 此刻是否正被勒住。两端可用；客户端只知道本地玩家自己的状态。
     */
    public static boolean isStrangled(PlayerEntity player) {
        return RopeStrangleHold.side(player) == RopeHoldComponent.Side.STRANGLED;
    }

    /** Server truth for {@link RopeStrangleHold}. / {@link RopeStrangleHold} 的服务端权威读取。 */
    static RopeHoldComponent.Side serverSide(PlayerEntity player) {
        UUID id = player.getUuid();
        if (BY_USER.containsKey(id)) {
            return RopeHoldComponent.Side.STRANGLER;
        }
        return BY_VICTIM.containsKey(id) ? RopeHoldComponent.Side.STRANGLED : RopeHoldComponent.Side.NONE;
    }

    /**
     * Server entry of {@code RopeItem.use}. Returns whether a strangle started; every refusal leaves the rope, the
     * cooldown and both players untouched.
     * {@code RopeItem.use} 的服务端入口。返回是否开始了勒杀；任何拒绝都不改动绳索、冷却与双方玩家。
     */
    static boolean tryStart(ServerPlayerEntity user, Hand hand) {
        ItemStack rope = user.getStackInHand(hand);
        // SparkTraits' new-action gate (Last Escape, a Close Quarters lock on this item): silent, like every weapon.
        // SparkTraits 的新动作门槛（脱险、近身格挡对该物品的锁定）：与其他武器一样静默拒绝。
        if (!rope.isOf(SparkWitchItems.rope()) || SparkTraitsKillerBridge.blocksWeaponAction(user, rope)) {
            return false;
        }
        boolean mayAct = RopeTargeting.mayAct(user);
        ServerPlayerEntity victim = mayAct ? RopeTargeting.findTarget(user) : null;
        RopeRules.Refusal refusal = RopeRules.refusal(mayAct, victim != null,
                victim != null && RopeTargeting.isBehind(user, victim));
        if (refusal != RopeRules.Refusal.NONE) {
            user.sendMessage(Text.translatable(switch (refusal) {
                case USER_BLOCKED -> BLOCKED_KEY;
                case NOT_BEHIND -> NOT_BEHIND_KEY;
                default -> NO_TARGET_KEY;
            }), true);
            return false;
        }
        // Trait protection, not a shield: a Close Quarters parry stops the grab and applies its own counter.
        // 词条保护而非护盾：近身格挡会挡下这次出手并施加自己的反制。
        if (SparkTraitsKillerBridge.shouldCancelMeleeAttack(user, victim, rope)) {
            return false;
        }
        start(user, victim);
        return true;
    }

    private static void start(ServerPlayerEntity user, ServerPlayerEntity victim) {
        ServerWorld world = user.getServerWorld();
        Session session = new Session(user.getUuid(), victim.getUuid(), world, user.getServer().getTicks(),
                new Anchor(user), new Anchor(victim));
        BY_USER.put(session.user, session);
        BY_VICTIM.put(session.victim, session);
        hold(user, RopeHoldComponent.Side.STRANGLER);
        hold(victim, RopeHoldComponent.Side.STRANGLED);
        GameRecordManager.recordItemUse(user, RopeRules.ITEM_ID, victim, null);
        world.playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), START_SOUND, SoundCategory.PLAYERS,
                START_VOLUME, START_PITCH, world.getRandom().nextLong());
        sendProgress(user, victim, 0);
    }

    private static void hold(ServerPlayerEntity player, RopeHoldComponent.Side side) {
        // Clear, never release: releasing a raised Wathe knife would stab. / 只清除、绝不释放：释放举起的 Wathe 小刀会触发刺击。
        player.clearActiveItem();
        // The lock is synced before the snap, so the client is already locked when it confirms the teleport.
        // 先同步锁再对齐位置，客户端确认传送时已处于锁定状态。
        RopeHoldComponent.KEY.get(player).set(side);
        if (isFreeStanding(player)) {
            // Snap to the server position: vanilla ignores movement packets until the client confirms, so latency never
            // reads as drift or displacement. Relative zero rotation keeps the camera free.
            // 对齐到服务端位置：原版在客户端确认前忽略移动数据包，因此延迟不会被误判为漂移或移位。相对旋转为零，视角保持自由。
            player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F,
                    PositionFlag.ROT);
        }
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
    }

    /** END_SERVER_TICK: runs every strangle. / 服务端刻末：处理所有勒杀。 */
    private static void tick(MinecraftServer server) {
        if (BY_USER.isEmpty()) {
            return;
        }
        for (Session session : List.copyOf(BY_USER.values())) {
            if (BY_USER.get(session.user) == session) {
                step(server, session);
            }
        }
    }

    private static void step(MinecraftServer server, Session session) {
        ServerPlayerEntity user = server.getPlayerManager().getPlayer(session.user);
        ServerPlayerEntity victim = server.getPlayerManager().getPlayer(session.victim);
        boolean userPresent = user != null && user.getWorld() == session.world && RopeTargeting.isParticipant(user);
        boolean victimPresent = victim != null && victim.getWorld() == session.world
                && RopeTargeting.staysVictim(victim);
        RopeRules.HoldStep userStep = userPresent ? session.userAnchor.step(user) : RopeRules.HoldStep.KEEP;
        RopeRules.HoldStep victimStep = victimPresent ? session.victimAnchor.step(victim) : RopeRules.HoldStep.KEEP;
        RopeRules.Interrupt interrupt = RopeRules.interrupt(userPresent, victimPresent,
                userPresent && RopeTargeting.isControlled(user),
                userPresent && RopeTargeting.holdsRope(user),
                userStep == RopeRules.HoldStep.DISPLACED,
                victimStep == RopeRules.HoldStep.DISPLACED);
        if (interrupt != RopeRules.Interrupt.NONE) {
            end(server, session, interrupt);
            return;
        }
        session.userAnchor.settle(user, userStep);
        session.victimAnchor.settle(victim, victimStep);
        int elapsed = server.getTicks() - session.startTick;
        if (RopeRules.isHitTick(elapsed)) {
            cosmeticHit(victim);
        }
        if (RopeRules.isComplete(elapsed)) {
            finish(session, user, victim);
        } else if (elapsed > 0 && elapsed % RopeRules.HIT_INTERVAL_TICKS == 0) {
            sendProgress(user, victim, elapsed);
        }
    }

    // A seated or sleeping player is positioned by the seat or bed; teleporting would eject them.
    // 坐着或睡着的玩家由座位或床决定位置；传送会把他们弹出。
    private static boolean isFreeStanding(ServerPlayerEntity player) {
        return !player.hasVehicle() && !player.isSleeping();
    }

    /**
     * One vanilla hurt (red flash, the victim's camera shake and own hurt sound) through the vanilla hurt packet with an
     * attacker-less, knockback-free source, plus the short public choke. Nothing on the server changes: no health, no
     * invulnerability frames, no attacker, no {@code damage()} hook of any mod.
     * 通过原版受击数据包（无攻击者、无击退的来源）表现一次原版受击（红闪、受害者的镜头晃动与自身受击音），外加公开的短促窒息声。服务端
     * 状态完全不变：不改生命值、不产生无敌帧、不记录攻击者，也不经过任何模组的 {@code damage()} 钩子。
     */
    private static void cosmeticHit(ServerPlayerEntity victim) {
        ServerWorld world = victim.getServerWorld();
        world.sendEntityDamage(victim, world.getDamageSources().generic());
        world.playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), CHOKE_SOUND, SoundCategory.PLAYERS,
                CHOKE_VOLUME, CHOKE_PITCH, world.getRandom().nextLong());
    }

    private static void sendProgress(ServerPlayerEntity user, ServerPlayerEntity victim, int elapsed) {
        int seconds = RopeRules.secondsLeft(elapsed);
        user.sendMessage(Text.translatable(STRANGLING_KEY, seconds), true);
        victim.sendMessage(Text.translatable(STRANGLED_KEY, seconds), true);
    }

    private static void finish(Session session, ServerPlayerEntity user, ServerPlayerEntity victim) {
        // Release first: the kill and everything it triggers (a feign, a revive) must see two free players, and a
        // nested kill attempt on the user finds no session to interrupt.
        // 先解除定身：击杀及其触发的一切（假死、复活）看到的必须是两名自由的玩家，嵌套的对使用者的击杀尝试也找不到可打断的会话。
        forget(session);
        release(user);
        release(victim);
        ItemStack rope = heldRope(user);
        // Normal, non-forced kill: BEFORE listeners, traits and role immunities all run. / 普通的非强制击杀：BEFORE 监听、词条与职业免疫全部照常执行。
        GameFunctions.killPlayer(victim, true, user, SparkWitchDeathReasons.STRANGLED);
        RopeRules.Outcome outcome = RopeRules.outcome(
                GameWorldComponent.KEY.get(session.world).isPlayerDead(session.victim),
                SparkTraitsSeekerBridge.isLastStandPending(victim),
                HiroReturnService.isFeignedVictim(victim));
        if (outcome == RopeRules.Outcome.CONSUMED) {
            // The shared EMPTY stack is never written. / 绝不写入共享的 EMPTY 物品堆。
            if (!rope.isEmpty()) {
                rope.decrement(1);
            }
            return;
        }
        coolDown(user);
        user.sendMessage(Text.translatable(PROTECTED_KEY), true);
    }

    /** Ends a running strangle before its kill. / 在击杀前结束进行中的勒杀。 */
    private static void end(@Nullable MinecraftServer server, Session session, RopeRules.Interrupt interrupt) {
        if (!forget(session) || server == null) {
            return;
        }
        ServerPlayerEntity user = server.getPlayerManager().getPlayer(session.user);
        ServerPlayerEntity victim = server.getPlayerManager().getPlayer(session.victim);
        release(user);
        release(victim);
        if (user != null && RopeRules.interruptCoolsDown(interrupt)) {
            coolDown(user);
            user.sendMessage(Text.translatable(INTERRUPTED_KEY), true);
        }
        if (victim != null && interrupt != RopeRules.Interrupt.VICTIM_GONE) {
            victim.sendMessage(Text.translatable(RELEASED_KEY), true);
        }
    }

    /**
     * Called at HEAD of every Wathe {@code killPlayer}, before any guard or shield can cancel it: a kill attempt on a
     * strangling player by another player breaks the strangle. Never cancels anything.
     * 在每次 Wathe {@code killPlayer} 的 HEAD 调用，早于任何可能取消它的守卫或护盾：其他玩家对勒杀者的击杀尝试会打断勒杀。绝不取消
     * 任何东西。
     */
    public static void onKillAttempt(@Nullable ServerPlayerEntity target, @Nullable ServerPlayerEntity killer) {
        if (target == null || killer == null || BY_USER.isEmpty() || killer.getUuid().equals(target.getUuid())) {
            return;
        }
        Session session = BY_USER.get(target.getUuid());
        if (session != null) {
            end(target.getServer(), session, RopeRules.Interrupt.USER_ATTACKED);
        }
    }

    // A real death: end the dead player's strangle at once and delete every rope they carry.
    // 真正的死亡：立即结束该玩家所在的勒杀，并删除其携带的所有绳索。
    private static void onDeath(ServerPlayerEntity dead) {
        onGone(dead);
        removeRopes(dead);
    }

    private static void onGone(ServerPlayerEntity player) {
        Session asUser = BY_USER.get(player.getUuid());
        if (asUser != null) {
            end(player.getServer(), asUser, RopeRules.Interrupt.USER_GONE);
        }
        Session asVictim = BY_VICTIM.get(player.getUuid());
        if (asVictim != null) {
            end(player.getServer(), asVictim, RopeRules.Interrupt.VICTIM_GONE);
        }
    }

    // Player reset and round end: no cooldown, no message. / 玩家重置与回合结束：无冷却、无提示。
    private static void releaseSilently(ServerPlayerEntity player) {
        Session asUser = BY_USER.get(player.getUuid());
        Session asVictim = BY_VICTIM.get(player.getUuid());
        for (Session session : new Session[]{asUser, asVictim}) {
            if (session != null && forget(session)) {
                MinecraftServer server = player.getServer();
                release(server.getPlayerManager().getPlayer(session.user));
                release(server.getPlayerManager().getPlayer(session.victim));
            }
        }
        // Also clears a stale synced flag (a session lost without its end). / 同时清除残留的同步标记（会话在未结束的情况下丢失）。
        release(player);
    }

    private static boolean forget(Session session) {
        boolean removed = BY_USER.remove(session.user, session);
        BY_VICTIM.remove(session.victim, session);
        return removed;
    }

    // The zero sync is what unlocks the owner's client. / 零值同步才会解除本人客户端的锁定。
    private static void release(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        RopeHoldComponent component = RopeHoldComponent.KEY.getNullable(player);
        if (component != null) {
            component.set(RopeHoldComponent.Side.NONE);
        }
    }

    // Plain vanilla cooldown so SparkTraits Fast Hands may shorten it (Taser precedent). / 使用普通原版冷却，SparkTraits 快手可缩短（电击枪先例）。
    private static void coolDown(ServerPlayerEntity user) {
        user.getItemCooldownManager().set(SparkWitchItems.rope(), RopeRules.INTERRUPT_COOLDOWN_TICKS);
    }

    private static ItemStack heldRope(ServerPlayerEntity user) {
        if (user.getMainHandStack().isOf(SparkWitchItems.rope())) {
            return user.getMainHandStack();
        }
        return user.getOffHandStack().isOf(SparkWitchItems.rope()) ? user.getOffHandStack() : ItemStack.EMPTY;
    }

    /**
     * Role-agnostic, like the Ninja's weapons: inventory, the personal 2x2 crafting grid and the cursor; external
     * container slots stay untouched. / 与忍者武器一样不区分职业：背包、个人 2x2 合成格与光标；外部容器槽位保持不变。
     */
    private static void removeRopes(ServerPlayerEntity player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(SparkWitchItems.rope())) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        var personalCraftingInput = player.playerScreenHandler.getCraftingInput();
        for (int slot = 0; slot < personalCraftingInput.size(); slot++) {
            if (personalCraftingInput.getStack(slot).isOf(SparkWitchItems.rope())) {
                personalCraftingInput.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (player.currentScreenHandler.getCursorStack().isOf(SparkWitchItems.rope())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            player.getInventory().markDirty();
        }
    }

    /** One running strangle. / 一次进行中的勒杀。 */
    private static final class Session {
        final UUID user;
        final UUID victim;
        final ServerWorld world;
        final int startTick;
        final Anchor userAnchor;
        final Anchor victimAnchor;

        Session(UUID user, UUID victim, ServerWorld world, int startTick, Anchor userAnchor, Anchor victimAnchor) {
            this.user = user;
            this.victim = victim;
            this.world = world;
            this.startTick = startTick;
            this.userAnchor = userAnchor;
            this.victimAnchor = victimAnchor;
        }
    }

    /**
     * Where one held player must stay. The spot moves only with a fall, with our own pull-back, or when the player
     * leaves or takes a seat or bed: getting up moves a player by about a block on its own, which must neither free a
     * strangled player nor read as an outside push, so the anchor is simply taken again at the new spot.
     * 一名被定身者必须停留的位置。该位置只随下落、我们自己的拉回，或玩家离开/坐上座位与床而改变：起身本身就会让玩家移动约一格，这既不能
     * 让被勒者脱身，也不能被读作外来推搡，因此直接在新位置重新取锚点。
     */
    private static final class Anchor {
        private Vec3d spot;
        private boolean freeStanding;

        Anchor(ServerPlayerEntity player) {
            this.spot = player.getPos();
            this.freeStanding = isFreeStanding(player);
        }

        RopeRules.HoldStep step(ServerPlayerEntity player) {
            if (isFreeStanding(player) != freeStanding) {
                freeStanding = !freeStanding;
                spot = player.getPos();
            }
            return RopeRules.holdStep(player.getX() - spot.x, player.getY() - spot.y, player.getZ() - spot.z);
        }

        void settle(ServerPlayerEntity player, RopeRules.HoldStep step) {
            if (step == RopeRules.HoldStep.FOLLOW_FALL) {
                spot = new Vec3d(spot.x, player.getY(), spot.z);
                return;
            }
            if (step != RopeRules.HoldStep.PULL_BACK || !freeStanding) {
                return;
            }
            // Never lift a falling player back up. / 绝不把下落中的玩家重新抬起。
            spot = new Vec3d(spot.x, Math.min(spot.y, player.getY()), spot.z);
            // Our own correction lands exactly on the anchor, so it can never read as a displacement next tick.
            // 我们自己的纠正恰好落在锚点上，下一刻绝不会被读作移位。
            player.networkHandler.requestTeleport(spot.x, spot.y, spot.z, 0.0F, 0.0F, PositionFlag.ROT);
            player.setVelocity(Vec3d.ZERO);
        }
    }
}
