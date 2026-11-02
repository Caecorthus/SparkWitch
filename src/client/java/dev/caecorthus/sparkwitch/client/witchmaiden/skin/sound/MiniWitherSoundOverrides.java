package dev.caecorthus.sparkwitch.client.witchmaiden.skin.sound;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSoundSlot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The selected skin's replacement sounds, swapped in for {@code sparkwitch:mini_wither.*} on this client only. The
 * server's volume, pitch, position and timing are kept; custom files are downmixed to mono so they stay positional.
 * 选中皮肤的替换声音，只在本客户端替换 {@code sparkwitch:mini_wither.*}。保留服务端给出的音量、音调、位置与时机；
 * 自定义文件会混成单声道，以保持方位感。
 */
public final class MiniWitherSoundOverrides {
    // CONTRACT SKELETON (2026-11-02): the sound agent owns the implementation.

    private MiniWitherSoundOverrides() {
    }

    /**
     * Replaces every override; slots missing from {@code files} play vanilla. {@code skinKey} is a lower-case
     * identifier-safe tag of the skin. Client thread.
     * 替换全部覆盖项；{@code files} 中缺少的槽位播放原版。{@code skinKey} 为皮肤的小写、可用于标识符的标记。客户端线程。
     */
    public static void update(String skinKey, Map<MiniWitherSoundSlot, List<Path>> files) {
        throw new UnsupportedOperationException("TODO");
    }

    public static void clear() {
        throw new UnsupportedOperationException("TODO");
    }
}
