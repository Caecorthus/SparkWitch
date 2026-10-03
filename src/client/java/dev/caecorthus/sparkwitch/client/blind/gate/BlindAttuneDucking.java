package dev.caecorthus.sparkwitch.client.blind.gate;

import dev.caecorthus.sparkwitch.client.blind.BlindClientStatus;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Attune ambient ducking on the Blind's own client (C8). While the local Blind's Attune window is active, sounds in
 * {@code AMBIENT}, {@code MUSIC}, {@code RECORDS} and {@code WEATHER} plus every {@code wathe:ambient.*} id (the
 * blackout drone plays in {@code PLAYERS}) are multiplied by {@link BlindRules#ATTUNE_AMBIENT_VOLUME}; players, blocks,
 * creatures and voice stay at full volume. The factor composes with other volume mixins and never writes options
 * (Wathe locks them). The flag is refreshed once per client tick; on each flip every playing sound is re-volumed.
 * 盲人自己客户端上的凝神氛围降音（C8）。本地盲人的凝神窗口生效期间，{@code AMBIENT}、{@code MUSIC}、{@code RECORDS}、
 * {@code WEATHER} 类别以及所有 {@code wathe:ambient.*} 声音（停电嗡鸣在 {@code PLAYERS} 类别中播放）乘以
 * {@link BlindRules#ATTUNE_AMBIENT_VOLUME}；玩家、方块、生物与语音保持原音量。该系数与其他音量 mixin 相乘叠加，
 * 从不写入选项（Wathe 锁定了选项）。标记每个客户端刻刷新一次；每次翻转时重新设置所有正在播放的声音音量。
 */
public final class BlindAttuneDucking {
    private static final String WATHE_NAMESPACE = "wathe";
    private static final String AMBIENT_PATH_PREFIX = "ambient.";
    private static boolean ducking;

    private BlindAttuneDucking() {
    }

    /** Volume factor for a sound on this client right now. / 当前该声音在本客户端上的音量系数。 */
    public static float factor(@Nullable SoundInstance sound) {
        if (!ducking || sound == null) {
            return 1.0f;
        }
        return factor(true, sound.getCategory(), sound.getId());
    }

    public static float factor(boolean ducking, @Nullable SoundCategory category, @Nullable Identifier id) {
        return ducking && isDucked(category, id) ? BlindRules.ATTUNE_AMBIENT_VOLUME : 1.0f;
    }

    /** C8's ambient set. / C8 规定的氛围音集合。 */
    public static boolean isDucked(@Nullable SoundCategory category, @Nullable Identifier id) {
        if (category == SoundCategory.AMBIENT || category == SoundCategory.MUSIC
                || category == SoundCategory.RECORDS || category == SoundCategory.WEATHER) {
            return true;
        }
        return id != null && WATHE_NAMESPACE.equals(id.getNamespace()) && id.getPath().startsWith(AMBIENT_PATH_PREFIX);
    }

    public static boolean shouldDuck(boolean viewActive, boolean attuneActive) {
        return viewActive && attuneActive;
    }

    static void tick(MinecraftClient client) {
        boolean next = shouldDuck(BlindView.isActive(client), BlindClientStatus.of(client.player).attuneActive());
        if (next == ducking) {
            return;
        }
        ducking = next;
        reapply(client);
    }

    /**
     * Re-volumes every playing source through {@code getAdjustedVolume(SoundInstance)}; for a non-master category the
     * passed volume is ignored, so this reads options and writes none.
     * 通过 {@code getAdjustedVolume(SoundInstance)} 重新设置所有正在播放的声源音量；非主音量类别会忽略传入的音量，
     * 因此只读取选项，不写入任何选项。
     */
    private static void reapply(MinecraftClient client) {
        if (client.getSoundManager() != null) {
            client.getSoundManager().updateSoundVolume(SoundCategory.AMBIENT,
                    client.options.getSoundVolume(SoundCategory.AMBIENT));
        }
    }
}
