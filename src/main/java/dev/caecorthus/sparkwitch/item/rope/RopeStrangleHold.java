package dev.caecorthus.sparkwitch.item.rope;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Side-neutral read of the Rope strangle hold, for both the strangler and the strangled. Stable seam (Black Raven
 * landing-hold precedent): {@code ControlExpertStun.isStunned} answers true while held, so the hold reuses the stun's
 * server interaction guards, its payload deny-list (knife, gun, shop, every classified role skill), every role's own
 * stun refusal and the owner's client key lock; mouse look stays free ({@code LOCK_CAMERA} is off), voice chat is
 * untouched, and none of the Taser's Blindness or Slowness is applied. Server: the authoritative session table.
 * Owner's client: the owner-only {@link RopeHoldComponent}, held until the server's zero sync, so the lock never ends
 * early; other clients never see the field and read false.
 * 绳索勒杀定身的两端通用读取，同时适用于勒杀者与被勒者。稳定接缝（黑羽鸦落地定身先例）：定身期间
 * {@code ControlExpertStun.isStunned} 返回 true，因此定身复用眩晕的服务端交互拦截、数据包拦截名单（刀、枪、商店与所有已归类的职业
 * 技能）、各职业自身的眩晕拒绝以及拥有者客户端的按键锁；鼠标视角保持自由（{@code LOCK_CAMERA} 关闭），语音不受影响，也不会施加电击枪
 * 的失明或缓慢。服务端：读取权威会话表。拥有者客户端：读取仅同步给本人的 {@link RopeHoldComponent}，直到服务端同步零值才解除，因此
 * 锁定不会提前结束；其他客户端收不到该字段，读取为 false。
 */
public final class RopeStrangleHold {
    private RopeStrangleHold() {
    }

    public static boolean isHeld(@Nullable PlayerEntity player) {
        return side(player) != RopeHoldComponent.Side.NONE;
    }

    static RopeHoldComponent.Side side(@Nullable PlayerEntity player) {
        if (player == null) {
            return RopeHoldComponent.Side.NONE;
        }
        if (!player.getWorld().isClient()) {
            return RopeStrangleService.serverSide(player);
        }
        RopeHoldComponent component = RopeHoldComponent.KEY.getNullable(player);
        return component == null ? RopeHoldComponent.Side.NONE : component.side();
    }
}
