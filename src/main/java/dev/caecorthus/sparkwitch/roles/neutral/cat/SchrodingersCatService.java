package dev.caecorthus.sparkwitch.roles.neutral.cat;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkfactionapi.api.replay.SparkReplayApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSchrodingersCatBridge;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRoundComponent;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.caecorthus.sparkwitch.roles.civilian.judge.PoliceSlotAssignmentService;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelService;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithRoleAnnouncementService;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.cca.ScoreboardRoleSelectorComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopUtils;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of 薛定谔之猫. {@link #onDeathCommit} runs at Wathe's death-commit point inside {@code killPlayer}, after
 * every veto, shield, vest, elixir and revival had its turn (owner 2026-11-04: protections are spent first), so only a
 * death that would really happen reaches it. A cat killed there by another player of a side it can join is never
 * marked dead: it vanishes into a pinned spectator "spectre" for {@link SchrodingersCatRules#SPECTRE_TICKS} (the
 * FiendFeign pattern: still playing and alive in Wathe, hidden by the vanilla spectator rule, untouchable through
 * {@link #isSpectre}), then gets up where it was hit with the killer's side: Insider, Bewitched, plain killer or a random
 * unheld police role. The new role goes through {@code RoleAssigned} like a Bewitched promotion, so every kit, skill
 * and shop is the role's own. State is round-scoped and server-thread only; it is dropped at every round boundary,
 * reset, disconnect and server stop.
 * 薛定谔之猫的服务端。{@link #onDeathCommit} 运行于 {@code killPlayer} 内 Wathe 的死亡落定点，此时所有否决、护盾、
 * 防弹衣、药剂与复活都已先行结算（所有者 2026-11-04：先消耗保护），因此只有真正会发生的死亡才会到达这里。在此被另一名
 * 可加入阵营的玩家杀死的猫不会被标记死亡：它消失为被固定的旁观“幽魂” {@link SchrodingersCatRules#SPECTRE_TICKS}（沿用
 * 魔人假死做法：在 Wathe 中仍在参与且存活，被原版旁观规则隐藏，并经 {@link #isSpectre} 免疫一切击杀），随后在被击中处
 * 起身并倒向凶手的阵营：内应、魔化使、普通杀手或随机未被占用的警职。新身份与魔化使晋升一样经过 {@code RoleAssigned}，
 * 因此装备、技能与商店都是该职业自己的。状态仅限本局且只在服务端线程使用；每个回合边界、重置、断线与服务器停止时清除。
 */
public final class SchrodingersCatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchrodingersCatService.class);
    static final String COLLAPSING_KEY = "message.sparkwitch.schrodingers_cat.collapsing";
    static final String TRANSFORMED_KEY = "message.sparkwitch.schrodingers_cat.transformed";
    static final String TRAITS_CHANGED_KEY = "message.sparkwitch.schrodingers_cat.traits_changed";
    /** A spectre farther than this from its anchor is pulled back. / 幽魂离锚点超过此距离即被拉回。 */
    private static final double PINNED_DRIFT = 0.05;
    private static final double GROUND_SNAP_DEPTH = 3.0;
    /**
     * Runs before Wathe's default-phase disconnect listener, which kills a leaving participant on the spot (reason
     * {@code escaped}): the spectre must be released first, or the HEAD veto would refuse that kill.
     * 先于 Wathe 默认阶段的断线监听器执行（后者当场以 {@code escaped} 杀死离开的参与者）：必须先解除幽魂，否则 HEAD 否决会拒绝
     * 这次击杀。
     */
    private static final Identifier DISCONNECT_PHASE = SparkWitch.id("schrodingers_cat_release");
    /**
     * Cats in their spectre window, by player. Written on the server thread only; concurrent so the voice plugin's
     * microphone thread may read it. / 处于幽魂阶段的猫，按玩家索引。只在服务端线程写入；使用并发表，使语音插件的麦克风
     * 线程也可读取。
     */
    private static final Map<UUID, Spectre> SPECTRES = new ConcurrentHashMap<>();
    private static boolean registered;

    private SchrodingersCatService() {
    }

    private record Spectre(RegistryKey<World> world, Vec3d anchor, float yaw, float pitch, GameMode previousMode,
                           SchrodingersCatRules.Side side, long endTick) {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        ServerTickEvents.END_SERVER_TICK.register(SchrodingersCatService::tick);
        ResetPlayer.EVENT.register(SchrodingersCatService::onReset);
        ServerPlayConnectionEvents.DISCONNECT.addPhaseOrdering(DISCONNECT_PHASE, Event.DEFAULT_PHASE);
        // Fabric may fire DISCONNECT on the network thread; execute() runs at once on the server thread and otherwise
        // queues ahead of Wathe's own queued kill. / Fabric 可能在网络线程触发 DISCONNECT；execute() 在服务端线程上立即
        // 执行，否则排在 Wathe 自身排队的击杀之前。
        ServerPlayConnectionEvents.DISCONNECT.register(DISCONNECT_PHASE, (handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> onDisconnect(player));
        });
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> SPECTRES.clear());
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> SPECTRES.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SPECTRES.clear());
    }

    public static boolean isCatRole(@Nullable Role role) {
        return role != null && SchrodingersCatRules.isCatId(role.identifier());
    }

    /**
     * Whether this player is a cat in its spectre window: every {@code killPlayer} on it is refused at HEAD, forced
     * kills included (they ignore a {@code KillPlayer.BEFORE} cancel), and the spectator guards in {@code mixin/cat/}
     * close the Swapper, spectator teleport and camera possession.
     * 该玩家是否为处于幽魂阶段的猫：对其的每次 {@code killPlayer} 都在 HEAD 被拒绝，包括忽略 {@code KillPlayer.BEFORE}
     * 取消结果的强制击杀；{@code mixin/cat/} 中的旁观守卫封住换位、旁观传送与视角附身。
     */
    public static boolean isSpectre(@Nullable ServerPlayerEntity player) {
        return player != null && SPECTRES.containsKey(player.getUuid());
    }

    /** By UUID, for packet receivers that name players. / 按 UUID 判断，供以 UUID 指定玩家的数据包接收器使用。 */
    public static boolean isSpectre(@Nullable UUID player) {
        return player != null && SPECTRES.containsKey(player);
    }

    /**
     * Death-commit seam ({@code mixin/cat/GameFunctionsSchrodingersCatMixin}), called right before Wathe turns the
     * victim into a spectator and marks it dead. Returns true when the death is taken over: the caller cancels the rest
     * of {@code killPlayer}, so there is no death mark, corpse, kill reward, drop or {@code KillPlayer.AFTER}, exactly
     * like any other save at this point (Tofana). Every non-cat victim returns false at once.
     * 死亡落定接缝（{@code mixin/cat/GameFunctionsSchrodingersCatMixin}），在 Wathe 把受害者设为旁观并标记死亡之前调用。
     * 返回 true 表示接管这次死亡：调用方取消 {@code killPlayer} 的剩余部分，因此没有死亡标记、尸体、击杀奖励、掉落或
     * {@code KillPlayer.AFTER}，与此处的其他救援（托法娜）完全一致。非猫受害者立即返回 false。
     */
    public static boolean onDeathCommit(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                        @Nullable Identifier deathReason) {
        ServerWorld world = victim.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        // A swallowed cat dies (FiendFeign's guard): NoellesRoles pins a swallowed player's camera and game mode to the
        // Taotie, so a spectre could never get up. / 被吞的猫照常死亡（沿用魔人假死守卫）：NoellesRoles 把被吞者的视角与
        // 游戏模式锁定在饕餮身上，幽魂无法起身。
        if (!isCatRole(game.getRole(victim)) || isSpectre(victim) || NoellesTaotieSeekerBridge.isSwallowed(victim)) {
            return false;
        }
        boolean distinctKiller = killer != null && !killer.getUuid().equals(victim.getUuid());
        SchrodingersCatRules.Side side = distinctKiller ? sideOf(game, killer) : SchrodingersCatRules.Side.NONE;
        if (!SchrodingersCatRules.transforms(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                distinctKiller, deathReason, side)) {
            return false;
        }
        startSpectre(victim, world, side);
        return true;
    }

    /** The killer's side, read from the live role map and SparkFactionAPI. / 凶手的阵营，读取实时身份表与 SparkFactionAPI。 */
    private static SchrodingersCatRules.Side sideOf(GameWorldComponent game, ServerPlayerEntity killer) {
        Role role = game.getRole(killer);
        return SchrodingersCatRules.sideOf(
                InsiderParticipation.isTeamRole(role),
                WitchFactionRules.isWitchFactionMember(role),
                SparkFactionApi.resolveEffectiveFaction(killer, game));
    }

    private static void onTaskComplete(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player) && isCatRole(game.getRole(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(SchrodingersCatRules.TASK_MONEY_REWARD);
        }
    }

    private static void startSpectre(ServerPlayerEntity cat, ServerWorld world, SchrodingersCatRules.Side side) {
        GameMode previous = cat.interactionManager.getGameMode();
        cat.stopRiding();
        if (cat.isSleeping()) {
            cat.wakeUp();
        }
        // Drop a charging knife or bat without a release, and any open screen. / 放下蓄力中的刀或球棒（不触发释放），关闭界面。
        cat.clearActiveItem();
        cat.closeHandledScreen();
        Spectre spectre = new Spectre(world.getRegistryKey(), groundAnchor(world, cat), cat.getYaw(), cat.getPitch(),
                previous == GameMode.SPECTATOR ? GameMode.ADVENTURE : previous, side,
                world.getTime() + SchrodingersCatRules.SPECTRE_TICKS);
        SPECTRES.put(cat.getUuid(), spectre);
        // What Wathe does right after the commit point, which the takeover skips: a spent poison never kills again, and
        // its poisoner is forgotten too (killPlayer credits a killerless death to a lingering poisoner).
        // Wathe 在落定点之后会做、而接管跳过的事：用尽的毒不会再次致死，下毒者也一并清除（killPlayer 会把无凶手的死亡记给
        // 残留的下毒者）。
        PlayerPoisonComponent poison = PlayerPoisonComponent.KEY.get(cat);
        poison.reset();
        poison.poisoner = null;
        poison.sync();
        // A Knight duel ends on a duelist's death through KillPlayer.AFTER, which the takeover skips.
        // 骑士决斗经由 KillPlayer.AFTER 在决斗者死亡时结束，而接管跳过了它。
        KnightDuelService.endForTakenOverDeath(cat);
        cat.setVelocity(Vec3d.ZERO);
        cat.velocityModified = true;
        cat.fallDistance = 0.0F;
        hold(cat, spectre);
        cat.sendMessage(Text.translatable(COLLAPSING_KEY).withColor(SchrodingersCatRules.COLOR), true);
    }

    /**
     * "In place" (owner 2026-11-03): the cat's feet, or the floor right below when it was hit mid-air, so it never
     * gets up falling. With no floor within {@link #GROUND_SNAP_DEPTH} blocks the anchor stays where it is.
     * “原地”（所有者 2026-11-03）：猫的脚下；若在半空被击中则取其正下方的地面，绝不在下落中起身。
     * {@link #GROUND_SNAP_DEPTH} 格内没有地面时锚点保持原处。
     */
    private static Vec3d groundAnchor(ServerWorld world, ServerPlayerEntity cat) {
        Vec3d feet = cat.getPos();
        if (cat.isOnGround()) {
            return feet;
        }
        BlockHitResult hit = world.raycast(new RaycastContext(feet.add(0.0, 0.1, 0.0),
                feet.subtract(0.0, GROUND_SNAP_DEPTH, 0.0), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, cat));
        return hit.getType() == HitResult.Type.BLOCK ? new Vec3d(feet.x, hit.getPos().y, feet.z) : feet;
    }

    private static void tick(MinecraftServer server) {
        if (SPECTRES.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Spectre> entry : List.copyOf(SPECTRES.entrySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Spectre spectre = entry.getValue();
            if (player == null) {
                SPECTRES.remove(entry.getKey());
                continue;
            }
            try {
                tickSpectre(player, spectre);
            } catch (RuntimeException exception) {
                SPECTRES.remove(entry.getKey());
                LOGGER.error("Schrödinger's Cat spectre tick failed for {}", player.getUuid(), exception);
            }
        }
    }

    private static void tickSpectre(ServerPlayerEntity player, Spectre spectre) {
        ServerWorld world = player.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        boolean valid = world.getRegistryKey().equals(spectre.world())
                && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && isCatRole(game.getRole(player));
        if (!valid) {
            // Round over, re-roled (admin) or marked dead: no transformation, only the body comes back.
            // 回合结束、被改职业（管理员）或已被判死：不转化，只恢复身体。
            SPECTRES.remove(player.getUuid());
            release(player, spectre);
        } else if (world.getTime() >= spectre.endTick()) {
            SPECTRES.remove(player.getUuid());
            transform(player, world, game, spectre);
        } else {
            hold(player, spectre);
        }
    }

    /**
     * Every spectre tick: spectator, own camera, still, pinned to the anchor (a spectator flies through walls, so even
     * a small drift is pulled back at once). No invulnerable flag: a spectator already is, and the HEAD veto covers
     * {@code killPlayer}.
     * 幽魂期间每刻：旁观模式、自身视角、静止并固定在锚点（旁观者可以穿墙，因此轻微偏移也立即拉回）。不设置无敌标记：
     * 旁观者本就无敌，HEAD 否决覆盖 {@code killPlayer}。
     */
    private static void hold(ServerPlayerEntity cat, Spectre spectre) {
        if (cat.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            cat.changeGameMode(GameMode.SPECTATOR);
        }
        if (cat.getCameraEntity() != cat) {
            cat.setCameraEntity(cat);
        }
        cat.setVelocity(Vec3d.ZERO);
        Vec3d anchor = spectre.anchor();
        if (cat.getPos().squaredDistanceTo(anchor) > PINNED_DRIFT * PINNED_DRIFT) {
            teleportToAnchor(cat, spectre);
        }
    }

    /** The body back at the anchor with the saved game mode; used by both endings. / 在锚点以保存的游戏模式恢复身体；两种结局共用。 */
    private static void release(ServerPlayerEntity cat, Spectre spectre) {
        cat.setCameraEntity(cat);
        if (GameFunctions.isPlayerPlayingAndAlive(cat) || !GameWorldComponent.KEY.get(cat.getWorld()).isRunning()) {
            cat.changeGameMode(spectre.previousMode());
        }
        if (cat.getWorld().getRegistryKey().equals(spectre.world())) {
            teleportToAnchor(cat, spectre);
        }
        cat.setVelocity(Vec3d.ZERO);
        cat.velocityModified = true;
        cat.fallDistance = 0.0F;
    }

    /**
     * Gets up with the new role: body back, role written under the replay cause, {@code RoleAssigned} for the role's
     * own kit and skills, the higher of the old and new balance kept, the shop rebuilt, ineligible traits swapped
     * through the SparkTraits facade, then the totem sound, the role announcement and a chat line.
     * 以新身份起身：恢复身体，在回放原因下写入身份，触发 {@code RoleAssigned} 发放该职业自己的装备与技能，保留新旧余额中
     * 较高者并重建商店，经 SparkTraits 门面替换不合资格的词条，随后播放图腾音效、公告新身份并发送聊天提示。
     */
    private static void transform(ServerPlayerEntity cat, ServerWorld world, GameWorldComponent game,
                                  Spectre spectre) {
        release(cat, spectre);
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(cat);
        int balance = shop.getBalance();
        Role next = writeRole(cat, game, roleFor(spectre.side(), world, game), spectre.side());
        if (next == null) {
            LOGGER.error("Schrödinger's Cat {} could not take a {} role; it stays a cat", cat.getUuid(), spectre.side());
            return;
        }
        try {
            RoleAssigned.EVENT.invoker().assignRole(cat, next);
        } catch (RuntimeException exception) {
            // The role is committed: log the listener failure, never undo. / 身份已提交：记录监听器故障，绝不撤销。
            LOGGER.error("Schrödinger's Cat transformation committed but a role-assignment listener failed for {}",
                    cat.getUuid(), exception);
        }
        try {
            // Kits several roles hand out only at the round start. / 多个职业只在开局发放的装备。
            SchrodingersCatLoadouts.grantLateLoadout(cat, next);
        } catch (RuntimeException exception) {
            LOGGER.error("Schrödinger's Cat late loadout failed for {} as {}", cat.getUuid(), next.identifier(),
                    exception);
        } finally {
            // RoleAssigned may reset the balance to the role's starting money; the cat never loses what it earned.
            // RoleAssigned 可能把余额重置为该职业的开局金币；猫绝不损失已赚到的金币。
            shop.setBalance(Math.max(balance, shop.getBalance()));
            shop.initializeShop(ShopUtils.getShopEntriesForPlayer(cat));
            game.sync();
            shop.sync();
        }
        SparkTraitsSchrodingersCatBridge.replaceIneligibleTraits(cat, (lost, gained) ->
                cat.sendMessage(Text.translatable(TRAITS_CHANGED_KEY, joined(lost), joined(gained)), false));
        Vec3d anchor = spectre.anchor();
        world.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS,
                1.0F, 1.0F);
        WraithRoleAnnouncementService.announceCurrentRole(cat);
        cat.sendMessage(Text.translatable(TRANSFORMED_KEY,
                Text.translatable(RoleDisplayTextRules.roleTranslationKey(next)).withColor(next.color())), false);
    }

    /**
     * Writes the role under the replay cause and checks it stuck: a role guard may refuse {@code addRole} (Emma's one
     * claim per round). A refused police role falls back to the plain Vigilante; null when nothing stuck.
     * 在回放原因下写入身份并确认写入成功：职业守卫可能拒绝 {@code addRole}（艾玛每局唯一的声明）。被拒的警职退回普通义警；
     * 均未写入时返回 null。
     */
    private static @Nullable Role writeRole(ServerPlayerEntity cat, GameWorldComponent game, Role role,
                                           SchrodingersCatRules.Side side) {
        SparkReplayApi.withRoleChangeCause(SchrodingersCatRules.TRANSFORM_CAUSE, (UUID) null,
                () -> game.addRole(cat, role));
        if (game.getRole(cat) == role) {
            return role;
        }
        if (side != SchrodingersCatRules.Side.CIVILIAN || role == WatheRoles.VIGILANTE) {
            return null;
        }
        SparkReplayApi.withRoleChangeCause(SchrodingersCatRules.TRANSFORM_CAUSE, (UUID) null,
                () -> game.addRole(cat, WatheRoles.VIGILANTE));
        return game.getRole(cat) == WatheRoles.VIGILANTE ? WatheRoles.VIGILANTE : null;
    }

    /**
     * The concrete role for a side. Police is the live pool (owner 2026-11-03 "随机"): a random enabled police variant
     * nobody holds this round, the plain Vigilante only once every variant is held, through the same
     * {@link PoliceSlotAssignmentService#choose} the round start uses.
     * 阵营对应的具体身份。警职取实时警位池（所有者 2026-11-03“随机”）：本局无人担任的随机已启用警察变体，所有变体都有人
     * 担任时才给普通义警，与开局使用同一个 {@link PoliceSlotAssignmentService#choose}。
     */
    private static Role roleFor(SchrodingersCatRules.Side side, ServerWorld world, GameWorldComponent game) {
        return switch (side) {
            case TEAM_JIAHAO -> SparkWitchRoles.insider();
            case WITCH -> SparkWitchRoles.bewitched();
            case KILLER -> WatheRoles.KILLER;
            // NONE never gets here: transforms() refuses it. / NONE 不会到达此处：transforms() 已拒绝。
            case CIVILIAN, NONE -> PoliceSlotAssignmentService.choose(ScoreboardRoleSelectorComponent.KEY
                    .get(world.getScoreboard()).createSelectionContext(world, game, participants(world, game)),
                    role -> EmmaRules.isEmma(role) && !emmaClaimOpen(world));
        };
    }

    /** Emma may be claimed once per round (EmmaRoleClaimMixin). / 艾玛每局只能被声明一次（EmmaRoleClaimMixin）。 */
    private static boolean emmaClaimOpen(ServerWorld world) {
        EmmaRoundComponent emma = EmmaRoundComponent.KEY.get(world);
        return emma.active() && !emma.claimed();
    }

    private static List<ServerPlayerEntity> participants(ServerWorld world, GameWorldComponent game) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (game.hasAnyRole(player.getUuid())) {
                players.add(player);
            }
        }
        return players;
    }

    private static Text joined(List<Text> names) {
        return names.isEmpty() ? Text.literal("-") : Texts.join(names, Text.literal(", "));
    }

    /** Round boundary reset: Wathe already reset the game mode and position. / 回合边界重置：Wathe 已重置游戏模式与位置。 */
    private static void onReset(ServerPlayerEntity player) {
        if (SPECTRES.remove(player.getUuid()) != null) {
            player.setCameraEntity(player);
        }
    }

    /**
     * Leaving mid-spectre: the body is released at once (no transformation), so the saved player data never keeps a
     * living player in spectator mode and Wathe's own disconnect kill that follows (no killer) is not refused.
     * 幽魂期间离线：立即恢复身体（不转化），确保保存的玩家数据不会让活着的玩家停留在旁观模式，且随后 Wathe 自身的断线击杀
     * （没有凶手）不会被拒绝。
     */
    private static void onDisconnect(@Nullable ServerPlayerEntity player) {
        Spectre spectre = player == null ? null : SPECTRES.remove(player.getUuid());
        if (spectre == null) {
            return;
        }
        try {
            release(player, spectre);
        } catch (RuntimeException exception) {
            LOGGER.error("Schrödinger's Cat spectre could not finish for disconnecting {}", player.getUuid(),
                    exception);
        }
    }

    private static void teleportToAnchor(ServerPlayerEntity cat, Spectre spectre) {
        Vec3d anchor = spectre.anchor();
        cat.teleport(cat.getServerWorld(), anchor.x, anchor.y, anchor.z, Set.of(), spectre.yaw(), spectre.pitch());
    }
}
