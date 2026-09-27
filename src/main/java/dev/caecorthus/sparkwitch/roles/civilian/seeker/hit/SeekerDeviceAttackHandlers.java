package dev.caecorthus.sparkwitch.roles.civilian.seeker.hit;

/**
 * AttackEntityCallback in phase {@code seeker_device}, registered on both sides: the client lets the attack packet
 * through for devices; the server breaks through {@code SeekerDeviceHits.onMelee}. PASS for every non-device target.
 * TODO(WP-04): implement. / 待 WP-04 实现。
 * 在 {@code seeker_device} 阶段、两端都注册的 AttackEntityCallback：客户端对设备放行攻击包；服务端经
 * {@code SeekerDeviceHits.onMelee} 损坏设备。对所有非设备目标返回 PASS。
 */
public final class SeekerDeviceAttackHandlers {
    private SeekerDeviceAttackHandlers() {
    }

    public static void register() {
        // TODO(WP-04) / 待 WP-04 实现
    }
}
