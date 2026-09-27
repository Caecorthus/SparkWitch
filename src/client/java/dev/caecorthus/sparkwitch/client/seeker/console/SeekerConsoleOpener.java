package dev.caecorthus.sparkwitch.client.seeker.console;

import net.minecraft.util.Hand;

/**
 * Client UseItemCallback (default phase): the Seeker's tablet right-click opens SeekerConsoleScreen and returns CONSUME; a one-shot bypass re-issues the vanilla use for the police network.
 * TODO(WP-11): implement. / 待 WP-11 实现。
 * 客户端 UseItemCallback（默认阶段）：搜寻者右键平板打开 SeekerConsoleScreen 并返回 CONSUME；一次性旁路重发原版使用以进入警察网络。
 */
public final class SeekerConsoleOpener {
    private SeekerConsoleOpener() {
    }

    public static void register() {
        // TODO / 待实现
    }

    public static void bypassOnce(Hand hand) {
    }

    public static void reset() {
    }
}
