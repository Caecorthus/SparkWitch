package dev.caecorthus.sparkwitch.client.bear.helmet;

import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisorToggle;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ClickType;
import org.jetbrains.annotations.Nullable;

/**
 * Client seam for Wathe's in-round inventory: {@code LimitedInventoryArmorSlotsMixin} swallows an empty-cursor press on
 * a worn armor piece when no shown slot is free (taking it off would hide it in the main inventory). A right press on
 * the worn Rys-T is the visor toggle ({@link RysTVisorToggle}), which never picks the helmet up, so it is exempt from
 * that gate and goes through as the ordinary PICKUP click the server resolves. Presentation only.
 * Wathe 局内背包的客户端接缝：没有空的显示栏位时，{@code LimitedInventoryArmorSlotsMixin} 会吞掉空光标对已穿护甲的点击（取下会把
 * 它藏进主背包）。右键已戴的 Rys-T 是面罩开合（{@link RysTVisorToggle}），从不拿起头盔，因此不受该门槛限制，按服务端结算的普通
 * PICKUP 点击发出。仅为展示。
 */
public final class RysTVisorClicks {
    /** Mouse button of a right press in {@code HandledScreen}. / {@code HandledScreen} 中右键的鼠标按键编号。 */
    static final int RIGHT_BUTTON = 1;

    private RysTVisorClicks() {
    }

    /**
     * True when this press toggles the visor of a Rys-T in the player's own head slot. / 这次按下会开合玩家自己头部槽中
     * Rys-T 的面罩时为真。
     */
    public static boolean exemptsPickUpGate(@Nullable PlayerEntity player, ScreenHandler handler, @Nullable Slot slot,
                                            int button) {
        return button == RIGHT_BUTTON && RysTVisorToggle.claims(slot, handler.getCursorStack(), ClickType.RIGHT, player);
    }
}
