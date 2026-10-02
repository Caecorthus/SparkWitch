package dev.caecorthus.sparkwitch.client.mixin.armor;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout;
import dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorPanel;
import dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorSlots;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Shows the vanilla armor slots 5..8 on Wathe's player inventory for every player (D10). Wathe's screen draws and
 * hit-tests only hotbar slots 36..44 and never sends a shift-click; these seams add the armor block as an inactive
 * widget, let Wathe's own click, drag, release and hover code find the armor slots, turn an eligible shift-click into
 * one vanilla {@code SWAP}, and put a cursor armor piece back into its empty armor slot before a close would offer it to
 * the hidden main inventory. Client presentation only: every move is a vanilla slot click the server validates with
 * the vanilla armor-slot rules; no packet, NBT or server rule changes. The slots are live exactly while the panel added
 * at {@code init} (gated by {@link LimitedInventoryArmorSlots#applies}) is shown.
 * 为所有玩家在 Wathe 玩家背包显示原版护甲槽 5..8（D10）。Wathe 界面只绘制并命中检测快捷栏槽 36..44，且从不发送
 * Shift 点击；这些接缝把护甲块作为不可交互控件加入，让 Wathe 自己的点击、拖动、松开与悬停代码能找到护甲槽，把
 * 符合条件的 Shift 点击转为一次原版 {@code SWAP}，并在关闭界面会把光标上的护甲放进隐藏主背包之前，将其放回自身的
 * 空护甲槽。仅为客户端展示：每次移动都是服务器按原版护甲槽规则校验的原版槽位点击；不改数据包、NBT 或服务器规则。
 * 护甲槽仅在 {@code init} 时加入的面板（由 {@link LimitedInventoryArmorSlots#applies} 控制）显示期间生效。
 */
@Mixin(LimitedHandledScreen.class)
public abstract class LimitedInventoryArmorSlotsMixin extends Screen {
    @Shadow
    @Final
    protected ScreenHandler handler;
    @Shadow
    protected int x;
    @Shadow
    protected int y;
    @Shadow
    @Nullable
    protected Slot focusedSlot;
    @Shadow
    @Final
    protected Set<Slot> cursorDragSlots;
    @Shadow
    protected boolean cursorDragging;
    @Unique
    @Nullable
    private LimitedInventoryArmorPanel sparkwitch$armorPanel;

    protected LimitedInventoryArmorSlotsMixin(Text title) {
        super(title);
    }

    @Shadow
    protected abstract void drawSlot(DrawContext context, Slot slot);

    @Shadow
    protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

    // Keep Yarn: intermediary selectors crash runClient; remapJar emits the intermediary form verifyClientMixinSelectors checks.
    // 保持 Yarn：intermediary 选择器会使 runClient 崩溃；remapJar 生成的 intermediary 形式由 verifyClientMixinSelectors 校验。
    @Inject(method = "init()V", at = @At("TAIL"))
    private void sparkwitch$addArmorPanel(CallbackInfo ci) {
        // init re-runs on every resize after clearChildren(), so the panel and its gate are rebuilt together.
        // 每次缩放都会在 clearChildren() 后重新 init，面板与其开关一起重建。
        sparkwitch$armorPanel = LimitedInventoryArmorSlots.applies(this, handler)
                ? addDrawableChild(new LimitedInventoryArmorPanel(x, y, this::sparkwitch$paintArmorSlots))
                : null;
    }

    /**
     * Wathe resets {@code focusedSlot} each frame and sets it only for hotbar slots; a hovered armor slot takes it
     * here, before {@code LimitedInventoryScreen.render} draws the item tooltip and number-key swaps read it.
     * Wathe 每帧重置 {@code focusedSlot}，且只为快捷栏槽设置；悬停的护甲槽在此接管，早于物品提示与数字键交换读取。
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
    private void sparkwitch$focusArmorSlot(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (focusedSlot == null && sparkwitch$armorPanel != null) {
            focusedSlot = LimitedInventoryArmorSlots.slotAt(handler, x, y, mouseX, mouseY);
        }
    }

    /**
     * Wathe's click, drag, release and double-click paths all resolve the slot here; armor slots answer only where no
     * hotbar slot did.
     * Wathe 的点击、拖动、松开与双击都在此解析槽位；只有没有快捷栏槽命中时才返回护甲槽。
     */
    @ModifyReturnValue(method = "getSlotAt(DD)Lnet/minecraft/screen/slot/Slot;", at = @At("RETURN"))
    private @Nullable Slot sparkwitch$armorSlotAt(@Nullable Slot original, double mouseX, double mouseY) {
        if (original != null || sparkwitch$armorPanel == null) {
            return original;
        }
        return LimitedInventoryArmorSlots.slotAt(handler, x, y, mouseX, mouseY);
    }

    /**
     * With the Touchscreen option, Wathe closes the screen on a tap outside its 176x32 strip; a tap on the armor block
     * is inside the inventory.
     * 开启触屏选项时，Wathe 会在点到 176x32 热栏外时关闭界面；点到护甲块应视为界面内部。
     */
    @ModifyReturnValue(method = "isClickOutsideBounds(DDIII)Z", at = @At("RETURN"))
    private boolean sparkwitch$armorBlockIsInside(boolean outside, double mouseX, double mouseY, int left, int top,
                                                 int button) {
        return outside && (sparkwitch$armorPanel == null
                || LimitedInventoryArmorLayout.armorIndexAt(x, y, mouseX, mouseY) < 0);
    }

    /**
     * Wathe's press always sends PICKUP. A shift + left press that vanilla would quick-move between the hotbar and an
     * armor slot becomes one {@code SWAP} with that hotbar index instead. Taking a worn piece onto an empty cursor
     * needs a free hotbar slot: otherwise closing the screen would return it into the main inventory Wathe hides
     * (number-key swaps still exchange it). Any other press is untouched.
     * Wathe 的按下总是发送 PICKUP。原版会在快捷栏与护甲槽之间快速移动的 Shift + 左键改为一次带快捷栏序号的
     * {@code SWAP}。用空光标取下已穿的护甲需要有空快捷栏格，否则关闭界面时它会被放回 Wathe 隐藏的主背包（数字键
     * 交换仍可调换）。其他按下保持不变。
     */
    @WrapOperation(
            method = "mouseClicked(DDI)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/client/gui/screen/ingame/LimitedHandledScreen;onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V"
            )
    )
    private void sparkwitch$shiftClickArmor(LimitedHandledScreen<?> screen, Slot slot, int slotId, int button,
                                            SlotActionType action, Operation<Void> original) {
        if (action == SlotActionType.PICKUP && slot != null && sparkwitch$armorPanel != null
                && client != null && client.player != null) {
            if (button == 0 && hasShiftDown()) {
                LimitedInventoryArmorLayout.Swap swap = LimitedInventoryArmorSlots.planShiftClick(client.player, handler, slot);
                if (swap != null) {
                    original.call(screen, handler.getSlot(swap.slotId()), swap.slotId(), swap.button(), SlotActionType.SWAP);
                    return;
                }
            }
            if (!LimitedInventoryArmorSlots.mayPickUp(handler, slot)) {
                return;
            }
        }
        original.call(screen, slot, slotId, button, action);
    }

    /**
     * Esc, the inventory key and a Touchscreen tap outside all close through {@code close()}, which sends the close
     * packet first; on that packet the server offers the cursor stack to the first free inventory slot. With a full
     * hotbar that slot is in the hidden main inventory, so a cursor armor piece is clicked back into its own empty
     * armor slot before the close packet leaves.
     * Esc、物品栏键与触屏点到外部都经由 {@code close()} 关闭，它会先发送关闭数据包；服务器收到后把光标物品放进第一个空
     * 背包格。快捷栏已满时那会是隐藏的主背包，因此在关闭数据包发出前先把光标上的护甲点回其自身的空护甲槽。
     */
    @Inject(method = "close()V", at = @At("HEAD"))
    private void sparkwitch$returnCursorArmorOnClose(CallbackInfo ci) {
        sparkwitch$returnCursorArmor();
    }

    /**
     * Wathe's {@code tick} also closes the screen during a fade or once the player is dead; same return as
     * {@link #sparkwitch$returnCursorArmorOnClose} (the server refuses the click from a dead spectator).
     * Wathe 的 {@code tick} 在淡入淡出期间或玩家死亡后也会关闭界面；处理同上（服务器会拒绝已死亡旁观者的点击）。
     */
    @Inject(
            method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;closeHandledScreen()V")
    )
    private void sparkwitch$returnCursorArmorOnForcedClose(CallbackInfo ci) {
        sparkwitch$returnCursorArmor();
    }

    @Unique
    private void sparkwitch$returnCursorArmor() {
        if (sparkwitch$armorPanel == null || client == null || client.player == null) {
            return;
        }
        int slotId = LimitedInventoryArmorSlots.cursorReturnSlotId(client.player, handler);
        if (slotId >= 0) {
            // The click is predicted locally, so a second close path in the same frame sees an empty cursor.
            // 点击会在本地预测执行，因此同一帧内的第二条关闭路径看到的光标为空。
            onMouseClick(handler.getSlot(slotId), slotId, 0, SlotActionType.PICKUP);
        }
    }

    @Unique
    private void sparkwitch$paintArmorSlots(DrawContext context, int mouseX, int mouseY) {
        LimitedInventoryArmorSlots.drawFrame(context, LimitedInventoryArmorLayout.panelX(x), LimitedInventoryArmorLayout.panelY(y));
        ItemStack cursor = handler.getCursorStack();
        int hovered = LimitedInventoryArmorLayout.armorIndexAt(x, y, mouseX, mouseY);
        MatrixStack matrices = context.getMatrices();
        for (int index = 0; index < LimitedInventoryArmorLayout.ARMOR_SLOT_COUNT; index++) {
            Slot slot = LimitedInventoryArmorSlots.armorSlot(handler, index);
            int slotX = LimitedInventoryArmorLayout.slotX(x, index);
            int slotY = LimitedInventoryArmorLayout.slotY(y, index);
            boolean previewed = cursorDragging && !cursor.isEmpty() && cursorDragSlots.contains(slot);
            if (!slot.hasStack() && !previewed && client != null) {
                LimitedInventoryArmorSlots.drawEmptyIcon(client, context, slot, slotX, slotY);
            }
            // Slot x/y are final vanilla coordinates; shift the matrix so Wathe's drawSlot lands in our cell.
            // Slot 的 x/y 是原版的 final 坐标；平移矩阵让 Wathe 的 drawSlot 画在我们的格子里。
            matrices.push();
            matrices.translate(slotX - slot.x, slotY - slot.y, 0.0F);
            drawSlot(context, slot);
            matrices.pop();
            if (index == hovered && slot.canBeHighlighted()) {
                LimitedHandledScreen.drawSlotHighlight(context, slotX, slotY, 0);
            }
        }
    }
}
