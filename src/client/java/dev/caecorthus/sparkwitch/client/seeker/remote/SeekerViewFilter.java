package dev.caecorthus.sparkwitch.client.seeker.remote;

/**
 * Private PostEffectProcessor filter while possessing the car (warm/green) or viewing the camera (cold/grey); never GameRenderer.postProcessor.
 * TODO(WP-10b): implement. / 待 WP-10b 实现。
 * 操控小车（暖/绿）或查看摄像头（冷/灰）时的私有 PostEffectProcessor 滤镜；绝不使用 GameRenderer.postProcessor。
 */
public final class SeekerViewFilter {
    private SeekerViewFilter() {
    }

    public static void register() {
        // TODO / 待实现
    }

    /** Releases the processor on disconnect or resource reload. / 断线或资源重载时释放处理器。 */
    public static void reset() {
    }
}
