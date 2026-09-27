package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

/**
 * Server-authoritative session lock: the five Fabric player callbacks in phase {@code seeker_session_lock} return FAIL
 * while locked, PASS otherwise.
 * TODO(WP-09): implement. / 待 WP-09 实现。
 * 服务端权威的会话锁：在 {@code seeker_session_lock} 阶段的五个 Fabric 玩家回调，锁定时返回 FAIL，否则 PASS。
 */
public final class SeekerInteractionGuards {
    private SeekerInteractionGuards() {
    }

    public static void register() {
        // TODO(WP-09) / 待 WP-09 实现
    }
}
