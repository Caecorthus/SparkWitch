package dev.caecorthus.sparkwitch.client.saint;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashComponent;
import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashRules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only Holy Flash audio controller for the local player's own synced {@code sparkwitch:holy_flash} state.
 * Once per client tick it derives the ring and muffle intensities, starts or stops the tinnitus loop, and re-volumes
 * playing sources so the muffle fades with the flash. Everything is gated to a confirmed SparkWitch server and
 * resets on disconnect. Intensities are volatile because Simple Voice Chat reads the muffle on its audio thread.
 * 仅客户端的圣光弹音频控制器，读取本地玩家自己同步的 {@code sparkwitch:holy_flash} 状态。
 * 每个客户端刻计算耳鸣与压音强度、启动或停止耳鸣循环，并重新设置正在播放声源的音量，使压音随闪光一起减弱。
 * 全部仅在已确认的 SparkWitch 服务器上生效，断线时重置。强度字段为 volatile，因为 Simple Voice Chat
 * 在其音频线程读取压音系数。
 */
public final class HolyFlashAudioClient {
    private static volatile float ringIntensity;
    private static volatile float muffleIntensity;
    @Nullable
    private static HolyFlashTinnitusSoundInstance ring;
    private static int ticksSinceReapply;

    private HolyFlashAudioClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HolyFlashAudioClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(client));
    }

    /** Linear ring intensity this tick; 0 when no flash. / 本刻的线性耳鸣强度；无闪光时为 0。 */
    public static float ringIntensity() {
        return ringIntensity;
    }

    /**
     * Muffle factor for a vanilla sound about to be (re)volumed; 1 for the tinnitus loop itself.
     * 即将（重新）设置音量的原版声音的压音系数；耳鸣循环本身为 1。
     */
    public static float muffleFactor(@Nullable SoundInstance sound) {
        float intensity = muffleIntensity;
        if (!(intensity > 0.0F) || sound == null || isTinnitus(sound)) {
            return 1.0F;
        }
        return HolyFlashAudioCurve.muffleFactor(intensity);
    }

    /** Muffle factor for incoming voice; any thread. / 传入语音的压音系数；可在任意线程调用。 */
    public static float voiceFactor() {
        return HolyFlashAudioCurve.muffleFactor(muffleIntensity);
    }

    static void tick(MinecraftClient client) {
        HolyFlashComponent flash = activeFlash(client);
        float nextRing = 0.0F;
        float nextMuffle = 0.0F;
        if (flash != null) {
            float elapsed = flash.elapsedTicks(0.0F);
            int total = flash.totalTicks();
            nextRing = HolyFlashRules.tinnitus(elapsed, total);
            nextMuffle = HolyFlashAudioCurve.muffleIntensity(elapsed, total);
        }
        boolean wasMuffling = muffleIntensity > 0.0F;
        ringIntensity = nextRing;
        muffleIntensity = nextMuffle;

        SoundManager sounds = client.getSoundManager();
        if (sounds == null) {
            return;
        }
        updateRing(sounds, nextRing > 0.0F);

        boolean muffling = nextMuffle > 0.0F;
        ticksSinceReapply++;
        if (HolyFlashAudioCurve.shouldReapply(wasMuffling, muffling, ticksSinceReapply)) {
            ticksSinceReapply = 0;
            reapply(client, sounds);
        }
    }

    /**
     * Deliberately not gated on the Blind view: a flashed Blind keeps the ring, the muffle and the voice muffle, and
     * only {@code HolyFlashOverlayRenderer} skips for it (owner decision Q12).
     * 刻意不按盲人视图设门槛：被闪的盲人保留耳鸣、压音与语音压低，只有 {@code HolyFlashOverlayRenderer}
     * 为其跳过（所有者决定 Q12）。
     */
    @Nullable
    private static HolyFlashComponent activeFlash(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (!SparkWitchServerConnection.isConfirmedServer() || player == null || client.world == null
                || !HolyFlashRules.isActivePhase(client.world)) {
            return null;
        }
        HolyFlashComponent flash = HolyFlashComponent.KEY.getNullable(player);
        return flash != null && flash.isActive() ? flash : null;
    }

    /**
     * A new, longer flash keeps the same loop: the controller's intensity jumps back up, so restarting the source
     * (and clicking mid-waveform) is never needed.
     * 新的、更长的闪光沿用同一个循环：控制器强度会重新升高，因此无需重启声源（也避免在波形中途切断产生爆音）。
     */
    private static void updateRing(SoundManager sounds, boolean active) {
        if (!active) {
            stopRing(sounds);
            return;
        }
        if (ring == null || ring.isDone()) {
            ring = new HolyFlashTinnitusSoundInstance(ringIntensity);
        }
        if (!sounds.isPlaying(ring)) {
            sounds.play(ring);
        }
    }

    private static void stopRing(@Nullable SoundManager sounds) {
        HolyFlashTinnitusSoundInstance current = ring;
        ring = null;
        if (current != null) {
            current.stopLoop();
            if (sounds != null) {
                sounds.stop(current);
            }
        }
    }

    /**
     * Re-volumes every playing source through {@code getAdjustedVolume(SoundInstance)}, where the muffle mixin
     * applies; for a non-master category the passed volume is ignored, so this reads options and writes none.
     * 通过 {@code getAdjustedVolume(SoundInstance)}（压音 mixin 作用于此）重新设置所有正在播放声源的音量；
     * 非主音量类别会忽略传入的音量，因此只读取选项，不写入任何选项。
     */
    private static void reapply(MinecraftClient client, SoundManager sounds) {
        sounds.updateSoundVolume(SoundCategory.AMBIENT, client.options.getSoundVolume(SoundCategory.AMBIENT));
    }

    private static boolean isTinnitus(SoundInstance sound) {
        return sound == ring || SparkWitchSounds.HOLY_FLASH_TINNITUS_ID.equals(sound.getId());
    }

    static void reset(MinecraftClient client) {
        ringIntensity = 0.0F;
        muffleIntensity = 0.0F;
        ticksSinceReapply = 0;
        stopRing(client == null ? null : client.getSoundManager());
    }
}
