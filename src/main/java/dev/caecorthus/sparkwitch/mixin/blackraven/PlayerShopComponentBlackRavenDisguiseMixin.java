package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseEconomy;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseService;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Server purchase guard: rejects tryBuy within 20 ticks of an identity change (shop_changed), because a
 * client may still index the previous identity's shop. Also exposes the component owner to the wallet mixins.
 * 服务端购买保护：身份变化后 20 刻内拒绝 tryBuy（shop_changed），因为客户端可能仍按上一身份的商店下标购买。
 * 同时向钱包 mixin 暴露组件持有者。
 */
@Mixin(value = PlayerShopComponent.class, remap = false)
public abstract class PlayerShopComponentBlackRavenDisguiseMixin implements BlackRavenDisguiseEconomy.ShopOwnerAccess {
    @Shadow @Final private PlayerEntity player;

    @Shadow
    private void sendPurchaseError(String translationKey) {
        throw new AssertionError();
    }

    @WrapMethod(method = "tryBuy(I)V")
    private void sparkwitch$rejectRightAfterIdentityChange(int index, Operation<Void> original) {
        if (BlackRavenDisguiseService.isWithinPostSwitchGuard(player)) {
            sendPurchaseError(BlackRavenDisguiseRules.messageKey("shop_changed"));
            return;
        }
        original.call(index);
    }

    @Override
    public PlayerEntity sparkwitch$blackRavenShopOwner() {
        return player;
    }
}
