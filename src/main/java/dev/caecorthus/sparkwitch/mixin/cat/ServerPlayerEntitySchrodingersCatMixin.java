package dev.caecorthus.sparkwitch.mixin.cat;

import dev.caecorthus.sparkwitch.roles.neutral.cat.SchrodingersCatService;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Closes spectator camera possession for a cat in its spectre, like the FiendFeign guard: vanilla
 * {@code ServerPlayerEntity#attack} in SPECTATOR calls {@code setCameraEntity(target)}, a free look through anyone
 * nearby; the attack is cancelled at HEAD and {@code setCameraEntity} refuses every camera but the cat itself. Every
 * other player is untouched.
 * 与魔人假死守卫相同，为幽魂阶段的猫封住旁观者附身视角：原版旁观模式下 {@code ServerPlayerEntity#attack} 会调用
 * {@code setCameraEntity(target)}，等于借附近任何人的眼睛白看；攻击在 HEAD 处取消，{@code setCameraEntity} 拒绝猫自身以外的
 * 一切视角。其他玩家不受影响。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntitySchrodingersCatMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockCatSpectrePossession(Entity target, CallbackInfo ci) {
        if (SchrodingersCatService.isSpectre((ServerPlayerEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$vetoCatSpectreCamera(Entity entity, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (entity != null && entity != self && SchrodingersCatService.isSpectre(self)) {
            ci.cancel();
        }
    }
}
