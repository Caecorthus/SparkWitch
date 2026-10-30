package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.knight.CrossSwordItem;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelClientState;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/**
 * Reuses Wathe's own knife crosshair path for the cross sword: when the Knight can stab the duelled player, the
 * knife check in {@link CrosshairRenderer#renderCrosshair} is made to pass, so Wathe draws its knife target reticle
 * (five-dot X) and the knife attack indicator exactly as it does for the vanilla knife.
 * 复用 Wathe 自己的小刀准星路径：骑士能刺中被决斗者时，让 {@link CrosshairRenderer#renderCrosshair}
 * 里的小刀判断通过，Wathe 就会像原版小刀一样画五点 X 目标准星和刀攻击提示。
 */
@Mixin(CrosshairRenderer.class)
public abstract class CrossSwordWatheCrosshairMixin {
    /** The first {@code stack.isOf(WatheItems.KNIFE)} check in renderCrosshair (the knife branch selector). */
    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z", ordinal = 2))
    private static boolean sparkwitch$crossSwordKnifeCrosshair(ItemStack stack, Item item,
                                                                Operation<Boolean> original) {
        if (item == WatheItems.KNIFE && sparkwitch$canStabDuelledPlayer(stack)) {
            return true;
        }
        return original.call(stack, item);
    }

    private static boolean sparkwitch$canStabDuelledPlayer(ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || !stack.isOf(SparkWitchItems.crossSword())) {
            return false;
        }
        if (!player.isUsingItem() || !player.getActiveItem().isOf(SparkWitchItems.crossSword())) {
            return false;
        }
        int used = stack.getMaxUseTime(player) - player.getItemUseTimeLeft();
        if (used < CrossSwordItem.MIN_HOLD_TICKS) {
            return false;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(player.getWorld());
        UUID knightId = duel.active() ? duel.knight() : KnightDuelClientState.knight();
        UUID targetId = duel.active() ? duel.target() : KnightDuelClientState.target();
        if (knightId == null || !knightId.equals(player.getUuid())) {
            return false;
        }
        HitResult hit = ProjectileUtil.getCollision(player,
                entity -> entity instanceof PlayerEntity candidate && GameFunctions.isPlayerAliveAndSurvival(candidate),
                CrossSwordItem.STAB_RANGE);
        return hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof PlayerEntity nearest
                && targetId != null && targetId.equals(nearest.getUuid());
    }
}
