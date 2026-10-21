package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Side-neutral read of the 3 s Homing Feather landing hold. Stable seam: {@code ControlExpertStun.isStunned} answers
 * true while held, so the hold reuses the stun's server interaction guards, its payload deny-list, every role's own
 * stun refusal and the owner's client key lock (mouse look stays free, {@code LOCK_CAMERA} is off). Server: world time
 * before {@link BlackRavenCrowPlayerComponent#getLandingHoldUntilTick()}. Owner's client: held until the server's zero
 * sync, so the lock never ends early; other clients never see the field (owner-only sync) and read false.
 * 3 秒归羽落地定身的两端通用读取。稳定接缝：定身期间 {@code ControlExpertStun.isStunned} 返回 true，因此定身复用眩晕的服务端交互拦截、
 * 数据包拦截名单、各职业自身的眩晕拒绝与主人客户端的按键锁（鼠标视角保持自由，{@code LOCK_CAMERA} 关闭）。服务端：世界时间早于
 * 定身结束刻。主人客户端：直到服务端同步零值才解除，因此锁定不会提前结束；其他客户端收不到该字段（仅同步给本人），读取为 false。
 */
public final class BlackRavenCrowLandingHold {
    private BlackRavenCrowLandingHold() {
    }

    public static boolean isHeld(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.getNullable(player);
        if (component == null) {
            return false;
        }
        if (player.getWorld().isClient()) {
            return component.getLandingHoldUntilTick() > 0L;
        }
        return component.isLandingHeld(player.getWorld().getTime());
    }
}
