package dev.caecorthus.sparkwitch.mixin.dominator;

import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotComponent;
import java.util.OptionalInt;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server authority behind the forced-shot inventory lock: during the 1 s sequence the marked player opens no
 * container screen (chest, barrel, shulker, any block or entity inventory), so the marked gun can never be moved into
 * one; the client lock only hides screens. The sequence start closes one that is already open.
 * 强制开枪物品栏锁的服务端权威：1 秒序列期间被标记者无法打开任何容器界面（箱子、木桶、潜影盒及任何方块或实体物品栏），
 * 因此标记左轮永远无法被移入其中；客户端锁只负责隐藏界面。序列开始时会关闭已打开的容器。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityForcedShotScreenMixin {
    @Inject(method = "openHandledScreen", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$refuseContainerDuringForcedShot(@Nullable NamedScreenHandlerFactory factory,
                                                           CallbackInfoReturnable<OptionalInt> cir) {
        if (factory != null && DominatorForcedShotComponent.isSequenceActive((ServerPlayerEntity) (Object) this)) {
            cir.setReturnValue(OptionalInt.empty());
        }
    }
}
