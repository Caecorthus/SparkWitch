package dev.caecorthus.sparkwitch.roles.civilian.usec;

/**
 * Owned by WP5: the usec_attachment receiver (magazine swap, suppressor, single-round chambering). Called once from {@link UsecFeatureService#register()}.
 * 归 WP5 所有：usec_attachment 接收器（换弹匣、消音器、单发压膛）。由 {@link UsecFeatureService#register()} 调用一次。
 */
public final class UsecAttachmentService {
    private UsecAttachmentService() {
    }

    public static void register() {
    }
}
