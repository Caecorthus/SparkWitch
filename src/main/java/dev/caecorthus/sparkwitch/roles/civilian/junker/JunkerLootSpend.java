package dev.caecorthus.sparkwitch.roles.civilian.junker;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;

/**
 * Spends 拾荒者 loot exactly once per real use, and handles the ragpicker's own sneak-click manual destroy (owner,
 * 2026-11-04).
 * <p>Use signals, all server-side and for any holder of a marked stack: Wathe's {@code recordItemUse} (through
 * {@code GameRecordManagerItemUseMixin}, ahead of the Vendetta suppression), a looted room key, lockpick or
 * NoellesRoles master key that really opens a locked door, a Wathe bat swing at full charge on a player, and a charged
 * release (bow, crossbow or spear use action, never the knife) that started the item's cooldown or counted a vanilla
 * {@code USED} stat ({@code LivingEntityJunkerReleaseMixin}). A signal only queues; the queue drains at
 * {@code END_SERVER_TICK}, spends a stack at most once per tick, and skips a stack whose count fell since the previous
 * tick, because then the item already spent itself (a thrown grenade, a drunk cocktail).
 * 拾荒者战利品每次真正使用恰好消耗一次，并处理拾荒者本人潜行右键的主动销毁（所有者 2026-11-04）。
 * <p>使用信号（均在服务端，对任何持有带标记物品的玩家生效）：Wathe 的 {@code recordItemUse}（经
 * {@code GameRecordManagerItemUseMixin}，先于仇杀屏蔽）、搜刮来的房间钥匙/撬锁器/NoellesRoles 万能钥匙真正打开了上锁的门、
 * Wathe 球棒满蓄力击中玩家，以及蓄力松手（弓、弩或矛的使用动作，小刀除外）且松手让该物品进入冷却或计入原版
 * {@code USED} 统计（{@code LivingEntityJunkerReleaseMixin}）。信号只负责排队；队列在 {@code END_SERVER_TICK} 结算，
 * 每个物品每 tick 至多消耗一次；若物品数量比上一 tick 少，说明物品已自行消耗（扔出的手雷、喝掉的鸡尾酒），跳过。
 */
public final class JunkerLootSpend {
    /** Door observer: runs before NoellesRoles can end the chain with ALLOW. / 门观察者：在 NoellesRoles 以 ALLOW 结束链前执行。 */
    private static final Identifier DOOR_OBSERVE_PHASE = SparkWitch.id("ragpicker_loot_door_observe");
    /** Attack observer: only reached when no listener refused the attack. / 攻击观察者：只有没有监听器拒绝攻击时才会执行。 */
    private static final Identifier ATTACK_EFFECTIVE_PHASE = SparkWitch.id("ragpicker_loot_attack_effective");
    private static final Identifier MASTER_KEY_ID = Identifier.of("noellesroles", "master_key");
    private static final Set<String> NON_USE_ACTIONS = Set.of(
            "edit", "reload", "pickup", "dismantle", "poison", "recall", "remote_recall", "transfer");
    private static final double DESTROY_REACH = 4.5D;
    private static final Ledger<ItemStack> LEDGER = new Ledger<>();
    private static boolean registered;

    private JunkerLootSpend() {
    }

