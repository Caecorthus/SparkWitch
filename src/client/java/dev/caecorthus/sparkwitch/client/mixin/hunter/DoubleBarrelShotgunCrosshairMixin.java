package dev.caecorthus.sparkwitch.client.mixin.hunter;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.hunter.HunterShotgunCrosshair;
import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hunter shotgun crosshair. A ready slug reuses Wathe's target crosshair while its ray has an unobstructed target (a
 * player, or a Magician puppet as its copied player). A loaded buckshot instead replaces the 3x3 crosshair draw with
 * the Hunter's two 15x15 sprites ({@link HunterShotgunCrosshair}): the marked one when the spread would kill, else the
 * idle one.
 * 猎人猎枪准星。就绪的独头弹在其射线锁定无遮挡目标（玩家，或视同其复制玩家的魔术师皮套）时复用 Wathe 的目标准星。
 * 已装填霰弹时则把 3x3 准星绘制替换成猎人的两枚 15x15 贴图（{@link HunterShotgunCrosshair}）：散射能打死人时用带标记
 * 的准星，否则用空准星。
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
                // The buckshot sprite is chosen by the draw wrap below. / 霰弹贴图由下方绘制包装选择。
                || DoubleBarrelShotgunItem.peekShell(stack) == HunterRules.SHELL_BUCKSHOT
                || player.getItemCooldownManager().isCoolingDown(shotgun)
                || !HunterShotgunCrosshair.hasTarget(player, stack)) {
            return original;
        }
        return Identifier.of("wathe", "hud/crosshair_target");
    }

    /**
     * Replaces the crosshair draw itself (Wathe's 3x3 {@code drawGuiTexture} of CROSSHAIR or CROSSHAIR_TARGET, with its
     * inverse blend already set) by the Hunter's 15x15 sprite while a buckshot is loaded; the knife and bat draws
     * (10x7) and every other state pass through. The sprite goes through the same operation chain, centred on Wathe's
     * crosshair centre and scaled to the auto GUI scale ({@link HunterShotgunCrosshair#scaleFor}).
     * 把准星本身的绘制（Wathe 绘制 CROSSHAIR 或 CROSSHAIR_TARGET 的 3x3 {@code drawGuiTexture}，此时反色混合已设置）在装填
     * 霰弹时换成猎人的 15x15 贴图；小刀与球棒的绘制（10x7）以及其他状态原样放行。贴图仍走同一操作链，居中于 Wathe 准星
     * 中心，并按自动界面尺寸缩放（{@link HunterShotgunCrosshair#scaleFor}）。
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
        if (custom == null) {
            original.call(context, texture, x, y, width, height);
            return;
        }
        // Wathe translated by (-1.5,-1.5), so local (1.5,1.5) is the screen centre.
        // Wathe 已平移 (-1.5,-1.5)，本地 (1.5,1.5) 即屏幕中心。
        MatrixStack matrices = context.getMatrices();
        float scale = HunterShotgunCrosshair.scaleFor(client);
        float half = HunterShotgunCrosshair.SIZE / 2.0F;
        matrices.push();
        matrices.translate(1.5F, 1.5F, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        matrices.translate(-half, -half, 0.0F);
        original.call(context, custom, 0, 0, HunterShotgunCrosshair.SIZE, HunterShotgunCrosshair.SIZE);
        matrices.pop();
    }
}
