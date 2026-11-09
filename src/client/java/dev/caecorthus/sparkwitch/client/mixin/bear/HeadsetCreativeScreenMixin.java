package dev.caecorthus.sparkwitch.client.mixin.bear;

import dev.caecorthus.sparkwitch.client.headset.HeadsetScreenFrames;
import dev.caecorthus.sparkwitch.headset.HeadsetSlot;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Creative inventory tab: vanilla wraps every player-handler slot in a {@code CreativeSlot} and places the ones it does
 * not know by index, which would put the headset slot (handler 46) on hotbar cell 37. The wrapper built for the
 * headset slot is moved to {@link HeadsetScreenFrames#CREATIVE_X}/{@code CREATIVE_Y} (the free mirror of the offhand
 * cell), and a slot frame is drawn under it on the inventory tab. Clicks still go through vanilla's creative path
 * (the {@code CreativeSlot}'s wrapped slot id 46), which the server seam {@code HeadsetCreativeActionMixin}
 * accepts. Presentation only.
 * 创造模式物品栏页：原版把每个玩家界面栏位包成 {@code CreativeSlot}，并按下标放置它不认识的栏位，会把耳机栏位（界面 46）放到快捷栏
 * 37 号格上。为耳机栏位创建的包装被移到 {@link HeadsetScreenFrames#CREATIVE_X}/{@code CREATIVE_Y}（副手格的空镜像位置），并在物品栏页
 * 其下绘制栏位框。点击仍走原版创造模式路径（{@code CreativeSlot} 包装的 46 号栏位），由服务端接缝
 * {@code HeadsetCreativeActionMixin} 接受。仅为展示。
 */
@Mixin(CreativeInventoryScreen.class)
public abstract class HeadsetCreativeScreenMixin
        extends AbstractInventoryScreen<CreativeInventoryScreen.CreativeScreenHandler> {
    protected HeadsetCreativeScreenMixin(CreativeInventoryScreen.CreativeScreenHandler handler,
                                                  PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Shadow
    public abstract boolean isInventoryTabSelected();

    @ModifyArgs(method = "setSelectedTab(Lnet/minecraft/item/ItemGroup;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ingame/CreativeInventoryScreen$CreativeSlot;<init>(Lnet/minecraft/screen/slot/Slot;III)V"))
    private void sparkwitch$placeHeadsetSlot(Args args) {
        if (args.get(0) instanceof HeadsetSlot) {
            args.set(2, HeadsetScreenFrames.CREATIVE_X);
            args.set(3, HeadsetScreenFrames.CREATIVE_Y);
        }
    }

    @Inject(method = "drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V", at = @At("TAIL"))
    private void sparkwitch$drawHeadsetFrame(DrawContext context, float delta, int mouseX, int mouseY,
                                             CallbackInfo ci) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player != null && isInventoryTabSelected() && HeadsetScreenFrames.shown(player.playerScreenHandler)) {
            HeadsetScreenFrames.drawFrame(context, x + HeadsetScreenFrames.CREATIVE_X, y + HeadsetScreenFrames.CREATIVE_Y);
        }
    }
}
