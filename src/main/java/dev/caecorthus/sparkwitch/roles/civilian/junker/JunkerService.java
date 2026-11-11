package dev.caecorthus.sparkwitch.roles.civilian.junker;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightDuelSkillLock;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroFakeBodies;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 拾荒者 search flow: corpse ledger hooks, the ability-key request, the five-second search and the transfer.
 * <p>Server handler of {@code sparkwitch:ragpicker_search} (the shared NoellesRoles ability key, G). The search is a
 * role-owned skill: it never enters the witch skill registry, the shared witch-skill cooldowns or the witch skill
 * panel. Every gate lives in the pure {@link JunkerSearchRules}; the cooldown is an absolute ready tick in the
 * owner-synced {@link JunkerPlayerComponent}, reached by other features only through
 * {@code compat/cooldown/SparkWitchForcedCooldowns#ragpickerSearch}. Spending looted items is {@link JunkerLootSpend}'s
 * job and replay lines are {@link JunkerReplay}'s.
 * 拾荒者搜刮流程：尸体记录钩子、技能键请求、五秒搜刮与物品转移。
 * <p>{@code sparkwitch:ragpicker_search}（共享的 NoellesRoles 技能键 G）的服务端处理。搜刮是职业自有技能：从不进入
 * 魔女技能注册表、共享的魔女技能冷却或魔女技能面板。所有门槛都在纯规则 {@link JunkerSearchRules} 中；冷却是只同步给
 * 本人的 {@link JunkerPlayerComponent} 上的绝对就绪刻，其他功能只能经
 * {@code compat/cooldown/SparkWitchForcedCooldowns#ragpickerSearch} 作用于它。搜刮所得物品的消耗由
 * {@link JunkerLootSpend} 负责，回放记录由 {@link JunkerReplay} 负责。
 */
public final class JunkerService {
    /** How far the aim box reaches past the corpse's hitbox (lag tolerance from the PR). / 瞄准判定在尸体碰撞箱外的余量。 */
    private static final double AIM_BOX_MARGIN = 0.35D;
    /**
     * Deaths whose snapshot waits for the end of the server tick, so it sees the inventory after every
     * {@code KillPlayer.AFTER} listener (including role kit strips registered after this one). Server thread only.
     * 快照等到服务端刻末尾才拍的死亡，使其看到所有 {@code KillPlayer.AFTER} 监听（包括在此之后注册的职业道具清除）
     * 执行后的背包。仅服务端线程。
     */
    private static final Map<UUID, ServerPlayerEntity> PENDING_SNAPSHOTS = new LinkedHashMap<>();

    private JunkerService() {
    }

    public static void register() {
        // Queued here, taken at END_SERVER_TICK: later AFTER listeners still strip kits from the victim.
        // 在此排队、于 END_SERVER_TICK 拍摄：之后的 AFTER 监听仍会清除受害者的职业道具。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> PENDING_SNAPSHOTS.put(victim.getUuid(), victim));
        ServerTickEvents.END_SERVER_TICK.register(server -> takePendingSnapshots());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_SNAPSHOTS.clear());
        // A dead owner going offline leaves only the snapshot, so retake it from what they really left with. DISCONNECT
        // can fire on the Netty thread, so the retake hops to the server thread.
        // 死者离线后只剩快照，因此按其离开时的真实背包重拍。DISCONNECT 可能在 Netty 线程触发，因此重拍切到服务端线程。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> JunkerLootWorldComponent.KEY.get(player.getServerWorld()).refreshSnapshot(player));
        });
        // Stacks taken from the snapshot while the owner was away leave their saved inventory now.
        // 主人离线期间从快照取走的物品，此时从其存档背包中移除。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            JunkerLootWorldComponent.KEY.get(player.getServerWorld()).applyPendingRemovals(player);
        });
        // Any assignment to the role (round start or mid-round) restarts the 45 s initial cooldown.
        // 任何时候被分配为该职业（开局或回合中）都重新开始 45 秒初始冷却。
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && JunkerRules.isRagpicker(role)) {
                JunkerPlayerComponent.KEY.get(serverPlayer).reset(initialReadyTick(serverPlayer.getServerWorld()));
            }
        });
        // Roles are assigned before this fires; the round-start cooldown counts from the end of initialization.
        // 职业在此之前已分配；回合初始冷却从初始化结束时开始计。
        GameEvents.ON_FINISH_INITIALIZE.register((world, gameComponent) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            JunkerLootWorldComponent.KEY.get(serverWorld).reset();
            PENDING_SNAPSHOTS.clear();
            long readyTick = initialReadyTick(serverWorld);
            for (UUID uuid : gameComponent.getAllWithRole(SparkWitchRoleRegistry.ragpicker())) {
                ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    JunkerPlayerComponent.KEY.get(player).reset(readyTick);
                }
            }
        });
        JunkerLootSpend.register();
        JunkerReplay.register();
    }

    /** The real (raw) role is the 拾荒者. / 真实职业是拾荒者。 */
    public static boolean isJunker(PlayerEntity player) {
        return player != null && JunkerRules.isRagpicker(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /**
     * Called on the server thread for every search request. Payload guards may already drop some refused requests;
     * this re-checks every gate against server state. A refusal costs nothing.
     * 每个搜刮请求都在服务端线程调用。数据包拦截可能已丢弃部分被拒请求；此处按服务端状态重新检查每个门槛。拒绝时不消耗
     * 任何东西。
     */
    public static void handleSearchRequest(ServerPlayerEntity player, UUID bodyUuid) {
        if (player == null || bodyUuid == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        JunkerPlayerComponent component = JunkerPlayerComponent.KEY.get(player);
        JunkerLootWorldComponent loot = JunkerLootWorldComponent.KEY.get(world);
        UUID ownerUuid = bodyOwner(world, bodyUuid);
        ItemStack blocking = JunkerLoot.findBlockingLoot(player);
        JunkerSearchRules.StartVerdict verdict = JunkerSearchRules.startVerdict(
                gates(player),
                component.isSearching(),
                component.searchCooldownRemainingTicks(world.getTime()) == 0,
                ownerUuid != null && isLookingAtBody(player, bodyUuid),
                blocking != null,
                JunkerLoot.findEmptyHotbarSlot(player) >= 0,
                ownerUuid != null && loot.wasSearched(ownerUuid),
                ownerUuid != null && loot.hasLoot(ownerUuid));
        switch (verdict) {
            case START -> component.startSearch(bodyUuid);
            case FEARED -> GrandWitchFearService.sendSkillBlocked(player);
            case BLOCKED -> actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.blocked",
                    blocking.getName().copy().formatted(Formatting.RED)));
            case HOTBAR_FULL -> actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.hotbar_full"));
            case SEARCHED -> actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.searched"));
            case EMPTY -> {
                loot.markSearched(ownerUuid);
                actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.empty"));
            }
            case REFUSE -> {
                // The remaining cooldown lives on the HUD, not in an action-bar toast. / 剩余冷却显示在 HUD 上。
            }
        }
    }

    /** Per-tick gate of a running search; any failure cancels it. / 进行中搜刮的每刻门槛；任何失败都会取消。 */
    static boolean keepsSearching(ServerPlayerEntity player, UUID bodyUuid) {
        return JunkerSearchRules.keepsSearching(gates(player), isLookingAtBody(player, bodyUuid));
    }

    /**
     * The five seconds are up (the same tick has just passed {@link #keepsSearching}). Moves one whole stack from the
     * dead owner into an empty hotbar slot, marks it as loot and starts the cooldown; any other outcome takes nothing
     * and costs nothing.
     * 五秒已满（同一刻刚通过 {@link #keepsSearching}）。从死者身上把一整组物品移到空快捷栏、标记为战利品并进入冷却；
     * 其余结果不拿任何东西，也不消耗任何东西。
     */
    static void completeSearch(ServerPlayerEntity player, UUID bodyUuid) {
        ServerWorld world = player.getServerWorld();
        UUID ownerUuid = bodyOwner(world, bodyUuid);
        if (ownerUuid == null) {
            return;
        }
        JunkerLootWorldComponent loot = JunkerLootWorldComponent.KEY.get(world);
        ItemStack blocking = JunkerLoot.findBlockingLoot(player);
        int slot = JunkerLoot.findEmptyHotbarSlot(player);
        switch (JunkerSearchRules.completionVerdict(blocking != null, slot >= 0, loot.wasSearched(ownerUuid),
                loot.hasLoot(ownerUuid))) {
            case BLOCKED -> actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.blocked",
                    blocking.getName().copy().formatted(Formatting.RED)));
            case HOTBAR_FULL -> actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.hotbar_full"));
            case SEARCHED -> actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.searched"));
            case EMPTY -> {
                loot.markSearched(ownerUuid);
                actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.empty"));
            }
            case TRANSFER -> transfer(player, world, loot, ownerUuid, slot);
        }
    }

    private static void transfer(ServerPlayerEntity player, ServerWorld world, JunkerLootWorldComponent loot,
                                 UUID ownerUuid, int slot) {
        ItemStack reward = loot.takeLoot(ownerUuid, player.getRandom());
        loot.markSearched(ownerUuid);
        if (reward.isEmpty()) {
            actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.empty"));
            return;
        }
        JunkerLoot.mark(reward);
        // Two replay lines, as the owner's spec lists them: the search, then what came out of it; both before the
        // insert, which may merge and empty the stack. / 按所有者给的规格记两条回放：先“搜刮了尸体”，再“搜刮获得了什么”；
        // 都在放入之前记录，因为放入可能合并并清空该堆叠。
        JunkerReplay.recordSearch(player, ownerUuid);
        JunkerReplay.recordFound(player, ownerUuid, reward);
        Text found = reward.getName().copy().formatted(Formatting.GREEN);
        player.getInventory().setStack(slot, reward);
        // Never shortens a forced cooldown raised during the search. / 绝不缩短搜刮期间被强制抬高的冷却。
        JunkerPlayerComponent component = JunkerPlayerComponent.KEY.get(player);
        component.setSearchReadyTick(Math.max(component.getSearchReadyTick(),
                world.getTime() + JunkerRules.SEARCH_COOLDOWN_TICKS));
        actionBar(player, Text.translatable("tip.sparkwitch.ragpicker.found", found));
    }

    /**
     * Aim, range and line of sight: the corpse exists, it is within range of the player's feet, the look ray reaches
     * its widened aim box within range, and a block ray from the eye to the matching point on the REAL hitbox
     * ({@link JunkerSearchRules#lineOfSightTarget}) hits nothing. Rays are COLLIDER casts, so closed doors stay solid
     * through the shared {@code RaycastShapeScope} wrapper.
     * 瞄准、范围与视线：尸体存在、与玩家脚下距离在范围内、视线射线在范围内命中其放宽的瞄准箱，且从眼睛到真实碰撞箱上
     * 对应点（{@link JunkerSearchRules#lineOfSightTarget}）的方块射线没有命中任何东西。射线为 COLLIDER，因此关闭的门经共享的
     * {@code RaycastShapeScope} 包装仍是实心的。
     */
    public static boolean isLookingAtBody(ServerPlayerEntity player, UUID bodyUuid) {
        ServerWorld world = player.getServerWorld();
        if (!(world.getEntity(bodyUuid) instanceof PlayerBodyEntity body) || body.isRemoved()
                || body.squaredDistanceTo(player) > JunkerRules.SEARCH_RANGE * JunkerRules.SEARCH_RANGE) {
            return false;
        }
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(JunkerRules.SEARCH_RANGE));
        Box bodyBox = body.getBoundingBox();
        Optional<Vec3d> target = JunkerSearchRules.lineOfSightTarget(bodyBox, bodyBox.expand(AIM_BOX_MARGIN), eye, end);
        return target.isPresent() && world.raycast(new RaycastContext(eye, target.get(),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player)).getType()
                == HitResult.Type.MISS;
    }

    private static void takePendingSnapshots() {
        if (PENDING_SNAPSHOTS.isEmpty()) {
            return;
        }
        for (ServerPlayerEntity victim : new ArrayList<>(PENDING_SNAPSHOTS.values())) {
            JunkerLootWorldComponent.KEY.get(victim.getServerWorld()).recordDeath(victim);
        }
        PENDING_SNAPSHOTS.clear();
    }

    private static JunkerSearchRules.Gates gates(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return new JunkerSearchRules.Gates(
                JunkerRules.isRagpicker(game.getRole(player)),
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player),
                player.isSpectator(),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                ControlExpertStun.isStunned(player),
                SparkTraitsKillerBridge.isRoleSkillBlocked(player),
                KnightDuelSkillLock.isLocked(player),
                RiftSessionService.isInside(player),
                GrandWitchFearService.isPlayerFeared(player));
    }

    private static long initialReadyTick(ServerWorld world) {
        return world.getTime() + JunkerRules.INITIAL_COOLDOWN_TICKS;
    }

    @Nullable
    private static UUID bodyOwner(ServerWorld world, UUID bodyUuid) {
        // The fake corpse of a 二阶堂希罗 victim has no owner to search: refused silently, never marked searched.
        // 二阶堂希罗受害者的假尸体没有可搜刮的主人：静默拒绝，也绝不标记为已搜刮。
        return world.getEntity(bodyUuid) instanceof PlayerBodyEntity body && !HiroFakeBodies.isFake(body)
                ? body.getPlayerUuid() : null;
    }

    private static void actionBar(ServerPlayerEntity player, Text text) {
        player.sendMessage(text, true);
    }
}
