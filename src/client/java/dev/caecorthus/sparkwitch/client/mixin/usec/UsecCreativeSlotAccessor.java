package dev.caecorthus.sparkwitch.client.mixin.usec;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only accessor to the player screen handler slot that a vanilla 1.21.1 creative inventory-tab slot wraps
 * ({@code CreativeInventoryScreen$CreativeSlot}, a private class, hence the string target). The wrapper's own
 * {@code getIndex()} is its handler position (36 for hotbar 0, 45 for the offhand), so the USEC attachment opener reads
 * the wrapped slot to get the real player inventory index (0-35, 40). Only the creative inventory tab holds these
 * wrappers; the item-list tabs and their hotbar row never do. Client presentation only; nothing is written.
 * 只读访问器：读取原版 1.21.1 创造模式物品栏“背包”标签页栏位所包装的玩家界面处理器栏位
 * （{@code CreativeInventoryScreen$CreativeSlot} 是私有类，因此使用字符串目标）。包装栏位自身的 {@code getIndex()} 是它在
 * 处理器中的位置（快捷栏 0 为 36，副手为 45），因此 USEC 配件界面入口读取被包装的栏位，以得到真正的玩家背包下标
 * （0-35、40）。只有创造模式的背包标签页持有这些包装栏位；物品列表标签页及其快捷栏行从不持有。仅为客户端展示，不写入任何内容。
 */
@Mixin(targets = "net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen$CreativeSlot")
public interface UsecCreativeSlotAccessor {
    @Accessor("slot")
    Slot sparkwitch$getWrappedSlot();
}
