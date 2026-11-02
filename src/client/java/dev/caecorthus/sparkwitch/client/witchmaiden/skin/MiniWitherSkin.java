package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import org.jetbrains.annotations.Nullable;

/**
 * A loaded, selected skin. Client-only presentation: it changes how every Mini Wither looks and sounds on this client
 * only (owner 2026-11-02: local only, auto-fit, nothing synced).
 * 已加载并选中的皮肤。纯客户端表现：只改变本客户端上所有小凋灵的外观与声音（所有者 2026-11-02：仅本地、自动缩放、不同步）。
 *
 * @param id     folder name under the skins directory / 皮肤目录下的文件夹名
 * @param body   the Mini Wither model; null keeps the vanilla Wither (a sounds-only skin) / 小凋灵模型；null 时保留原版凋灵
 *               （仅声音的皮肤）
 * @param skull  the skull projectile model; null keeps the vanilla skull / 头颅弹体模型；null 时保留原版头颅
 */
public record MiniWitherSkin(String id, String displayName, @Nullable MiniWitherSkinModel body,
                             @Nullable MiniWitherSkinModel skull, int soundCount) {
}
