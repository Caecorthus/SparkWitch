package dev.caecorthus.sparkwitch.mixin.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendSwallowPolicy;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * External seam (pinned NoellesRoles 1.7.6, member {@code remap = false}): {@code swallowPlayer} is the only code that
 * marks a player swallowed (called only by the swallow packet receiver). A dormant Fiend target is refused at HEAD, as
 * an invalid target: no Iron Man, whiskey or bodyguard protection is spent and no Taotie cooldown starts. Every other
 * target returns at once and NoellesRoles runs unchanged.
 * 外部接缝（锁定 NoellesRoles 1.7.6，成员 {@code remap = false}）：{@code swallowPlayer} 是唯一把玩家标记为被吞的代码
 * （仅由吞噬数据包接收器调用）。休眠魔人目标在 HEAD 被视为无效目标拒绝：不消耗铁人、威士忌或保镖保护，也不开始饕餮冷却。
 * 其他目标立即返回，NoellesRoles 原样执行。
 */
@Mixin(value = TaotiePlayerComponent.class, remap = false)
public abstract class TaotiePlayerComponentFiendSwallowMixin {
    @Inject(method = "swallowPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;)Z", at = @At("HEAD"),
            cancellable = true)
    private void sparkwitch$refuseDormantFiend(ServerPlayerEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (FiendSwallowPolicy.blocksSwallow(target)) {
            cir.setReturnValue(false);
        }
    }
}
