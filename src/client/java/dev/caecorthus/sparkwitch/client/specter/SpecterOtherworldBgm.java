package dev.caecorthus.sparkwitch.client.specter;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/**
 * Otherworld BGM, client presentation only: the server picks one of the three tracks when the window opens and syncs
 * the variant, so every client in the round plays the same song, once per window. The tracks run about 29-30 s, just
 * under the 30 s window, so they play through once instead of looping (a loop would restart the intro for the last
 * fraction of a second). Non-positional, AMBIENT category (Wathe locks MUSIC), a 10-tick fade-in, and a 10-tick
 * fade-out when the window closes or the round stops before the track ends. A client that joins mid-window starts the
 * track from the beginning.
 * 里世界 BGM，仅客户端展示：窗口开启时服务端从三首中选一并同步变体，回合内所有客户端每个窗口播放同一首、只播一次。
 * 曲目约 29-30 秒，略短于 30 秒窗口，因此完整播放一次而不循环（循环会在最后不到一秒重新播放前奏）。非位置音、
 * AMBIENT 类别（Wathe 锁定了 MUSIC），10 刻淡入；若窗口关闭或回合停止时曲目尚未结束，则 10 刻淡出。
 * 窗口中途加入的客户端从头播放。
 */
public final class SpecterOtherworldBgm {
    /** Fade-in/out in ticks, as the shared ambience system used. / 淡入淡出刻数，与原共享氛围系统一致。 */
    private static final int FADE_TICKS = 10;
    private static boolean registered;
    /** The current window already started its track. / 当前窗口已开始播放曲目。 */
    private static boolean playedThisWindow;

    private SpecterOtherworldBgm() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(SpecterOtherworldBgm::tick);
        // Vanilla stops every sound on disconnect; forget the window so the next connection starts fresh.
        // 原版断线时会停止所有声音；同时忘掉窗口状态，让下一次连接重新开始。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> playedThisWindow = false);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> playedThisWindow = false);
    }

    private static void tick(MinecraftClient client) {
        int variant = activeVariant(client.player);
        if (variant < 0) {
            playedThisWindow = false;
            return;
        }
        if (playedThisWindow || variant >= SparkWitchSounds.SPECTER_OTHERWORLD_BGM.length) {
            return;
        }
        playedThisWindow = true;
        client.getSoundManager().play(new Track(SparkWitchSounds.SPECTER_OTHERWORLD_BGM[variant], variant));
    }

    /** The synced track for a running round's open window, or -1. / 进行中回合开启窗口的同步曲目，否则为 -1。 */
    private static int activeVariant(ClientPlayerEntity player) {
        if (player == null || player.getWorld() == null
                || !GameWorldComponent.KEY.get(player.getWorld()).isRunning()) {
            return -1;
        }
        SpecterOtherworldComponent otherworld = SpecterOtherworldComponent.KEY.get(player.getWorld());
        return otherworld.isActive() ? otherworld.getBgmVariant() : -1;
    }

    /** One play-through of a track; fades out early if its window ends first. / 曲目播放一次；窗口先结束时提前淡出。 */
    private static final class Track extends MovingSoundInstance {
        private final int variant;
        private int fadeTicks;

        private Track(SoundEvent sound, int variant) {
            super(sound, SoundCategory.AMBIENT, SoundInstance.createRandom());
            this.variant = variant;
            this.repeat = false;
            this.repeatDelay = 0;
            this.relative = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
            this.x = 0.0D;
            this.y = 0.0D;
            this.z = 0.0D;
            this.volume = 0.0F;
        }

        /** Starts at volume 0 for the fade-in; keep the source instead of skipping it. / 从 0 音量淡入，不跳过声源。 */
        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }

        @Override
        public void tick() {
            if (activeVariant(MinecraftClient.getInstance().player) == variant) {
                fadeTicks = Math.min(FADE_TICKS, fadeTicks + 1);
            } else if (--fadeTicks < 0) {
                setDone();
                return;
            }
            volume = Math.max(0.0F, Math.min(1.0F, fadeTicks / (float) FADE_TICKS));
        }
    }
}
