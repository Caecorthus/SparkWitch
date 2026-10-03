package dev.caecorthus.sparkwitch.client.saint;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

/**
 * The flashed player's own tinnitus loop: listener-relative, no attenuation, {@code PLAYERS} category (Wathe locks
 * option volumes, so it never uses MUSIC/RECORDS and never writes options). Volume and pitch follow the controller's
 * intensity every sound tick; vanilla re-reads them for ticking sounds.
 * 被闪玩家自己的耳鸣循环：相对听者、无衰减、{@code PLAYERS} 类别（Wathe 锁定了选项音量，因此从不使用 MUSIC/RECORDS，
 * 也从不写入选项）。音量和音调每个声音刻跟随控制器强度；原版会为可 tick 的声音重新读取这两个值。
 */
public final class HolyFlashTinnitusSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {
    private boolean done;

    HolyFlashTinnitusSoundInstance(float intensity) {
        super(SparkWitchSounds.HOLY_FLASH_TINNITUS, SoundCategory.PLAYERS, Random.create());
        this.repeat = true;
        this.repeatDelay = 0;
        this.relative = true;
        this.attenuationType = SoundInstance.AttenuationType.NONE;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
        apply(intensity);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    /** Keeps the source alive while the volume is momentarily 0. / 音量暂时为 0 时也保持声源存活。 */
    @Override
    public boolean shouldAlwaysPlay() {
        return true;
    }

    @Override
    public void tick() {
        if (done) {
            return;
        }
        float intensity = HolyFlashAudioClient.ringIntensity();
        if (!(intensity > 0.0F)) {
            stopLoop();
            return;
        }
        apply(intensity);
    }

    void stopLoop() {
        repeat = false;
        done = true;
    }

    private void apply(float intensity) {
        this.volume = HolyFlashAudioCurve.ringVolume(intensity);
        this.pitch = HolyFlashAudioCurve.ringPitch(intensity);
    }
}
