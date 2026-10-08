package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.Gate;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules.Outcome;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ClickType;
import org.jetbrains.annotations.Nullable;

/**
 * Cursor loading (Q9/D17, after the NoellesRoles Bartender base spirit and the Hunter shotgun): with .338 rounds on the
 * cursor, a right-click on a magazine, or on the rifle, loads exactly one round. {@code Item#onClicked} runs on both
 * sides. Both sides claim the click, so a right-click with rounds never turns into a vanilla swap; only the server
 * changes anything (the shotgun pattern: the client never writes rifle or magazine state, and the server's inventory
 * sync corrects the slot and the cursor). The server re-checks the {@link UsecAttachmentRules#gate player gate} and
 * that the clicked slot is in the player's own inventory. Creative mode is not supported: the creative inventory
 * sets slots client-side, so the server never sees the click, and the attachment screen does not open from it either
 * (it opens only from a player screen handler).
 * 光标装填（Q9/D17，参照 NoellesRoles 酒保基酒与猎人霰弹枪）：光标拿着 .338 子弹时右键弹匣或步枪，恰好装入一发。
 * {@code Item#onClicked} 在两端都会执行。两端都认领这次点击，因此拿着子弹右键永远不会变成原版交换；只有服务端修改状态
 * （霰弹枪模式：客户端从不写入步枪或弹匣状态，由服务端的背包同步纠正栏位与光标）。服务端复核
 * {@link UsecAttachmentRules#gate 玩家准入}，并确认被点击的栏位属于玩家自己的背包。不支持创造模式：创造模式物品栏在客户端
 * 直接设置栏位，服务端看不到这次点击；配件界面也不会从创造模式物品栏打开（只从玩家界面处理器打开）。
 */
public final class UsecAttachmentCursorLoading {
    private UsecAttachmentCursorLoading() {
    }

    /**
     * The round type a click would load: a right-click with USEC rounds on the cursor, else null (the click is left to
     * vanilla). / 这次点击将装入的弹种：光标拿着 USEC 子弹右键时返回弹种，否则为 null（交给原版处理）。
     */
    public static @Nullable UsecAmmoType claimedRound(ItemStack cursor, ClickType clickType) {
        if (clickType != ClickType.RIGHT || cursor == null || cursor.isEmpty()
                || !(cursor.getItem() instanceof UsecAmmoItem round)) {
            return null;
        }
        return round.ammoType();
    }

    /** {@code UsecMagazineItem#onClicked}: push one round onto a loose magazine. / 向散装弹匣压入一发。 */
    public static boolean onMagazineClicked(ItemStack magazine, ItemStack cursor, Slot slot, ClickType clickType,
                                            PlayerEntity player, StackReference cursorReference) {
        UsecAmmoType type = claimedRound(cursor, clickType);
        if (type == null) {
            return false;
        }
        if (player instanceof ServerPlayerEntity server && mayLoad(server, slot, magazine)) {
            UsecMagazineContents next = UsecAttachmentRules.loadLoose(UsecMagazineItem.contents(magazine), type);
            if (next != null) {
                UsecMagazineItem.setContents(magazine, next);
                consumeOne(cursor, cursorReference);
                UsecAttachmentService.finish(server, UsecAttachmentService.Cue.ROUND, false);
            }
        }
        return true;
    }

    /**
     * {@code UsecRifleItem#onClicked}: into the inserted magazine, or a single round into an empty chamber when there
     * is no magazine; the bolt rule applies. / 装入已装弹匣；未装弹匣时单发压入空弹膛；适用拉栓规则。
     */
    public static boolean onRifleClicked(ItemStack rifle, ItemStack cursor, Slot slot, ClickType clickType,
                                         PlayerEntity player, StackReference cursorReference) {
        UsecAmmoType type = claimedRound(cursor, clickType);
        if (type == null) {
            return false;
        }
        if (player instanceof ServerPlayerEntity server && mayLoad(server, slot, rifle)) {
            Outcome outcome = UsecAttachmentRules.cursorLoadRifle(UsecRifleState.read(rifle), type);
            if (outcome != null) {
                UsecRifleState.write(rifle, outcome.rifle());
                consumeOne(cursor, cursorReference);
                UsecAttachmentService.finish(server, UsecAttachmentService.Cue.ROUND, outcome.bolted());
            }
        }
        return true;
    }

    /** Own inventory slot, single target stack, open gate. / 自己的背包栏位、单件目标、准入通过。 */
    private static boolean mayLoad(ServerPlayerEntity player, Slot slot, ItemStack target) {
        return slot != null && slot.inventory == player.getInventory()
                && UsecAttachmentRules.isActionSlot(slot.getIndex()) && target.getCount() == 1
                && UsecAttachmentService.gate(player) == Gate.ALLOWED;
    }

    private static void consumeOne(ItemStack cursor, StackReference cursorReference) {
        cursor.decrement(1);
        cursorReference.set(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
    }
}
