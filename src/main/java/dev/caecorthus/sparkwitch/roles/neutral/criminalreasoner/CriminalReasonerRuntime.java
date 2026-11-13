package dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner;

import dev.caecorthus.sparkwitch.net.OpenCriminalReasonerS2CPacket;
import dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

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
 * 犯罪推理学家运行期：开局准备、死亡归因、推理菜单、隐身、成功通知与中立胜利。
 * <p>移植自 NoellesRoles 的 Parox 版本。死亡归因沿用其规则：有凶手就记凶手；
 * 无凶手时，只有原实现视为“自责”的死因才记成死者自己。
 */
public final class CriminalReasonerRuntime {

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

    private static final Identifier MASTER_KEY_ID = Identifier.of("noellesroles", "neutral_master_key");

    private CriminalReasonerRuntime() {
    }

    public static void register() {
        CriminalReasonerReplay.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer
                    && role != null && CriminalReasonerRules.ROLE_ID.equals(role.identifier())) {
                // Assign starts the ledger; the win threshold is recomputed once the whole deal is complete.
                // 分配时先清账并给钥匙；阈值等全部身份发完再算。
                CriminalReasonerComponent component = CriminalReasonerComponent.KEY.get(serverPlayer);
                component.reset();
                giveMasterKey(serverPlayer);
                // Neutral: may not pick up the revolver (source rule "不能捡左轮").
                // 中立：不允许捡起左轮手枪（原规则“不可捡枪”）。
                if (serverPlayer.getWorld() instanceof ServerWorld world) {
                    GameWorldComponent.KEY.get(world).addToPreventGunPickup(serverPlayer);
                }
            }
        });
        GameEvents.ON_FINISH_INITIALIZE.register((world, gameComponent) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            int required = Math.max(1, gameComponent.getAllPlayers().size()
                    / CriminalReasonerRules.WIN_THRESHOLD_DIVISOR);
            for (UUID uuid : gameComponent.getAllWithRole(SparkWitchRoleRegistry.criminalReasoner())) {
                ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    CriminalReasonerComponent.KEY.get(player).setRequiredReasoningCount(required);
                    // Round-start cooldown: nobody reasons in the opening seconds. / 回合初始冷却：开局不能立刻推理。
                    COOLDOWN_UNTIL.put(uuid,
                            serverWorld.getTime() + CriminalReasonerRules.INITIAL_COOLDOWN_TICKS);
                    CriminalReasonerComponent.KEY.get(player).setCooldownSeconds(
                            CriminalReasonerRules.INITIAL_COOLDOWN_TICKS / 20);
                }
            }
        });
        KillPlayer.AFTER.register(CriminalReasonerRuntime::afterKill);
        CheckWinCondition.EVENT.register(CriminalReasonerRuntime::checkWin);
        DoorInteraction.EVENT.register(CriminalReasonerRuntime::doorInteraction);
        ServerTickEvents.END_SERVER_TICK.register(CriminalReasonerRuntime::tick);
        // Mirror the remaining cooldown to the owner's HUD once per changed second.
        // 冷却剩余每秒变化时推给本人 HUD。
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (COOLDOWN_UNTIL.isEmpty()) {
                return;
            }
            long nowTicks = server.getTicks();
            COOLDOWN_UNTIL.entrySet().removeIf(entry -> {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.getKey());
                long remaining = entry.getValue() - nowTicks;
                if (owner != null) {
                    CriminalReasonerComponent.KEY.get(owner)
                            .setCooldownSeconds((int) Math.max(0, Math.ceil(remaining / 20.0)));
                }
                return remaining <= 0;
            });
        });
    }

    /** The role carries a neutral master key and may open what it opens. 该身份携带中立万能钥匙，并可开它能开的门。 */
    private static void giveMasterKey(ServerPlayerEntity player) {
        var item = Registries.ITEM.get(MASTER_KEY_ID);
        if (item != Items.AIR) {
            player.giveItemStack(new ItemStack(item));
        }
    }

    private static DoorInteraction.DoorInteractionResult doorInteraction(
            DoorInteraction.DoorInteractionContext context) {
        if (context.isOpen() || context.isBlasted() || context.isJammed()) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        boolean trainDoor = context.getDoorType() == DoorInteraction.DoorType.TRAIN_DOOR;
        if (!trainDoor && !context.requiresKey()) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        PlayerEntity player = context.getPlayer();
        if (!isReasoner(player)) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        if (!context.getHandItem().isOf(Registries.ITEM.get(MASTER_KEY_ID))) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        return DoorInteraction.DoorInteractionResult.ALLOW;
    }

    /**
     * Records who killed whom for every live reasoner.
     * <p>A killer always wins the attribution. Without one, only the source build's self-attributed reasons count as
     * the victim killing himself; every other killer-less death stays unrecorded and cannot be reasoned about.
     * 为每位存活的推理学家记录谁杀了谁。
     * <p>有凶手时一律记凶手；没有凶手时，只有原实现视为自责的死因才记成死者自己，
     * 其余无凶手的死亡不记录，也就无法被推理。
     */
    private static void afterKill(ServerPlayerEntity victim, ServerPlayerEntity killer, Identifier deathReason) {
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

    /** Neutral win once the required number of victims has been solved. 解出达到阈值的死者后中立胜利。 */
    private static CheckWinCondition.WinResult checkWin(ServerWorld world, GameWorldComponent game,
                                                        GameFunctions.WinStatus status) {
        for (UUID uuid : game.getAllWithRole(SparkWitchRoleRegistry.criminalReasoner())) {
            ServerPlayerEntity reasoner = world.getServer().getPlayerManager().getPlayer(uuid);
            if (reasoner == null || !GameFunctions.isPlayerPlayingAndAlive(reasoner)) {
                continue;
            }
            CriminalReasonerComponent component = CriminalReasonerComponent.KEY.get(reasoner);
            int required = component.getRequiredReasoningCount();
            if (required > 0 && component.getSuccessfulReasoningCount() >= required) {
                return CheckWinCondition.WinResult.neutralWin(reasoner, List.of(reasoner));
            }
        }
        return null;
    }

    /** Builds the roster and opens the menu on the client. 生成名单并在客户端打开菜单。 */
    public static void openSelection(ServerPlayerEntity reasoner) {
        boolean reasonerRole = isReasoner(reasoner);
        boolean alive = GameFunctions.isPlayerPlayingAndAlive(reasoner);
        // Logged because "the key does nothing" is otherwise indistinguishable from a dropped packet.
        // 记日志：“按键没反应”否则无法区分是角色不对还是包丢了。
        dev.caecorthus.sparkwitch.SparkWitch.LOGGER.info(
                "[criminal_reasoner] open request from {} reasoner={} alive={}",
                reasoner.getGameProfile().getName(), reasonerRole, alive);
        if (!reasonerRole || !alive) {
            return;
        }
        if (!(reasoner.getWorld() instanceof ServerWorld world)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        CriminalReasonerComponent component = CriminalReasonerComponent.KEY.get(reasoner);

        List<UUID> victimUuids = new ArrayList<>();
        List<String> victimNames = new ArrayList<>();
        for (UUID uuid : game.getAllPlayers()) {
            if (uuid.equals(reasoner.getUuid()) || component.hasSolvedVictim(uuid) || !game.isPlayerDead(uuid)) {
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
            if (uuid.equals(reasoner.getUuid())) {
                continue;
            }
            suspectUuids.add(uuid);
            suspectNames.add(nameOf(world, uuid));
        }

        UUID sessionId = UUID.randomUUID();
        SESSIONS.put(reasoner.getUuid(), new Session(sessionId, world.getTime()));
        ServerPlayNetworking.send(reasoner, new OpenCriminalReasonerS2CPacket(
                sessionId, victimUuids, victimNames, suspectUuids, suspectNames));
    }

    /** Validates and resolves one reasoning attempt from the menu. 校验并结算菜单提交的一次推理。 */
    public static void reason(ServerPlayerEntity reasoner, UUID sessionId, UUID victimId, UUID suspectId) {
        if (!isReasoner(reasoner) || !GameFunctions.isPlayerPlayingAndAlive(reasoner)) {
            return;
        }
        long now = reasoner.getServer().getTicks();
        Session session = SESSIONS.get(reasoner.getUuid());
        if (session == null || !session.id().equals(sessionId) || session.expired(now)) {
            SESSIONS.remove(reasoner.getUuid());
            return;
        }
        if (!(reasoner.getWorld() instanceof ServerWorld world)) {
            return;
        }
        long cooldownUntil = COOLDOWN_UNTIL.getOrDefault(reasoner.getUuid(), 0L);
        if (now < cooldownUntil) {
            reasoner.sendMessage(Text.translatable("tip.sparkwitch.criminal_reasoner.cooldown",
                    (cooldownUntil - now) / 20).formatted(Formatting.GRAY), true);
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (victimId.equals(reasoner.getUuid()) || !game.isPlayerDead(victimId)) {
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
        component.sync();
        // Replay line: who reasoned which victim was killed by whom, and whether it held.
        // 回放记录：谁推测哪名死者被谁所杀，以及是否猜对。
        net.minecraft.nbt.NbtCompound replay = new net.minecraft.nbt.NbtCompound();
        replay.putUuid("victim", victimId);
        replay.putUuid("suspect", suspectId);
        replay.putBoolean("success", correct);
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                reasoner, CriminalReasonerRules.ROLE_ID,
                world.getServer().getPlayerManager().getPlayer(suspectId), replay);
    }

    /** Spends one charge for a 20 s invisibility; a second activation waits for it to end. 消耗充能隐身 20 秒。 */
    public static void activateInvisibility(ServerPlayerEntity reasoner) {
        if (!isReasoner(reasoner) || !GameFunctions.isPlayerPlayingAndAlive(reasoner)) {
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

    public static void clearRoundState() {
        SESSIONS.clear();
        COOLDOWN_UNTIL.clear();
        NOTICES.clear();
    }

    private static void tick(MinecraftServer server) {
        if (NOTICES.isEmpty()) {
            return;
        }
        long now = server.getTicks();
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

    private static boolean isReasoner(PlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        var role = GameWorldComponent.KEY.get(world).getRole(player);
        return role != null && CriminalReasonerRules.ROLE_ID.equals(role.identifier());
    }

    private record Session(UUID id, long openedAtTick) {
        boolean expired(long now) {
            return now - openedAtTick > 30 * 20;
        }
    }

    private record PendingNotice(UUID reasonerId, long dueTick) {
    }
}
