package dev.caecorthus.sparkwitch.headset;

import com.mojang.datafixers.util.Pair;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The fifth "headset" slot (E1, design A): a real {@code Slot} that {@code mixin.bear.HeadsetPlayerScreenHandlerMixin}
 * appends to every {@code PlayerScreenHandler} on both sides, so it is handler index {@link HeadsetSlots#HANDLER_SLOT_ID}
 * (46) and ordinary slot clicks, the screen-handler sync and the creative action all reach it. Backed by
 * {@link HeadsetInventory}; only what {@link HeadsetSlots#accepts} allows (the CENS earplugs or the Blind ComTac VIII),
 * one at a time. Its vanilla position (77,44) is the free cell above the offhand in the survival inventory; the
 * creative tab and Wathe's limited inventory reposition it on the client. Client presentation gate: on the client it is
 * enabled (drawn, hit-tested) only on a confirmed SparkWitch server, whose handler has the same slot; the server side
 * is always enabled.
 * 第五个“耳机”栏位（E1，方案 A）：由 {@code mixin.bear.HeadsetPlayerScreenHandlerMixin} 在两端追加到每个
 * {@code PlayerScreenHandler} 的真实 {@code Slot}，因此其界面下标为 {@link HeadsetSlots#HANDLER_SLOT_ID}（46），普通栏位点击、
 * 界面同步与创造模式操作都能到达它。由 {@link HeadsetInventory} 支撑；只接受 {@link HeadsetSlots#accepts} 允许的物品（CENS
 * 耳塞或盲人的 ComTac VIII），每次一件。原版坐标 (77,44) 是生存背包副手上方的空格；创造模式物品栏页与 Wathe 局内背包在客户端
 * 重新定位它。客户端展示开关：客户端仅在已确认的 SparkWitch 服务器上（其界面有同一栏位）启用（绘制、命中检测）；服务端始终启用。
 */
public final class HeadsetSlot extends Slot {
    /** Vanilla survival inventory position (item corner). / 原版生存背包中的位置（物品左上角）。 */
    public static final int X = 77;
    public static final int Y = 44;
    /** Block-atlas sprite of the empty slot (art package). / 空栏位的方块图集精灵（美术包）。 */
    public static final Identifier EMPTY_SPRITE = SparkWitch.id("item/empty_slot_headset");

    private final PlayerEntity owner;

    public HeadsetSlot(PlayerEntity owner) {
        super(new HeadsetInventory(owner), 0, X, Y);
        this.owner = owner;
    }

    public PlayerEntity owner() {
        return owner;
    }

    /**
     * Whether {@code slot} is {@code player}'s own headset slot (their own {@code playerScreenHandler}).
     * {@code slot} 是否为 {@code player} 自己的耳机栏位（其自身 {@code playerScreenHandler} 中）。
     */
    public static boolean isHeadsetSlotOf(@Nullable Slot slot, @Nullable PlayerEntity player) {
        return player != null && slot instanceof HeadsetSlot headset && headset.owner == player;
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        return HeadsetSlots.accepts(stack);
    }

    @Override
    public int getMaxItemCount() {
        return 1;
    }

    @Override
    public int getMaxItemCount(ItemStack stack) {
        return 1;
    }

    @Override
    public @Nullable Pair<Identifier, Identifier> getBackgroundSprite() {
        return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, EMPTY_SPRITE);
    }

    @Override
    public boolean isEnabled() {
        return !owner.getWorld().isClient() || SparkWitchServerConnection.isConfirmedServer();
    }
}
