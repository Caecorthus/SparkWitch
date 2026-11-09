package dev.caecorthus.sparkwitch.roles.civilian.bear.helmet;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ClickType;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * The Rys-T visor toggle (H9, the only way to raise or lower it): a right-click with an EMPTY cursor on the helmet
 * while it sits in the player's own head armor slot, in any inventory screen that shows that slot (Wathe's in-round
 * inventory with the SparkWitch armor block, the vanilla survival inventory, the creative inventory tab). It runs from
 * {@code RysTHelmetItem#onClicked}, wherever the click is resolved; every such side claims the click, so the press
 * never turns into a vanilla pick-up. Left-clicks, shift-clicks, other slots and a held cursor stay vanilla. Exactly
 * one side writes each click (the USEC writer rule, {@code UsecAttachmentCursorLoading}):
 * <ul>
 *   <li>Survival, adventure and Wathe-inventory clicks are resolved on both sides; only the server writes, plays the
 *   public visor sound, and its slot and equipment sync carry the new visor to the wearer and to everyone else.</li>
 *   <li>The creative inventory screen resolves its clicks on the client only and syncs whole stacks through creative
 *   packets; there the client writes and plays the sound locally. The probe for that screen is installed by the client
 *   ({@code client.bear.helmet.RysTHelmetClient}).</li>
 * </ul>
 * Rys-T 面罩开合（H9，唯一的开合方式）：头盔位于玩家自己的头部护甲槽时，在任何显示该槽的背包界面（带 SparkWitch 护甲块的
 * Wathe 局内背包、原版生存背包、创造模式背包标签页）中用空光标右键它。由 {@code RysTHelmetItem#onClicked} 在结算点击的一端执行；
 * 每一端都认领这次点击，因此不会变成原版拿起。左键、Shift 点击、其他栏位与光标持物保持原版。每次点击恰好由一端写入（USEC 写入
 * 规则，见 {@code UsecAttachmentCursorLoading}）：生存、冒险与 Wathe 背包中的点击在两端都会结算，只有服务端写入并播放公开面罩音效，
 * 其栏位与装备同步把新面罩状态带给佩戴者与其他所有人；创造模式物品栏只在客户端结算并通过创造模式数据包同步整个物品堆，此时由
 * 客户端写入并在本地播放音效。该界面的探针由客户端安装（{@code client.bear.helmet.RysTHelmetClient}）。
 */
public final class RysTVisorToggle {
    /**
     * {@code PlayerInventory} index of the head armor slot: 36 + {@code EquipmentSlot.HEAD.getEntitySlotId()} (3); the
     * player screen handler shows it as handler slot 5. / 头部护甲槽在 {@code PlayerInventory} 中的序号：36 +
     * {@code EquipmentSlot.HEAD.getEntitySlotId()}（3）；玩家界面处理器中为第 5 号栏位。
     */
    public static final int HEAD_INVENTORY_INDEX = 39;
    /** Visor sound volume; 1.0 keeps vanilla's 16-block range. / 面罩音效音量；1.0 保持原版 16 格范围。 */
    static final float SOUND_VOLUME = 1.0F;

    /** Until the client installs its probe (and always on a dedicated server), no click is a creative-screen click. */
    private static volatile BooleanSupplier creativeScreenProbe = () -> false;

    private RysTVisorToggle() {
    }

    /** Which side writes a claimed toggle. / 已认领的开合由哪一端写入。 */
    enum Writer {
        /** The server (every server-side click). / 服务端（所有服务端点击）。 */
        SERVER,
        /** The client of a creative player in the creative inventory screen. / 创造模式物品栏中创造模式玩家的客户端。 */
        CREATIVE_CLIENT,
        /** Claim only; the server writes (survival and Wathe clicks on the client). / 只认领，由服务端写入。 */
        NONE
    }

    /**
     * Client only, called once at client init: answers whether the click being resolved belongs to the vanilla creative
     * inventory screen. / 仅客户端，在客户端初始化时调用一次：回答当前结算的点击是否来自原版创造模式物品栏。
     */
    public static void installCreativeScreenProbe(@Nullable BooleanSupplier probe) {
        creativeScreenProbe = probe == null ? () -> false : probe;
    }

    /**
     * Pure writer decision: the server always writes its own clicks; the client writes only a creative player's
     * creative-screen click, which the server never sees. / 纯判定：服务端总是写入自己的点击；客户端只写入创造模式玩家在创造
     * 模式物品栏中的点击（服务端看不到这类点击）。
     */
    static Writer writer(boolean clientSide, boolean creative, boolean creativeScreen) {
        if (!clientSide) {
            return Writer.SERVER;
        }
        return creative && creativeScreen ? Writer.CREATIVE_CLIENT : Writer.NONE;
    }

    /**
     * Pure claim rule: a Rys-T, a right-click, an empty cursor, the player's own head armor slot. / 纯认领规则：Rys-T、
     * 右键、空光标、玩家自己的头部护甲槽。
     */
    public static boolean claims(boolean helmet, boolean rightClick, boolean cursorEmpty, boolean ownHeadSlot) {
        return helmet && rightClick && cursorEmpty && ownHeadSlot;
    }

    /**
     * Whether this click on {@code slot} toggles the visor of the stack in it (both sides; no state is read beyond the
     * slot, the cursor and the button). / 这次点击 {@code slot} 是否开合其中物品的面罩（两端通用；只读取栏位、光标与按键）。
     */
    public static boolean claims(@Nullable Slot slot, @Nullable ItemStack cursor, ClickType clickType,
                                 @Nullable PlayerEntity player) {
        return slot != null && claims(slot.getStack().getItem() instanceof RysTHelmetItem,
                clickType == ClickType.RIGHT, cursor == null || cursor.isEmpty(), isOwnHeadSlot(slot, player));
    }

    /** The slot is the player's own head armor slot. / 该栏位是玩家自己的头部护甲槽。 */
    public static boolean isOwnHeadSlot(@Nullable Slot slot, @Nullable PlayerEntity player) {
        return slot != null && player != null && slot.inventory == player.getInventory()
                && slot.getIndex() == HEAD_INVENTORY_INDEX;
    }

    /**
     * {@code RysTHelmetItem#onClicked}: claims the toggle click on both sides and writes it on the writer side only.
     * {@code helmet} is the slot's own stack, so the visor is set in place and the slot sync carries it.
     * {@code RysTHelmetItem#onClicked}：两端都认领开合点击，只在写入端写入。{@code helmet} 是栏位自身的物品堆，因此面罩状态
     * 原地改写，由栏位同步带走。
     */
    static boolean onHelmetClicked(ItemStack helmet, ItemStack cursor, Slot slot, ClickType clickType,
                                   PlayerEntity player) {
        if (!claims(slot, cursor, clickType, player) || slot.getStack() != helmet) {
            return false;
        }
        if (helmet.getCount() == 1 && writes(player)) {
            RysTVisor next = RysTHelmetItem.visor(helmet).toggle();
            helmet.set(SparkWitchDataComponents.RYS_T_VISOR, next);
            playVisorSound(player, next.down());
        }
        return true;
    }

    private static boolean writes(PlayerEntity player) {
        if (!player.getWorld().isClient) {
            return player instanceof ServerPlayerEntity;
        }
        return writer(true, player.isCreative(), creativeScreenProbe.getAsBoolean()) == Writer.CREATIVE_CLIENT;
    }

    /**
     * Server: a public sound at the wearer's head, heard by the wearer and nearby players. Creative client: local
     * only, since the server never sees that click. / 服务端：在佩戴者头部播放公开音效，佩戴者与附近玩家都能听到。创造模式
     * 客户端：只在本地播放，因为服务端看不到这次点击。
     */
    private static void playVisorSound(PlayerEntity player, boolean down) {
        SoundEvent sound = down ? SparkWitchSounds.BEAR_RYS_T_VISOR_DOWN : SparkWitchSounds.BEAR_RYS_T_VISOR_UP;
        if (sound == null) {
            return;
        }
        if (player instanceof ServerPlayerEntity server) {
            server.getWorld().playSound(null, server.getX(), server.getEyeY(), server.getZ(), sound,
                    SoundCategory.PLAYERS, SOUND_VOLUME, 1.0F);
            return;
        }
        player.playSound(sound, SOUND_VOLUME, 1.0F);
    }
}
