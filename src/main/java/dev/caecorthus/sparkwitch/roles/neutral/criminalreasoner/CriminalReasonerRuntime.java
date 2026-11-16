package dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.OpenCriminalReasonerS2CPacket;
import dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanWinService;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendWinService;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderDoorRules;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.caecorthus.sparkwitch.roles.neutral.koko.KokoWinService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Criminal Reasoner runtime: round setup, death attribution, the reasoning menu, invisibility, the success notice
 * and the neutral win.
 * <p>Ported from NoellesRoles' Parox build. Death attribution follows its rule: a recorded killer wins, and a death
 * with no killer counts as self-inflicted only for the reasons that build treats that way.
 * <p>Every timer here (menu session, reasoning cooldown, delayed notice) reads one clock, the server tick counter
 * ({@link MinecraftServer#getTicks()}); world time is a different, persisted clock and must never be mixed in.
 * 犯罪推理学家运行期：开局准备、死亡归因、推理菜单、隐身、成功通知与中立胜利。
 * <p>移植自 NoellesRoles 的 Parox 版本。死亡归因沿用其规则：有凶手就记凶手；
 * 无凶手时，只有原实现视为“自责”的死因才记成死者自己。
 * <p>这里的所有计时（菜单会话、推理冷却、延迟通知）只读同一个时钟：服务器刻计数（{@link MinecraftServer#getTicks()}）；
 * 世界时间是另一个会持久化的时钟，绝不能混用。
 */
public final class CriminalReasonerRuntime {

    /**
     * Stable contract: the win listener's phase on Wathe's first-non-null {@code CheckWinCondition}, after the Fiend
     * Moment, the Charon's Soul Moment and Koko, and before the default phase, so a default-phase {@code block()} (the
     * Murderous Witch, the witch faction) cannot hide the Reasoner's win.
     * 稳定契约：胜负监听器在 Wathe「首个非 null 生效」的 {@code CheckWinCondition} 上的阶段，排在魔人时刻、卡戎亡魂时刻与
     * 可可之后、默认阶段之前，因此默认阶段的 {@code block()}（杀意魔女、魔女阵营）无法盖住推理学家的胜利。
     */
    public static final Identifier WIN_PHASE = SparkWitch.id("criminal_reasoner_win");

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final Deque<PendingNotice> NOTICES = new ArrayDeque<>();

    /** Reasons a death with no killer is attributed to the victim. 无凶手时归到死者自己的死因。 */
    private static final Set<Identifier> SELF_ATTRIBUTED_DEATHS = Set.of(
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.SHOT_INNOCENT,
            GameConstants.DeathReasons.MENTAL_BREAKDOWN,
            GameConstants.DeathReasons.ESCAPED
    );

    private static boolean registered;

    private CriminalReasonerRuntime() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CriminalReasonerReplay.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer
                    && role != null && CriminalReasonerRules.ROLE_ID.equals(role.identifier())) {
                // Assign starts the ledger; the key and the win threshold wait for the final deal.
                // 分配时先清账；钥匙与胜利阈值等全部身份发完再处理。
                CriminalReasonerComponent component = CriminalReasonerComponent.KEY.get(serverPlayer);
                component.reset();
                // Neutral: may not pick up the revolver (source rule "不能捡左轮").
                // 中立：不允许捡起左轮手枪（原规则“不可捡枪”）。
                if (serverPlayer.getWorld() instanceof ServerWorld world) {
                    GameWorldComponent game = GameWorldComponent.KEY.get(world);
                    game.addToPreventGunPickup(serverPlayer);
                    // A Reasoner forced in mid-round gets his threshold now; a dealt one gets it at round start.
                    // 回合进行中被强制设为推理学家时立即获得阈值；正常发放的在开局时获得。
                    if (game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
                        component.setRequiredReasoningCount(
                                CriminalReasonerRules.requiredSolves(game.getAllPlayers().size()));
                    }
                }
            }
        });
        GameEvents.ON_FINISH_INITIALIZE.register(CriminalReasonerRuntime::onRoundStart);
        KillPlayer.AFTER.register(CriminalReasonerRuntime::afterKill);
        CheckWinCondition.EVENT.addPhaseOrdering(FiendWinService.PHASE, WIN_PHASE);
        CheckWinCondition.EVENT.addPhaseOrdering(FerrymanWinService.PHASE, WIN_PHASE);
        CheckWinCondition.EVENT.addPhaseOrdering(KokoWinService.PHASE, WIN_PHASE);
        CheckWinCondition.EVENT.addPhaseOrdering(WIN_PHASE, Event.DEFAULT_PHASE);
        CheckWinCondition.EVENT.register(WIN_PHASE, CriminalReasonerRuntime::checkWin);
        // Both logical sides, so the client predicts the same door answer and key cooldown.
        // 两个逻辑端都注册，客户端因此预测相同的开门结果与钥匙冷却。
        DoorInteraction.EVENT.register(CriminalReasonerRuntime::doorInteraction);
        ResetPlayer.EVENT.register(CriminalReasonerRuntime::forget);
        ServerTickEvents.END_SERVER_TICK.register(CriminalReasonerRuntime::tick);
    }

    /**
     * Round start on final roles: the win threshold, the 45 s opening cooldown and one neutral master key per living
     * Reasoner that does not hold one yet (Koko / Insider pattern; a full inventory drops it at his feet).
     * 按最终身份开局：胜利阈值、45 秒开局冷却，以及给每名尚未持有钥匙的存活推理学家一把中立万能钥匙（沿用可可 / 内应的做法；
     * 背包已满时掉在脚下）。
     */
    private static void onRoundStart(net.minecraft.world.World world, GameWorldComponent game) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        clearRoundState();
        MinecraftServer server = serverWorld.getServer();
        int required = CriminalReasonerRules.requiredSolves(game.getAllPlayers().size());
        for (UUID uuid : game.getAllWithRole(SparkWitchRoleRegistry.criminalReasoner())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            CriminalReasonerComponent component = CriminalReasonerComponent.KEY.get(player);
            component.setRequiredReasoningCount(required);
            COOLDOWN_UNTIL.put(uuid, (long) server.getTicks() + CriminalReasonerRules.INITIAL_COOLDOWN_TICKS);
            component.setCooldownSeconds(CriminalReasonerRules.INITIAL_COOLDOWN_TICKS / 20);
            // The game is still STARTING here, so liveness is read from Wathe's dead list.
            // 此时对局仍处于 STARTING，因此存活状态取自 Wathe 的死亡名单。
            PlayerInventory inventory = player.getInventory();
            if (!game.isPlayerDead(uuid)
                    && !inventory.contains(stack -> stack.isOf(ModItems.NEUTRAL_MASTER_KEY))) {
                inventory.offerOrDrop(new ItemStack(ModItems.NEUTRAL_MASTER_KEY));
                inventory.markDirty();
                player.currentScreenHandler.sendContentUpdates();
            }
        }
    }

    private static DoorInteraction.DoorInteractionResult doorInteraction(
            DoorInteraction.DoorInteractionContext context) {
        PlayerEntity player = context.getPlayer();
        DoorInteraction.DoorInteractionResult result = InsiderDoorRules.masterKeyDoorResult(
                // handItem is Wathe's copy of the main-hand stack. / handItem 是 Wathe 复制的主手物品栈。
                context.getHandItem().isOf(ModItems.NEUTRAL_MASTER_KEY),
                isReasoner(player),
                context.getDoorType(),
                context.isBlasted(),
                context.isJammed(),
                context.isOpen(),
                context.requiresKey(),
                player.getItemCooldownManager().isCoolingDown(ModItems.NEUTRAL_MASTER_KEY)
        );
        if (result == DoorInteraction.DoorInteractionResult.ALLOW) {
            player.getItemCooldownManager().set(ModItems.NEUTRAL_MASTER_KEY, InsiderRules.MASTER_KEY_COOLDOWN_TICKS);
        }
        return result;
    }

    /**
     * Records who killed whom for every Reasoner in the round.
     * <p>A killer always wins the attribution. Without one, only the source build's self-attributed reasons count as
     * the victim killing himself; every other killer-less death stays unrecorded and is never offered in the menu.
     * 为本局每位推理学家记录谁杀了谁。
     * <p>有凶手时一律记凶手；没有凶手时，只有原实现视为自责的死因才记成死者自己，
     * 其余无凶手的死亡不记录，也不会出现在菜单中。
     */
    private static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                  @Nullable Identifier deathReason) {
        // A SparkTraits Last Stand interception is not a death. / SparkTraits 背水一战的拦截不算死亡。
        if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
            return;
        }
        UUID recordedKiller = killer != null ? killer.getUuid() : null;
        if (recordedKiller == null && deathReason != null && SELF_ATTRIBUTED_DEATHS.contains(deathReason)) {
            recordedKiller = victim.getUuid();
        }
        if (recordedKiller == null || !(victim.getWorld() instanceof ServerWorld world)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (UUID uuid : game.getAllWithRole(SparkWitchRoleRegistry.criminalReasoner())) {
            ServerPlayerEntity reasoner = world.getServer().getPlayerManager().getPlayer(uuid);
            if (reasoner != null) {
                CriminalReasonerComponent.KEY.get(reasoner)
                        .recordReasoningTarget(victim.getUuid(), recordedKiller);
            }
        }
    }

    /**
     * Neutral win once the required number of victims has been solved; {@code null} otherwise, so he blocks nobody.
     * A swallowed Reasoner or one inside a Rift Gate (an alive spectator) cannot win from in there.
     * 解出达到阈值的死者后中立胜利；否则返回 {@code null}，不阻止任何一方。被吞下或身处裂隙门内（存活的旁观者）时不能获胜。
     */
    private static CheckWinCondition.@Nullable WinResult checkWin(ServerWorld world, GameWorldComponent game,
                                                                 GameFunctions.WinStatus status) {
        for (UUID uuid : game.getAllWithRole(SparkWitchRoleRegistry.criminalReasoner())) {
            ServerPlayerEntity reasoner = world.getServer().getPlayerManager().getPlayer(uuid);
            if (reasoner == null || !GameFunctions.isPlayerPlayingAndAlive(reasoner) || reasoner.isSpectator()
                    || NoellesTaotieSeekerBridge.isSwallowed(reasoner)) {
                continue;
            }
            CriminalReasonerComponent component = CriminalReasonerComponent.KEY.get(reasoner);
            if (CriminalReasonerRules.hasWon(component.getSuccessfulReasoningCount(),
                    component.getRequiredReasoningCount())) {
                return CheckWinCondition.WinResult.neutralWin(reasoner, List.of(reasoner));
            }
        }
        return null;
    }

    /** Builds the roster and opens the menu on the client. 生成名单并在客户端打开菜单。 */
    public static void openSelection(ServerPlayerEntity reasoner) {
        if (!mayAct(reasoner) || !(reasoner.getWorld() instanceof ServerWorld world)) {
            return;
        }
        // No menu while cooling down: a guess built now could only be refused. / 冷却中不开菜单：此时拼出的推理只会被拒绝。
        if (refuseIfCoolingDown(reasoner, world.getServer().getTicks())) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        CriminalReasonerComponent component = CriminalReasonerComponent.KEY.get(reasoner);

        // Only attributable, unsolved deaths: a death with no recorded killer can never be answered right, and his own
        // kills are not puzzles (he can never name himself).
        // 只列出可归因且未解出的死亡：没有记录凶手的死亡永远不可能答对；他自己造成的死亡不是谜题（他不能指认自己）。
        List<UUID> victimUuids = new ArrayList<>();
        List<String> victimNames = new ArrayList<>();
        for (UUID uuid : game.getAllPlayers()) {
            if (victimUuids.size() >= OpenCriminalReasonerS2CPacket.MAX_ENTRIES) {
                break;
            }
            if (uuid.equals(reasoner.getUuid()) || component.hasSolvedVictim(uuid) || !game.isPlayerDead(uuid)
                    || !component.hasRecordedKiller(uuid)
                    || component.isCorrectReasoning(uuid, reasoner.getUuid())) {
                continue;
            }
            victimUuids.add(uuid);
            victimNames.add(nameOf(world, uuid));
        }
        // Always answer, even with no victim yet: the client opens the menu and says so, instead of the press
        // looking like it did nothing. 即使还没有可推理的死者也照样回包：客户端会打开菜单并说明，
        // 而不是让这次按键看起来毫无反应。
        List<UUID> suspectUuids = new ArrayList<>();
        List<String> suspectNames = new ArrayList<>();
        // Suspects include the dead: the source menu lists an "alive" and a "dead" section.
        // 嫌疑人包含死者：原版菜单分「存活玩家」与「死亡玩家」两节。
        for (UUID uuid : game.getAllPlayers()) {
            if (suspectUuids.size() >= OpenCriminalReasonerS2CPacket.MAX_ENTRIES) {
                break;
            }
            if (uuid.equals(reasoner.getUuid())) {
                continue;
            }
            suspectUuids.add(uuid);
            suspectNames.add(nameOf(world, uuid));
        }

        if (!ServerPlayNetworking.canSend(reasoner, OpenCriminalReasonerS2CPacket.ID)) {
            return;
        }
        UUID sessionId = UUID.randomUUID();
        SESSIONS.put(reasoner.getUuid(), new Session(sessionId, world.getServer().getTicks()));
        ServerPlayNetworking.send(reasoner, new OpenCriminalReasonerS2CPacket(
                sessionId, victimUuids, victimNames, suspectUuids, suspectNames));
    }

    /** Validates and resolves one reasoning attempt from the menu. 校验并结算菜单提交的一次推理。 */
    public static void reason(ServerPlayerEntity reasoner, UUID sessionId, UUID victimId, UUID suspectId) {
        if (!mayAct(reasoner) || !(reasoner.getWorld() instanceof ServerWorld world)) {
            return;
        }
        long now = world.getServer().getTicks();
        Session session = SESSIONS.get(reasoner.getUuid());
        if (session == null || !session.id().equals(sessionId) || session.expired(now)) {
            SESSIONS.remove(reasoner.getUuid());
            return;
        }
        if (refuseIfCoolingDown(reasoner, now)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (victimId.equals(reasoner.getUuid()) || suspectId.equals(reasoner.getUuid())
                || !game.isPlayerDead(victimId) || !game.getAllPlayers().contains(suspectId)) {
            return;
        }
        SESSIONS.remove(reasoner.getUuid());

        CriminalReasonerComponent component = CriminalReasonerComponent.KEY.get(reasoner);
        boolean correct = component.isCorrectReasoning(victimId, suspectId);
        if (correct) {
            if (component.recordSuccessfulReasoning(victimId)) {
                NOTICES.add(new PendingNotice(reasoner.getUuid(),
                        now + CriminalReasonerRules.NOTICE_DELAY_TICKS));
            }
            reasoner.sendMessage(Text.translatable("tip.sparkwitch.criminal_reasoner.guess_success",
                    nameOf(world, victimId),
                    nameOf(world, suspectId)).formatted(Formatting.GREEN), true);
        } else {
            reasoner.sendMessage(Text.translatable("tip.sparkwitch.criminal_reasoner.guess_wrong")
                    .formatted(Formatting.RED), true);
        }
        int cooldownTicks = correct
                ? CriminalReasonerRules.SUCCESS_COOLDOWN_TICKS
                : CriminalReasonerRules.FAIL_COOLDOWN_TICKS;
        COOLDOWN_UNTIL.put(reasoner.getUuid(), now + cooldownTicks);
        component.setCooldownSeconds(cooldownTicks / 20);
        // Replay line: who reasoned which victim was killed by whom, and whether it held.
        // 回放记录：谁推测哪名死者被谁所杀，以及是否猜对。
        NbtCompound replay = new NbtCompound();
        replay.putUuid("victim", victimId);
        replay.putUuid("suspect", suspectId);
        replay.putBoolean("success", correct);
        GameRecordManager.recordSkillUse(reasoner, CriminalReasonerRules.ROLE_ID,
                world.getServer().getPlayerManager().getPlayer(suspectId), replay);
    }

    private static boolean refuseIfCoolingDown(ServerPlayerEntity reasoner, long now) {
        long cooldownUntil = COOLDOWN_UNTIL.getOrDefault(reasoner.getUuid(), 0L);
        if (now >= cooldownUntil) {
            return false;
        }
        reasoner.sendMessage(Text.translatable("tip.sparkwitch.criminal_reasoner.cooldown",
                (cooldownUntil - now + 19) / 20).formatted(Formatting.GRAY), true);
        return true;
    }

    /** Spends one charge for a 20 s invisibility; a second activation waits for it to end. 消耗充能隐身 20 秒。 */
    public static void activateInvisibility(ServerPlayerEntity reasoner) {
        if (!mayAct(reasoner)) {
            return;
        }
        if (reasoner.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            return;
        }
        CriminalReasonerComponent component = CriminalReasonerComponent.KEY.get(reasoner);
        if (!component.consumeInvisibilityCharge()) {
            reasoner.sendMessage(Text.translatable("tip.sparkwitch.criminal_reasoner.no_charge")
                    .formatted(Formatting.GRAY), true);
            return;
        }
        reasoner.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY,
                CriminalReasonerRules.INVISIBILITY_TICKS,
                0,
                true,
                false,
                true));
    }

    /**
     * Server gate shared by the three payloads: the exact role in an ACTIVE round, alive and not spectating, not
     * swallowed, stunned or role-skill-blocked by SparkTraits, and not under the Grand Witch's Fear (which answers
     * with its own action-bar line). The payload policy drops most of these earlier; this re-checks server state.
     * 三个数据包共用的服务端门槛：ACTIVE 回合中的精确职业、存活且非旁观、未被吞下、未被眩晕、未被 SparkTraits 禁用职业技能，
     * 且不在大魔女的恐惧中（恐惧会回复自己的动作栏提示）。数据包策略通常已更早丢弃这些请求；这里按服务端状态复查。
     */
    private static boolean mayAct(ServerPlayerEntity player) {
        if (!isReasoner(player)
                || GameWorldComponent.KEY.get(player.getWorld()).getGameStatus()
                        != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator()
                || NoellesTaotieSeekerBridge.isSwallowed(player)
                || ControlExpertStun.isStunned(player)
                || SparkTraitsKillerBridge.isRoleSkillBlocked(player)) {
            return false;
        }
        return !GrandWitchFearService.denyRoleSkillIfFeared(player);
    }

    public static void clearRoundState() {
        SESSIONS.clear();
        COOLDOWN_UNTIL.clear();
        NOTICES.clear();
    }

    /**
     * Wathe's {@code ResetPlayer}: drops this player's menu session, cooldown and ledger (the component only syncs when
     * it held something, since Wathe resets lobby players every second).
     * Wathe 的 {@code ResetPlayer}：丢弃该玩家的菜单会话、冷却与记录（组件只在确有内容时才同步，因为 Wathe 每秒都会重置大厅里的玩家）。
     */
    private static void forget(ServerPlayerEntity player) {
        SESSIONS.remove(player.getUuid());
        COOLDOWN_UNTIL.remove(player.getUuid());
        CriminalReasonerComponent.KEY.get(player).resetIfDirty();
    }

    /**
     * Forced-cooldown store (SparkWitchForcedCooldowns store 31): ticks left on the reasoning cooldown, 0 when ready.
     * 强制冷却存储（SparkWitchForcedCooldowns 第 31 个存储）：推理冷却剩余刻数，就绪时为 0。
     */
    public static int reasoningCooldownTicks(ServerPlayerEntity player) {
        long until = COOLDOWN_UNTIL.getOrDefault(player.getUuid(), 0L);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, until - player.getServer().getTicks()));
    }

    /**
     * Forced-cooldown store writer: sets the reasoning cooldown to exactly {@code ticks} from now (0 or less clears it)
     * and refreshes the owner's HUD second. SparkFactionAPI's raise/extend only ever pass a longer value.
     * 强制冷却存储的写入：把推理冷却精确设为从现在起 {@code ticks} 刻（0 或更小即清除），并刷新本人 HUD 的秒数。
     * SparkFactionAPI 的抬高 / 延长只会传入更长的值。
     */
    public static void setReasoningCooldownTicks(ServerPlayerEntity player, int ticks) {
        if (ticks <= 0) {
            COOLDOWN_UNTIL.remove(player.getUuid());
            CriminalReasonerComponent.KEY.get(player).setCooldownSeconds(0);
            return;
        }
        COOLDOWN_UNTIL.put(player.getUuid(), (long) player.getServer().getTicks() + ticks);
        CriminalReasonerComponent.KEY.get(player).setCooldownSeconds((ticks + 19) / 20);
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();
        // Mirror the remaining cooldown to the owner's HUD; the component syncs only when the second changes.
        // 把剩余冷却同步给本人 HUD；组件只在显示的秒数变化时同步。
        if (!COOLDOWN_UNTIL.isEmpty()) {
            COOLDOWN_UNTIL.entrySet().removeIf(entry -> {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.getKey());
                long remaining = entry.getValue() - now;
                if (owner != null) {
                    CriminalReasonerComponent.KEY.get(owner)
                            .setCooldownSeconds((int) Math.max(0, (remaining + 19) / 20));
                }
                return remaining <= 0;
            });
        }
        if (NOTICES.isEmpty()) {
            return;
        }
        List<PendingNotice> ready = new ArrayList<>();
        NOTICES.removeIf(notice -> {
            if (notice.dueTick() <= now) {
                ready.add(notice);
                return true;
            }
            return false;
        });
        for (PendingNotice notice : ready) {
            broadcastNotice(server, notice.reasonerId());
        }
    }

    /**
     * Announces that a murder was seen through.
     * <p>The source build spoke to the police, neutrals, killers and spectators only; with no cross-mod police
     * registry here, the notice goes to everyone but the reasoner — it names nobody and reveals nothing.
     * 公告“有人看穿了列车上的命案”。
     * <p>原实现只通知警类、中立、杀手与旁观者；这里没有跨模组的“警类”名单，因此除推理学家本人外全部通知——
     * 这条公告不点名、也不泄露信息。
     */
    private static void broadcastNotice(MinecraftServer server, UUID reasonerId) {
        Text notice = Text.translatable("tip.sparkwitch.criminal_reasoner.notice_broadcast");
        for (ServerWorld world : server.getWorlds()) {
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            if (!game.isRunning()) {
                continue;
            }
            for (ServerPlayerEntity target : world.getPlayers()) {
                if (!target.getUuid().equals(reasonerId)) {
                    target.sendMessage(notice, true);
                }
            }
        }
    }

    private static String nameOf(ServerWorld world, UUID uuid) {
        ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(uuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        var profile = GameWorldComponent.KEY.get(world).getGameProfiles().get(uuid);
        return profile != null
                ? profile.getName()
                : Text.translatable("screen.sparkwitch.criminal_reasoner.unknown_player").getString();
    }

    /** The exact assigned role from Wathe's synced component; safe on both sides. 读取 Wathe 同步的真实职业；两端均可调用。 */
    public static boolean isReasoner(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        return CriminalReasonerRules.isReasoner(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    private record Session(UUID id, long openedAtTick) {
        boolean expired(long now) {
            return now - openedAtTick > CriminalReasonerRules.MENU_SESSION_TICKS;
        }
    }

    private record PendingNotice(UUID reasonerId, long dueTick) {
    }
}
