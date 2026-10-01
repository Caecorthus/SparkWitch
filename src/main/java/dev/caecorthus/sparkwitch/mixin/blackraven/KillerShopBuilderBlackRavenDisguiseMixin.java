package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.game.KillerShopBuilder;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Skips the killer shop prefill for a disguised Raven on both sides, so native disguise ids never collide.
 * 在双端为伪装黑羽鸦跳过杀手商店预填，避免与原生伪装条目 id 冲突。
 */
@Mixin(value = KillerShopBuilder.class, remap = false)
public abstract class KillerShopBuilderBlackRavenDisguiseMixin {
    @WrapMethod(method = "buildShop")
    private static void sparkwitch$skipKillerShopWhileDisguised(
            PlayerEntity player,
            BuildShopEntries.ShopContext context,
            Operation<Void> original
    ) {
        if (BlackRavenActingRole.isDisguised(player)) {
            return;
        }
        original.call(player, context);
    }
}
