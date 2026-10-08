package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Stable contract (owner O2, 2026-10-07): a USEC never picks up or receives a {@code wathe:guns} item (revolver,
 * derringer, Hunter shotgun, serial pistols, Demon Hunter pistol, ...). The AXMC is outside that tag, so the rifle and
 * its parts are never affected. The role is read live and exactly, so a former USEC whose role changed is free again,
 * and the death revolver a dead USEC drops ({@code dropItem}, not an insertion) still lands for others. Server
 * authority only, like Wathe's and SparkTraits' Impostor pickup guards; a creative player is exempt, as in both.
 * Asked by three {@code mixin/usec/} seams: the ground pickup ({@code ItemEntity#onPlayerCollision} HEAD), inventory
 * insertion ({@code PlayerInventory#insertStack} HEAD, so offers and gives fall back to a drop), and container slot
 * clicks ({@code ScreenHandlerUsecItemMixin}), which move stacks without {@code insertStack}.
 * 稳定契约（所有者 O2，2026-10-07）：USEC 永远不能拾取或获得任何 {@code wathe:guns} 物品（左轮、德林杰、猎人霰弹枪、
 * 疯魔双枪、恶魔猎手手枪等）。AXMC 不在该标签内，因此步枪及其零件从不受影响。职业按实时精确判定，所以职业已改变的前 USEC
 * 恢复自由；已死亡 USEC 掉落给他人的死亡左轮（{@code dropItem}，不是放入背包）照常落地。与 Wathe 及 SparkTraits 内鬼的拾取
 * 拦截一样只由服务端裁定；创造模式玩家豁免，两者亦然。由三个 {@code mixin/usec/} 接缝调用：地面拾取
 * （{@code ItemEntity#onPlayerCollision} HEAD）、放入背包（{@code PlayerInventory#insertStack} HEAD，因此给予与 offer
 * 会退回为丢出）以及容器栏位点击（{@code ScreenHandlerUsecItemMixin}，它移动物品时不经过 {@code insertStack}）。
 */
public final class UsecGunRules {
    private UsecGunRules() {
    }

    /** Ground pickup and inventory insertion veto from live state. / 依据实时状态的地面拾取与放入背包否决。 */
    public static boolean blocksGun(@Nullable PlayerEntity player, @Nullable ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || player.getWorld() == null) {
            return false;
        }
        return blocks(!player.getWorld().isClient, player.isCreative(), isGun(stack), () -> isExactUsec(player));
    }

    /**
     * Pure veto: server side, not creative, a gun, and the player's live role is exactly USEC (asked last).
     * 纯否决：服务端、非创造模式、是枪械，且玩家的实时职业恰为 USEC（最后才询问）。
     */
    static boolean blocks(boolean serverSide, boolean creative, boolean gun, BooleanSupplier usec) {
        return serverSide && !creative && gun && usec.getAsBoolean();
    }

    /**
     * Server-side container click veto: a USEC may not take a gun out of a foreign slot. Gathers side-effect-free
     * facts and decides through the pure {@link #blocksSlotClick(SlotActionType, boolean, boolean)}.
     * 服务端容器点击否决：USEC 不能从外部栏位拿走枪械。收集无副作用的事实，再交由纯函数判定。
     */
    public static boolean blocksSlotClick(ScreenHandler handler, @Nullable PlayerEntity player, int slotIndex,
                                          @Nullable SlotActionType actionType) {
        if (handler == null || player == null || actionType == null || slotIndex < 0
                || slotIndex >= handler.slots.size()) {
            return false;
        }
        Slot clicked = handler.slots.get(slotIndex);
        ItemStack stack = clicked.getStack();
        return blocksSlotClick(actionType, clicked.inventory == player.getInventory(), isGun(stack))
                && blocksGun(player, stack);
    }

    /**
     * Pure click decision. Every action that can move a foreign slot's gun to the USEC's cursor or own slots is
     * refused: PICKUP (to the cursor), QUICK_MOVE (into the inventory), SWAP (into the hotbar or offhand, which
     * vanilla writes without {@code insertStack}) and PICKUP_ALL. THROW drops the gun into the world and stays allowed;
     * CLONE is creative-only. Clicks on the player's own slots are never touched (a gun already carried moves freely).
     * 纯点击判定。凡能把外部栏位中的枪械移到 USEC 光标或自身栏位的动作都被拒绝：PICKUP（拿到光标）、QUICK_MOVE（移入背包）、
     * SWAP（换入快捷栏或副手，原版直接写入而不经过 {@code insertStack}）与 PICKUP_ALL。THROW 把枪丢进世界，保持允许；
     * CLONE 仅限创造模式。点击玩家自身栏位从不受影响（已携带的枪可自由移动）。
     *
     * @param ownSlot the clicked slot is in the clicker's own PlayerInventory / 被点击栏位属于点击者自己的背包
     * @param slotGun the clicked slot holds a {@code wathe:guns} stack / 被点击栏位中是枪械
     */
    static boolean blocksSlotClick(SlotActionType action, boolean ownSlot, boolean slotGun) {
        if (ownSlot || !slotGun) {
            return false;
        }
        return action == SlotActionType.PICKUP || action == SlotActionType.QUICK_MOVE
                || action == SlotActionType.SWAP || action == SlotActionType.PICKUP_ALL;
    }

    /** {@code wathe:guns} membership; the AXMC is outside the tag. / {@code wathe:guns} 判定；AXMC 不在该标签内。 */
    static boolean isGun(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isIn(WatheItemTags.GUNS);
    }

    private static boolean isExactUsec(PlayerEntity player) {
        return UsecRules.isUsec(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