    /** Registers every spend hook; called once from {@link JunkerService#register()}. / 注册全部消耗钩子。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.register(JunkerLootSpend::onUseItem);
        DoorInteraction.EVENT.addPhaseOrdering(DOOR_OBSERVE_PHASE, Event.DEFAULT_PHASE);
        DoorInteraction.EVENT.register(DOOR_OBSERVE_PHASE, JunkerLootSpend::onDoorInteraction);
        AttackEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, ATTACK_EFFECTIVE_PHASE);
        AttackEntityCallback.EVENT.register(ATTACK_EFFECTIVE_PHASE, JunkerLootSpend::onAttackEntity);
        ServerTickEvents.END_SERVER_TICK.register(JunkerLootSpend::endServerTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LEDGER.clear());
    }

    /**
     * Wathe {@code recordItemUse} HEAD (server thread): Wathe and the add-ons call it only on accepted uses. The used
     * stack is the active-use hand, else the main hand, else the off hand holding that item; a record whose id is not
     * an item id (a replay id) spends nothing.
     * Wathe {@code recordItemUse} 的 HEAD（服务端线程）：Wathe 与附属只在被接受的使用时调用它。被使用的物品取正在使用的手，
     * 否则取持有该物品的主手、再否则副手；记录 id 不是物品 id（回放 id）时不消耗。
     */
    public static void onRecordedItemUse(@Nullable ServerPlayerEntity actor, @Nullable Identifier itemId,
                                         @Nullable NbtCompound extra) {
        if (actor == null || itemId == null || !actor.getServer().isOnThread()
                || (extra != null && isNonUseAction(extra.getString("action")))) {
            return;
        }
        Item item = Registries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return;
        }
        ItemStack used = actingStack(actor, item);
        if (JunkerLoot.isDestroyable(used)) {
            queue(actor, used, used.getCount(), null);
        }
    }

    /**
     * {@code LivingEntity.stopUsingItem} before the release; null when this release cannot spend loot.
     * {@code LivingEntity.stopUsingItem} 松手前的状态；此次松手不可能消耗战利品时为 null。
     */
    public static @Nullable ReleaseProbe beforeRelease(ServerPlayerEntity player) {
        if (!player.isUsingItem()) {
            return null;
        }
        ItemStack active = player.getActiveItem();
        if (!JunkerLoot.isDestroyable(active) || active.isOf(WatheItems.KNIFE)
                || !isChargedRelease(active.getUseAction())) {
            return null;
        }
        Item item = active.getItem();
        return new ReleaseProbe(active, item, active.getCount(),
                player.getItemCooldownManager().isCoolingDown(item),
                player.getStatHandler().getStat(Stats.USED.getOrCreateStat(item)));
    }

    /**
     * After the release: it was a real throw or shot only if it started the item's cooldown or counted a USED stat.
     * 松手之后：只有让物品进入冷却或计入 USED 统计，才算一次真正的投掷或射击。
     */
    public static void afterRelease(ServerPlayerEntity player, ReleaseProbe probe) {
        boolean cooldownStarted = !probe.coolingDown()
                && player.getItemCooldownManager().isCoolingDown(probe.item());
        boolean counted = player.getStatHandler().getStat(Stats.USED.getOrCreateStat(probe.item())) > probe.usedStat();
        if (cooldownStarted || counted) {
            queue(player, probe.stack(), probe.count(), null);
        }
    }

    /** Pre-release state of a looted charged item. / 搜刮所得蓄力物品松手前的状态。 */
    public record ReleaseProbe(ItemStack stack, Item item, int count, boolean coolingDown, int usedStat) {
    }

    /**
     * {@code recordItemUse} actions that are not a use of the held stack: Wathe note text edit ({@code edit}), Hunter
     * shotgun reload and placed-trap pickup/dismantle/poison ({@code reload}, {@code pickup}, {@code dismantle},
     * {@code poison}), Seeker car recall ({@code recall}, {@code remote_recall}) and the NoellesRoles bomb hand-off
     * ({@code transfer}).
     * 并非使用手中物品的 {@code recordItemUse} 动作：Wathe 便条文字编辑、猎人霰弹枪装弹与已放置陷阱的拾取/拆除/涂毒、
     * 探索者遥控车召回、NoellesRoles 炸弹传递。
     */
    static boolean isNonUseAction(String action) {
        return NON_USE_ACTIONS.contains(action);
    }

    static boolean isChargedRelease(UseAction action) {
        return action == UseAction.BOW || action == UseAction.CROSSBOW || action == UseAction.SPEAR;
    }

    /**
     * Manual destroy, both sides: a real ragpicker sneak + right-clicks with destroyable loot in the main hand while no
     * block is within reach (current rotation, so client prediction and server agree). Every other case, including any
     * other holder of the loot, passes untouched; the synced Wathe role lets the client decide the same way.
     * 主动销毁（双端）：真正的拾荒者主手持可消耗的战利品潜行右键，且触及范围内没有方块（使用当前朝向，客户端预测与服务端一致）。
     * 其余情况（包括其他持有战利品的玩家）一律原样放行；Wathe 职业已同步，客户端据此做出相同判断。
     */
    private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND || !player.isSneaking() || player.isSpectator()
                || !JunkerLoot.isDestroyable(stack)
                || !JunkerRules.isRagpicker(GameWorldComponent.KEY.get(world).getRole(player))
                || !aimsAtAir(player)) {
            return TypedActionResult.pass(stack);
        }
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            Text name = stack.getName().copy().formatted(Formatting.GRAY);
            stack.decrement(1);
            serverPlayer.sendMessage(Text.translatable("tip.sparkwitch.ragpicker.destroyed", name), true);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    /**
     * No block outline within reach along the current yaw and pitch. {@code Entity.raycast} would take a player's yaw
     * from {@code headYaw}, which lags the yaw the right-click packet has just set.
     * 沿当前偏航与俯仰在触及范围内没有方块轮廓。{@code Entity.raycast} 对玩家取 {@code headYaw}，落后于右键数据包刚设置的偏航。
     */
    private static boolean aimsAtAir(PlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVector().multiply(DESTROY_REACH));
        return player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS;
    }

    /**
     * A looted door tool on a closed, locked door is spent next tick only if the door is open by then.
     * 搜刮来的开门工具作用于关闭且上锁的门时，只有门到下一 tick 确实打开了才消耗。
     */
    private static DoorInteraction.DoorInteractionResult onDoorInteraction(
            DoorInteraction.DoorInteractionContext context) {
        if (context.isServerSide() && !context.isOpen() && !context.isBlasted()
                && context.getPlayer() instanceof ServerPlayerEntity player) {
            // The context holds a copy; the real stack is the main hand Wathe read. / 上下文只是副本；真实物品是 Wathe 读取的主手。
            ItemStack held = player.getMainHandStack();
            if (JunkerLoot.isDestroyable(held) && unlocksDoor(context, held)) {
                DoorBlockEntity door = context.getEntity();
                queue(player, held, held.getCount(), () -> !door.isRemoved() && door.isOpen());
            }
        }
        return DoorInteraction.DoorInteractionResult.PASS;
    }

    private static boolean unlocksDoor(DoorInteraction.DoorInteractionContext context, ItemStack held) {
        if (held.isOf(WatheItems.KEY)) {
            return context.getInteractionType() == DoorInteraction.DoorInteractionType.USE_KEY;
        }
        // An unlocked small door opens for a bare hand, so opening it is no use of the tool.
        // 未上锁的小门空手也能打开，因此打开它不算使用工具。
        boolean locked = context.requiresKey() || context.getDoorType() == DoorInteraction.DoorType.TRAIN_DOOR;
        if (held.isOf(WatheItems.LOCKPICK)) {
            return locked && context.getInteractionType() == DoorInteraction.DoorInteractionType.USE_LOCKPICK;
        }
        Item masterKey = Registries.ITEM.get(MASTER_KEY_ID);
        return locked && masterKey != Items.AIR && held.isOf(masterKey);
    }

    /**
     * Wathe's bat kill condition ({@code PlayerEntityMixin#attack}): main-hand bat, player target, full charge.
     * Wathe 的球棒击杀条件（{@code PlayerEntityMixin#attack}）：主手球棒、目标为玩家、满蓄力。
     */
    private static ActionResult onAttackEntity(PlayerEntity player, World world, Hand hand, Entity entity,
                                               @Nullable EntityHitResult hit) {
        if (!world.isClient && player instanceof ServerPlayerEntity attacker
                && entity instanceof ServerPlayerEntity target && !target.isSpectator()) {
            ItemStack held = attacker.getMainHandStack();
            if (held.isOf(WatheItems.BAT) && JunkerLoot.isDestroyable(held)
                    && attacker.getAttackCooldownProgress(0.5F) >= 1.0F) {
                queue(attacker, held, held.getCount(), null);
            }
        }
        return ActionResult.PASS;
    }

    private static ItemStack actingStack(ServerPlayerEntity player, Item item) {
        if (player.isUsingItem()) {
            // An emptied active stack spent itself inside finishUsing. / 正在使用的物品已清空：它在 finishUsing 中自行消耗了。
            ItemStack active = player.getStackInHand(player.getActiveHand());
            if (active.isEmpty() || active.isOf(item)) {
                return active;
            }
        }
        if (player.getMainHandStack().isOf(item)) {
            return player.getMainHandStack();
        }
        return player.getOffHandStack().isOf(item) ? player.getOffHandStack() : ItemStack.EMPTY;
    }

    private static void queue(ServerPlayerEntity player, ItemStack stack, int countNow,
                              @Nullable BooleanSupplier condition) {
        LEDGER.request(stack, countNow, () -> !player.isRemoved() && holds(player, stack)
                && JunkerLoot.isDestroyable(stack) && (condition == null || condition.getAsBoolean()));
    }

    /** The exact stack is still in the player's inventory, not dropped or moved into a container. / 该物品仍在玩家背包中。 */
    private static boolean holds(ServerPlayerEntity player, ItemStack stack) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot) == stack) {
                return true;
            }
        }
        return false;
    }

    private static void endServerTick(MinecraftServer server) {
        for (ItemStack stack : LEDGER.drain(ItemStack::getCount)) {
            stack.decrement(1);
        }
        // Next tick's baselines: the loot each player could use from a hand. / 下一 tick 的基线：每名玩家能从手上使用的战利品。
        LEDGER.clearSnapshot();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerInventory inventory = player.getInventory();
            for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
                snapshot(inventory.getStack(slot));
            }
            snapshot(inventory.getStack(PlayerInventory.OFF_HAND_SLOT));
        }
    }

    private static void snapshot(ItemStack stack) {
        if (JunkerLoot.isLoot(stack)) {
            LEDGER.snapshot(stack, stack.getCount());
        }
    }

    /**
     * Pure per-tick spend bookkeeping, generic over the stack type so it is testable without a bootstrapped game.
     * <p>A request's baseline is the higher of the stack's count at the previous tick's end (if it was then in a
     * hotbar or off-hand slot) and its count at the request. A stack whose count fell below its baseline spent itself
     * during the use and is skipped, whether the item decremented before or after its record. A drain spends each
     * stack at most once, through the first of its requests whose condition still holds.
     * 纯逻辑的每 tick 消耗账本，对物品类型泛型，便于在未引导游戏的测试中使用。
     * <p>请求的基线取“上一 tick 结束时的数量（若当时在快捷栏或副手）”与“请求时的数量”中较大者。数量低于基线的物品在使用中
     * 已自行消耗，跳过，无论该物品是在记录之前还是之后扣减。每次结算每个物品至多消耗一次，取其第一个条件仍成立的请求。
     */
    static final class Ledger<S> {
        private final Map<S, Integer> snapshot = new IdentityHashMap<>();
        private final List<Request<S>> pending = new ArrayList<>();

        void snapshot(S stack, int count) {
            snapshot.put(stack, count);
        }

        void clearSnapshot() {
            snapshot.clear();
        }

        void request(S stack, int countNow, BooleanSupplier condition) {
            pending.add(new Request<>(stack, baseline(snapshot.get(stack), countNow), condition));
        }

        List<S> drain(ToIntFunction<S> currentCount) {
            if (pending.isEmpty()) {
                return List.of();
            }
            List<Request<S>> due = new ArrayList<>(pending);
            pending.clear();
            Set<S> spent = Collections.newSetFromMap(new IdentityHashMap<>());
            List<S> result = new ArrayList<>();
            for (Request<S> request : due) {
                if (!spent.contains(request.stack()) && request.condition().getAsBoolean()
                        && shouldSpend(request.baseline(), currentCount.applyAsInt(request.stack()))) {
                    spent.add(request.stack());
                    result.add(request.stack());
                }
            }
            return result;
        }

        int pendingCount() {
            return pending.size();
        }

        void clear() {
            snapshot.clear();
            pending.clear();
        }

        static int baseline(@Nullable Integer snapshotCount, int countNow) {
            return snapshotCount == null ? countNow : Math.max(snapshotCount, countNow);
        }

        static boolean shouldSpend(int baseline, int currentCount) {
            return currentCount > 0 && currentCount >= baseline;
        }

        private record Request<S>(S stack, int baseline, BooleanSupplier condition) {
        }
    }
}
