package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gated tickMovement HEAD sprint clear while the Seeker lock is active (common, remap = true: vanilla target). Runs on
 * both sides, so neither the owner's client nor the server lets the frozen body sprint, whatever grants stamina (e.g.
 * NoellesRoles vodka). Unlocked players are untouched.
 * 搜寻者锁定期间在 tickMovement HEAD 清除疾跑（通用配置，原版目标，remap = true）。两端都会运行，
 * 因此无论何种耐力加成（如 NoellesRoles 伏特加），拥有者客户端与服务端都不会让冻结的本体疾跑。未锁定的玩家不受影响。
 */
@Mixin(PlayerEntity.class)
public abstract class SeekerSprintLockMixin {
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void sparkwitch$clearSprintWhileLocked(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.isSprinting() && SeekerRemoteSessionService.isLocked(self)) {
            self.setSprinting(false);
        }
    }
}
