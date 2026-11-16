package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.item.BoundItemRules;
import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * The temporary forced-shot revolver as a bound item (Time Stealer / Potion Gunner pattern): it is identified only by
 * the {@code sparkwitch:forced_shot_gun} stack marker, never fires by hand, never goes into a world target, and every
 * copy is confiscated when a sequence ends. A player's own guns never carry the marker, so they are never touched.
 * 强制开枪临时左轮作为绑定物品（与窃时者、药炮手同一模式）：只按 {@code sparkwitch:forced_shot_gun} 物品堆标记识别，
 * 不能亲手开火、不能放入世界中的容纳目标，序列结束时没收所有副本。玩家自己的枪永远不带此标记，因此绝不会被误删。
 */
public final class DominatorForcedShotGuns {
    private DominatorForcedShotGuns() {
    }

    public static boolean isForcedShotGun(@Nullable ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && Boolean.TRUE.equals(stack.get(SparkWitchDataComponents.FORCED_SHOT_GUN));
    }

    /**
     * Server, HEAD of Wathe's gun receiver: a {@code wathe:gunshoot} not issued by the forced shot itself is refused
     * while the marked gun is in the main hand (it has no cooldown of its own) or while the player's sequence runs
     * (a hand shot then could make Wathe's punishment take the marked gun and drop an unmarked one, or ride on the
     * forced shot's protection). / 服务端，Wathe 枪械接收器 HEAD：非强制开枪本身发出的 {@code wathe:gunshoot}，在主手为
     * 标记左轮（它没有自己的冷却）或该玩家序列进行中时被拒绝（否则亲手开枪可能让 Wathe 的惩罚拿走标记枪并掉出一把无标记的枪，
     * 或蹭到强制开枪的保护）。
     */
    public static boolean refusesHandShot(@Nullable ServerPlayerEntity shooter) {
        return shooter != null
                && !DominatorForcedShotScope.isFiring(shooter)
                && (isForcedShotGun(shooter.getMainHandStack())
                || DominatorForcedShotComponent.isSequenceActive(shooter));
    }

    /**
     * Both sides, {@code UseEntityCallback}: the marked gun is never handed to an item frame, armor stand or allay.
     * 双端，{@code UseEntityCallback}：标记左轮永远不会交给物品展示框、盔甲架或悦灵。
     */
    public static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isForcedShotGun(held) && target != null && BoundItemRules.takesHeldStack(target.getClass());
    }

    /** Both sides, decorated pot ({@code DecoratedPotBlockForcedShotGunMixin}). / 双端，饰纹陶罐。 */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isForcedShotGun(held) && target != null && BoundItemRules.takesHeldStack(target.getBlock().getClass());
    }

    /**
     * Removes every marked gun from online players (inventory, cursor, open screen and crafting grid) and dropped item
     * entities. Other players whose own sequence is still running are skipped, so two overlapping sequences never
     * take each other's gun; {@code owner} is always swept.
     * 从在线玩家（背包、光标、已打开界面与合成格）与掉落物中移除所有标记左轮。跳过自身序列仍在进行的其他玩家，
     * 因此两段重叠的序列不会互相没收；{@code owner} 总会被清理。
     */
    static void confiscateAll(ServerPlayerEntity owner) {
        MinecraftServer server = owner.getServer();
        if (server == null) {
            confiscateFrom(owner);
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == owner || !DominatorForcedShotComponent.isSequenceActive(player)) {
                confiscateFrom(player);
            }
        }
        if (server.getPlayerManager().getPlayer(owner.getUuid()) != owner) {
            // A leaving player is already off the list. / 正在离开的玩家已不在列表中。
            confiscateFrom(owner);
        }
        for (ServerWorld world : server.getWorlds()) {
            for (ItemEntity item : world.getEntitiesByType(EntityType.ITEM, entity -> isForcedShotGun(entity.getStack()))) {
                item.discard();
            }
        }
    }

    /** Removes every marked gun one player holds anywhere. / 移除某位玩家身上任意位置的标记左轮。 */
    static void confiscateFrom(ServerPlayerEntity player) {
        boolean changed = clear(player.getInventory());
        ScreenHandler open = player.currentScreenHandler;
        if (isForcedShotGun(open.getCursorStack())) {
            open.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : open.slots) {
            if (!(slot.inventory instanceof PlayerInventory) && isForcedShotGun(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        changed |= clear(player.playerScreenHandler.getCraftingInput());
        if (changed) {
            player.getInventory().markDirty();
            open.syncState();
        }
    }

    private static boolean clear(Inventory inventory) {
        boolean changed = false;
        for (int index = 0; index < inventory.size(); index++) {
            if (isForcedShotGun(inventory.getStack(index))) {
                inventory.setStack(index, ItemStack.EMPTY);
                changed = true;
            }
        }
        return changed;
    }
}
