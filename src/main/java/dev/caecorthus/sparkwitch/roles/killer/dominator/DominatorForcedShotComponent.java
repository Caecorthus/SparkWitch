package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Server-only forced-shot sequence on the marked player: stash the held stack, issue a marked revolver in the same
 * hotbar slot for 1 s, then confiscate it and settle the stash. The sequence grants no protection by itself (see
 * {@link DominatorForcedShotScope}); it never reads or writes the player's item cooldowns.
 * <p>Stash settlement (stable contract): expiry, a disconnect (before Wathe's disconnect kill) and a mid-round role
 * change put the stack back; a committed death puts it back right before Wathe's own death-drop loop
 * ({@code GameFunctionsForcedShotDeathMixin}), so Wathe drops or keeps it exactly as it would have; a SparkTraits
 * intercepted death (Last Stand) is not a death; a reset, round finalize or a load from disk discards it, because the
 * inventory it belonged to is gone.</p>
 * 被标记者身上的仅服务端强制开枪序列：收起手持物品，在同一快捷栏槽位发一把带标记的左轮 1 秒，之后没收并结算原物品。
 * 序列本身不提供任何保护（见 {@link DominatorForcedShotScope}），也从不读写该玩家的物品冷却。
 * 原物品结算（稳定契约）：到期、断线（先于 Wathe 的断线击杀）与回合中途换职业时放回；死亡确定时在 Wathe 自身的死亡掉落
 * 循环之前放回，使 Wathe 按原本的方式掉落或保留；SparkTraits 拦截的死亡（背水一战）不算死亡；重置、回合结算或从存档读取时
 * 丢弃，因为它所属的物品栏已不存在。
 */
public final class DominatorForcedShotComponent implements ServerTickingComponent {
    public static final int DURATION_TICKS = 20;
    // A small orbit around the controlled player's body centre, kept under half a block even with the vertical sway.
    // 环绕被控制玩家身体中心的小粒子圈，加上垂直浮动后距离仍不超过半米。
    private static final double ORBIT_RADIUS = 0.45D;
    private static final double ORBIT_VERTICAL_SPREAD = 0.15D;
    private static final int ORBIT_PARTICLES_PER_TICK = 3;
    public static final ComponentKey<DominatorForcedShotComponent> KEY = ComponentRegistry.getOrCreate(
            DominatorRules.FORCED_SHOT_ID,
            DominatorForcedShotComponent.class
    );
    // Only "a sequence was running": its stash and marked gun are purged, never resumed, after a load.
    // 只记录“序列进行中”：读档后清除其原物品与标记左轮，绝不恢复。
    private static final String ACTIVE = "Active";

    private final PlayerEntity player;
    private boolean active;
    private int slot = -1;
    private ItemStack originalStack = ItemStack.EMPTY;
    private int remainingTicks;
    private boolean purgeAfterLoad;

    public DominatorForcedShotComponent(PlayerEntity player) {
        this.player = player;
    }

    /** True while {@code player}'s 1 s sequence runs. / {@code player} 的 1 秒序列进行中时为真。 */
    public static boolean isSequenceActive(@Nullable PlayerEntity player) {
        return player != null
                && KEY.maybeGet(player)
                .map(component -> component.active && component.remainingTicks > 0)
                .orElse(false);
    }

    /**
     * Records the selected hotbar slot, stashes its stack and issues the marked revolver. Any open container is
     * closed first so the gun can never be moved into it. False when a sequence is running or no hotbar slot is
     * selected. / 记录当前快捷栏槽位、收起其物品并发放标记左轮；先关闭已打开的容器，使左轮无法被移入其中。已有序列或
     * 无法解析快捷栏槽位时返回 false。
     */
    public boolean begin() {
        if (active || !(player instanceof ServerPlayerEntity owner)) {
            return false;
        }
        PlayerInventory inventory = owner.getInventory();
        int selected = inventory.selectedSlot;
        if (!PlayerInventory.isValidHotbarIndex(selected)) {
            return false;
        }
        if (owner.currentScreenHandler != owner.playerScreenHandler) {
            owner.closeHandledScreen();
        }
        purgeAfterLoad = false;
        slot = selected;
        originalStack = inventory.getStack(selected).copy();
        ItemStack gun = new ItemStack(WatheItems.REVOLVER);
        gun.set(SparkWitchDataComponents.FORCED_SHOT_GUN, true);
        inventory.setStack(selected, gun);
        inventory.markDirty();
        owner.currentScreenHandler.syncState();
        active = true;
        remainingTicks = DURATION_TICKS;
        ServerPlayNetworking.send(owner, new DominatorForcedShotS2CPacket(DURATION_TICKS));
        return true;
    }

    @Override
    public void serverTick() {
        if (purgeAfterLoad && player instanceof ServerPlayerEntity owner) {
            // Global: a vanilla death before a respawn copy may have dropped the gun into the world.
            // 全局清理：重生复制前的原版死亡可能已把左轮掉落在世界中。
            purgeAfterLoad = false;
            DominatorForcedShotGuns.confiscateAll(owner);
        }
        if (!active) {
            return;
        }
        spawnOrbitParticles();
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        if (remainingTicks <= 0) {
            settle();
        }
    }

    /**
     * Ends the sequence and puts the stash back (expiry, disconnect, mid-round role change). If the player has
     * meanwhile died for good without passing through Wathe's death drops here, the stash is disposed of as Wathe's
     * death would have: dropped when {@code shouldDropOnDeath}, else left in the inventory.
     * 结束序列并放回原物品（到期、断线、回合中途换职业）。若玩家期间已确定死亡且未经过此处的 Wathe 死亡掉落，则按 Wathe 死亡的
     * 方式处理原物品：{@code shouldDropOnDeath} 时掉落，否则留在物品栏中。
     */
    void settle() {
        Stash stash = deactivate();
        if (stash == null || !(player instanceof ServerPlayerEntity owner)) {
            return;
        }
        DominatorForcedShotGuns.confiscateAll(owner);
        if (!stash.stack().isEmpty() && hasLeftPlayByDeath(owner)
                && GameFunctions.shouldDropOnDeath(stash.stack(), owner)) {
            owner.dropItem(stash.stack(), true, false);
        } else {
            putBack(owner, stash, true);
        }
        sync(owner);
    }

    /**
     * Committed death, right before Wathe's death-drop loop: the marked gun is taken and the stash returns to the
     * inventory, so Wathe's own loop (and every {@code ShouldDropOnDeath} listener) drops or keeps it.
     * 死亡已确定、Wathe 死亡掉落循环之前：收走标记左轮并把原物品放回物品栏，由 Wathe 自身的循环（及所有
     * {@code ShouldDropOnDeath} 监听器）决定掉落或保留。
     */
    void settleBeforeDeathDrops() {
        Stash stash = deactivate();
        if (stash == null || !(player instanceof ServerPlayerEntity owner)) {
            return;
        }
        DominatorForcedShotGuns.confiscateAll(owner);
        putBack(owner, stash, false);
        sync(owner);
    }

    /** Reset, finalize or a pre-round role deal: the stash belongs to a cleared inventory. / 原物品所属物品栏已清空。 */
    void discard() {
        Stash stash = deactivate();
        if (stash != null && player instanceof ServerPlayerEntity owner) {
            DominatorForcedShotGuns.confiscateAll(owner);
            sync(owner);
        }
    }

    private record Stash(ItemStack stack, int slot) {
    }

    /** The stash, or null when no sequence was running. / 返回原物品；无序列时返回 null。 */
    private @Nullable Stash deactivate() {
        if (!active) {
            return null;
        }
        Stash stash = new Stash(originalStack, slot);
        active = false;
        remainingTicks = 0;
        originalStack = ItemStack.EMPTY;
        slot = -1;
        return stash;
    }

    private static void putBack(ServerPlayerEntity owner, Stash stash, boolean alive) {
        ItemStack stack = stash.stack();
        if (stack.isEmpty()) {
            return;
        }
        PlayerInventory inventory = owner.getInventory();
        int target = stash.slot();
        if (target >= 0 && target < inventory.size() && inventory.getStack(target).isEmpty()) {
            inventory.setStack(target, stack);
            return;
        }
        if (inventory.insertStack(stack) || stack.isEmpty()) {
            return;
        }
        // No room left: a living player drops the rest at their feet; a dying one drops it only where Wathe would.
        // 没有空位：存活玩家把剩余部分丢在脚下；濒死玩家只在 Wathe 会掉落时掉落。
        if (alive || GameFunctions.shouldDropOnDeath(stack, owner)) {
            owner.dropItem(stack, !alive, false);
        }
    }

    private static boolean hasLeftPlayByDeath(ServerPlayerEntity owner) {
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getWorld());
        return game.isRunning()
                && game.hasAnyRole(owner.getUuid())
                && game.isPlayerDead(owner.getUuid())
                && !WitchFactorTraitsBridge.isDeathIntercepted(owner);
    }

    private static void sync(ServerPlayerEntity owner) {
        owner.getInventory().markDirty();
        owner.currentScreenHandler.syncState();
    }

    /**
     * Ominous trial-spawner particles orbiting the controlled player during the sequence, staying within half a
     * block of the player's body centre.
     * 序列期间环绕被控制玩家的凶兆试炼刷怪粒子，始终位于其身体中心半米以内。
     */
    private void spawnOrbitParticles() {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        double centerY = player.getBodyY(0.5D);
        double baseAngle = player.age * 0.35D;
        for (int index = 0; index < ORBIT_PARTICLES_PER_TICK; index++) {
            double angle = baseAngle + index * (Math.PI * 2.0D / ORBIT_PARTICLES_PER_TICK);
            double y = centerY
                    + Math.sin(baseAngle * 2.0D + index) * ORBIT_VERTICAL_SPREAD;
            world.spawnParticles(
                    ParticleTypes.TRIAL_SPAWNER_DETECTION_OMINOUS,
                    player.getX() + Math.cos(angle) * ORBIT_RADIUS,
                    y,
                    player.getZ() + Math.sin(angle) * ORBIT_RADIUS,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (active || purgeAfterLoad) {
            tag.putBoolean(ACTIVE, true);
        }
    }

    /**
     * Migration: a saved sequence (disconnect saved before the release ran, server stop, respawn copy) is never
     * resumed; its stash is dropped and the marked gun left in the saved inventory is purged on the next tick. Older
     * saves that also carried {@code OriginalStack} are read the same way.
     * 迁移：存档中的序列（释放执行前就已保存的断线、服务端停止、重生复制）绝不恢复；丢弃其原物品，并在下一刻清除存档物品栏中
     * 遗留的标记左轮。同时带有 {@code OriginalStack} 的旧存档按同样方式读取。
     */
    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        purgeAfterLoad = tag.getBoolean(ACTIVE);
        active = false;
        remainingTicks = 0;
        slot = -1;
        originalStack = ItemStack.EMPTY;
    }
}
