package dev.caecorthus.sparkwitch.client.mixin.hunter;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reuses Wathe's target crosshair only when the loaded shotgun has an unobstructed target (a player, or a Magician
 * puppet as its copied player).
 * 仅当已装填的霰弹枪锁定无遮挡目标（玩家，或视同其复制玩家的魔术师皮套）时复用 Wathe 的目标准星。
 */
@Mixin(CrosshairRenderer.class)
public abstract class DoubleBarrelShotgunCrosshairMixin {
    /** The shotgun pick's player box growth ({@code TARGET_BOX_EXPANSION}). / 猎枪选取玩家时的箱体扩大量。 */
    @Unique
    private static final double SPARKWITCH$PLAYER_BOX_EXPANSION = 0.1D;

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
                || DoubleBarrelShotgunItem.findTarget(player) == null && !sparkwitch$puppetInSight(player)) {
            return original;
        }
        return Identifier.of("wathe", "hud/crosshair_target");
    }

    /**
     * Magician seam: a live puppet on the same 8-block, block-clipped aim ray (its box grown like the player pick)
     * lights the crosshair as its copied player would; the server decides the shot ({@code MagicianPuppetHits}).
     * 魔术师接缝：同一条 8 格、按方块截断的瞄准射线上的存活皮套（箱体与玩家选取同样扩大）与其复制的玩家一样点亮准星；
     * 这一枪由服务端判定（{@code MagicianPuppetHits}）。
     */
    @Unique
    private static boolean sparkwitch$puppetInSight(ClientPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(HunterRules.SHOTGUN_RANGE));
        HitResult block = player.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        Vec3d clipped = block.getType() == HitResult.Type.MISS ? end : block.getPos();
        for (MagicianPlaybackEntity puppet : player.getWorld().getEntitiesByClass(MagicianPlaybackEntity.class,
                new Box(eye, clipped).expand(1.0), candidate -> candidate.isAlive() && !candidate.isRemoved())) {
            Box box = puppet.getBoundingBox().expand(SPARKWITCH$PLAYER_BOX_EXPANSION);
            if (box.contains(eye) || box.raycast(eye, clipped).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
