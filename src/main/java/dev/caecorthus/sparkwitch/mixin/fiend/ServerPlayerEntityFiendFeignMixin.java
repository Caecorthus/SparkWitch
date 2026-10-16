package dev.caecorthus.sparkwitch.mixin.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFeignService;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Closes spectator camera possession for a feigning Fiend, like the Rift Gate guard. Vanilla
 * {@code ServerPlayerEntity#attack} in SPECTATOR calls {@code setCameraEntity(target)} and {@code tick} then moves the
 * player onto that entity, a free look through whoever stands over the corpse; the attack is cancelled at HEAD, and
 * {@code setCameraEntity} refuses every camera except the Fiend's own fake corpse and itself
 * ({@link FiendFeignService#vetoesCamera}). Every other player is untouched.
 * 像裂隙门守卫一样，为假死的魔人封住旁观者附身视角。原版旁观模式下 {@code ServerPlayerEntity#attack} 会调用
 * {@code setCameraEntity(target)}，随后 {@code tick} 把玩家移到该实体处，等于借站在尸体旁的人的眼睛白看；攻击在 HEAD 处取消，
 * {@code setCameraEntity} 拒绝魔人自己的假尸体与自身以外的一切视角（{@link FiendFeignService#vetoesCamera}）。其他玩家不受影响。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityFiendFeignMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockFeigningFiendPossession(Entity target, CallbackInfo ci) {
        if (FiendFeignService.isFeigning((ServerPlayerEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$vetoFeigningFiendCamera(Entity entity, CallbackInfo ci) {
        if (FiendFeignService.vetoesCamera((ServerPlayerEntity) (Object) this, entity)) {
            ci.cancel();
        }
    }
}
