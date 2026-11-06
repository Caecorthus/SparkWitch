package dev.caecorthus.sparkwitch.headset;

import dev.caecorthus.sparkwitch.roles.civilian.bear.headset.CensEarplugsItem;
import dev.caecorthus.sparkwitch.roles.civilian.blind.item.ComTacItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract (BEAR WP0): the single facade over the headset slot ({@link HeadsetComponent}). The slot holds at
 * most one item, and only an item {@link #accepts} allows: the BEAR CENS ProFlex DX5 earplugs or the Blind ComTac VIII
 * (E1; the ComTac moves here in WP4 with its effects unchanged). {@link #HANDLER_SLOT_ID} is the index WP4 appends to
 * {@code PlayerScreenHandler} (after vanilla's 0..45); WP0 adds no Slot or screen. Both sides may read; writes are
 * server-authoritative (a client write is only a prediction until the next sync). Not under {@code roles/civilian/bear}
 * because the Blind uses it too.
 * 冻结契约（BEAR WP0）：耳机槽（{@link HeadsetComponent}）的唯一门面。该槽至多放一件物品，且只接受 {@link #accepts} 允许的
 * 物品：BEAR 的 CENS ProFlex DX5 耳塞或盲人的 ComTac VIII（E1；ComTac 在 WP4 移到这里，效果不变）。{@link #HANDLER_SLOT_ID}
 * 是 WP4 追加到 {@code PlayerScreenHandler} 的下标（位于原版 0..45 之后）；WP0 不添加任何 Slot 或界面。两端都可读取；写入由
 * 服务端裁定（客户端写入在下次同步前只是预测）。不放在 {@code roles/civilian/bear} 下，因为盲人也使用它。
 */
public final class HeadsetSlots {
    /** {@code PlayerScreenHandler} index of the headset slot (WP4). / 耳机槽在 {@code PlayerScreenHandler} 中的下标（WP4）。 */
    public static final int HANDLER_SLOT_ID = 46;

    private HeadsetSlots() {
    }

    /** The worn stack, {@link ItemStack#EMPTY} for none or a null player. / 佩戴的物品堆；没有或玩家为 null 时为 EMPTY。 */
    public static ItemStack get(@Nullable PlayerEntity player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        return HeadsetComponent.KEY.maybeGet(player).map(HeadsetComponent::getStack).orElse(ItemStack.EMPTY);
    }

    /**
     * Replaces the worn stack (null or empty clears it). Does not check {@link #accepts}: callers decide (a clear must
     * always work). / 替换佩戴的物品堆（null 或空即清空）。不检查 {@link #accepts}：由调用方决定（清空必须总能成功）。
     */
    public static void set(@Nullable PlayerEntity player, @Nullable ItemStack stack) {
        if (player != null) {
            HeadsetComponent.KEY.maybeGet(player).ifPresent(component -> component.setStack(stack));
        }
    }

    /** Only the earplugs and the ComTac VIII fit, one at a time. / 只接受耳塞与 ComTac VIII，每次一件。 */
    public static boolean accepts(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && acceptsType(stack.getItem().getClass());
    }

    /** Pure class check, testable without a bootstrapped registry. / 纯类判断，无需引导注册表即可测试。 */
    public static boolean acceptsType(@Nullable Class<?> itemType) {
        return itemType != null
                && (CensEarplugsItem.class.isAssignableFrom(itemType) || ComTacItem.class.isAssignableFrom(itemType));
    }

    /** The headset slot holds {@code item}. / 耳机槽中是 {@code item}。 */
    public static boolean wears(@Nullable PlayerEntity player, @Nullable Item item) {
        return item != null && get(player).isOf(item);
    }
}
