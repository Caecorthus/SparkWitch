package dev.caecorthus.sparkwitch.client.mixin.stowaway;

import dev.caecorthus.sparkwitch.client.stowaway.StowawayClientVisibility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Final outline veto for an unseen Stowaway, mirroring {@code WraithMinecraftClientMixin}: it runs at RETURN and
 * cancels, so any outline source — Wathe instinct, a role highlight or a third-party glow/X-ray — is forced off while
 * the local viewer has no line of sight. The role lookup runs first, so only Stowaway targets pay for the raycast.
 * 对没有视线的偷渡客的最终描边否决，写法与 {@code WraithMinecraftClientMixin} 相同：在 RETURN 取消，因此无论是 Wathe
 * 本能、职业高亮还是第三方发光/透视，只要本地观察者没有视线就强制关闭。先判断职业，只有偷渡客目标才付出射线检测成本。
 */
@Mixin(value = MinecraftClient.class, priority = 100)
public abstract class StowawayMinecraftClientMixin {
    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$vetoUnseenStowawayOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (StowawayClientVisibility.shouldHideFromLocalViewer(entity)) {
            cir.setReturnValue(false);
        }
    }
}
