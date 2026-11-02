package dev.caecorthus.sparkwitch.client.mixin.hunter;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.hunter.HunterShotgunCrosshair;
import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hunter shotgun crosshair. Wathe's target crosshair is reused while the loaded shotgun has an unobstructed target
 * (a player, or a Magician puppet as its copied player). A loaded buckshot instead replaces the 3x3 crosshair draw
 * with the Hunter's two 15x15 sprites: no target on the spread shows the idle one, a target the marked one
 * ({@link HunterShotgunCrosshair}); the 4-tick follow-up cooldown keeps it. A slug keeps the Wathe target crosshair.
 * 猎人猎枪准星。已装填的猎枪锁定无遮挡目标（玩家，或视同其复制玩家的魔术师皮套）时复用 Wathe 的目标准星。已装填霰弹时
 * 则把 3x3 准星绘制替换成猎人的两枚 15x15 贴图：散射范围无目标用空准星，有目标用带标记的准星
 * （{@link HunterShotgunCrosshair}）；4 tick 连发冷却期间保留。独头弹继续使用 Wathe 目标准星。
 */
@Mixin(CrosshairRenderer.class)
public abstract class DoubleBarrelShotgunCrosshairMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;CROSSHAIR:Lnet/minecraft/util/Identifier;"
            )
    )
    private static Identifier sparkwitch$showShotgunTargetCrosshair(
            Identifier original,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof DoubleBarrelShotgunItem shotgun)
                || DoubleBarrelShotgunItem.getLoadedShells(stack) <= 0
                || player.getItemCooldownManager().isCoolingDown(shotgun)
                || !HunterShotgunCrosshair.hasTarget(player, stack)) {
            return original;
        }
        return Identifier.of("wathe", "hud/crosshair_target");
    }

    /**
     * Replaces the crosshair draw itself (Wathe's only 3x3 {@code drawGuiTexture} in this method, with its inverse
     * blend already set) by the Hunter's 15x15 sprite while a buckshot is loaded; every other draw passes through.
     * 把准星本身的绘制（本方法中唯一一次 3x3 的 {@code drawGuiTexture}，此时反色混合已设置）在装填霰弹时换成猎人的
     * 15x15 贴图；其余绘制原样放行。
     */
    @WrapOperation(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"
            )
    )
    private static void sparkwitch$drawBuckshotCrosshair(
            DrawContext context,
            Identifier texture,
            int x,
            int y,
            int width,
            int height,
            Operation<Void> original,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext screenContext,
            RenderTickCounter tickCounter
    ) {
        Identifier custom = width == 3 && height == 3 ? HunterShotgunCrosshair.spriteFor(player) : null;
        if (custom != null) {
            int size = HunterShotgunCrosshair.sizeFor(client);
            int offset = HunterShotgunCrosshair.drawOffset(size);
            context.drawGuiTexture(custom, offset, offset, size, size);
            return;
        }
        original.call(context, texture, x, y, width, height);
    }
}
