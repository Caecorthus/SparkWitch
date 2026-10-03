package dev.caecorthus.sparkwitch.mixin.riftwalker;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server mixin closing spectator camera possession for Rift Gate occupants (research 03 §3.3, vector 1). Vanilla
 * {@code ServerPlayerEntity#attack} calls {@code setCameraEntity(target)} in SPECTATOR (then {@code tick} drags the body
 * to the camera every tick), so an occupant's attack is cancelled at HEAD (the Fabric {@code AttackEntityCallback}
 * lock fails it too). {@code setCameraEntity} additionally vetoes another PLAYER as an occupant's camera, NR Taotie
 * style; non-player cameras stay allowed because SparkTraits Last Stand and Depression legitimately point the camera at
 * a death body, which the session tick then reads as INTERCEPTED (it never touches the game mode).
 * 为裂隙门内玩家封住旁观者附身视角的服务端 mixin（调研 03 §3.3，第 1 条）。原版旁观模式下 {@code ServerPlayerEntity#attack}
 * 会调用 {@code setCameraEntity(target)}（随后 {@code tick} 每刻把本体拖到镜头处），因此门内玩家的攻击在 HEAD 处取消
 * （Fabric {@code AttackEntityCallback} 锁也会使其失败）。{@code setCameraEntity} 另外按 NR 饕餮的做法，否决把其他玩家设为
 * 门内玩家的镜头；非玩家镜头保持允许，因为 SparkTraits 背水一战与抑郁会合理地把镜头指向死亡尸体，会话逐刻随后将其判为
 * INTERCEPTED（从不改动游戏模式）。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class RiftSessionPlayerMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockRiftCameraPossession(Entity target, CallbackInfo ci) {
        if (RiftSessionService.isInside((ServerPlayerEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$vetoRiftPlayerCamera(Entity entity, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (entity instanceof PlayerEntity other && other != self && RiftSessionService.isInside(self)) {
            ci.cancel();
        }
    }
}
