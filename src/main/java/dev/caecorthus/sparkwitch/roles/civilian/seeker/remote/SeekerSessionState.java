package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

/**
 * Server-only, never synced or saved session bookkeeping (anchor, last move seq, move budget, air ticks, reject
 * window, attach deadline), attached to the component as an opaque field.
 * TODO(WP-09): define fields. / 待 WP-09 定义字段。
 * 仅服务端、从不同步也不存盘的会话记录（锚点、最近移动序号、移动配额、空中刻数、拒绝窗口、挂接截止），作为不透明字段挂在组件上。
 */
public final class SeekerSessionState {
}
