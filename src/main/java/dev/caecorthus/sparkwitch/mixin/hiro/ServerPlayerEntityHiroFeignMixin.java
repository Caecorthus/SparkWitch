package dev.caecorthus.sparkwitch.mixin.hiro;

import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroVictimFeignService;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Closes spectator camera possession for a fake-dead victim of 二阶堂希罗, like the Fiend's and the Charon's guards.
 * Vanilla {@code ServerPlayerEntity#attack} in SPECTATOR calls {@code setCameraEntity(target)} and {@code tick} then
 * moves the player onto that entity, a free look through whoever stands over the corpse; the attack is cancelled at
 * HEAD, and {@code setCameraEntity} refuses every camera except the victim's own fake corpse and itself
 * ({@link HiroVictimFeignService#vetoesCamera}). Every other player is untouched.
 * 像魔人与卡戎的守卫一样，为二阶堂希罗的假死受害者封住旁观者附身视角。原版旁观模式下 {@code ServerPlayerEntity#attack} 会调用
 * {@code setCameraEntity(target)}，随后 {@code tick} 把玩家移到该实体处，等于借站在尸体旁的人的眼睛白看；攻击在 HEAD 处取消，
 * {@code setCameraEntity} 拒绝受害者自己的假尸体与自身以外的一切视角（{@link HiroVictimFeignService#vetoesCamera}）。其他玩家
 * 不受影响。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityHiroFeignMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockFeignedVictimPossession(Entity target, CallbackInfo ci) {
        if (HiroVictimFeignService.isFeigned((ServerPlayerEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$vetoFeignedVictimCamera(Entity entity, CallbackInfo ci) {
        if (HiroVictimFeignService.vetoesCamera((ServerPlayerEntity) (Object) this, entity)) {
            ci.cancel();
        }
    }
}
