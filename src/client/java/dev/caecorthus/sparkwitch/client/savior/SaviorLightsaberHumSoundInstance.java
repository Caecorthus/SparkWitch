package dev.caecorthus.sparkwitch.client.savior;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;

/**
 * Looping idle hum attached to the holder. The position is refreshed every client tick, so the sound follows the player
 * and attenuates with distance for other players. The bundled OGG is pre-looped (tail crossfaded into the head) and
 * plays non-streamed, letting the sound engine loop it without a gap. It stops as soon as that exact stack leaves both
 * hands, or the holder is removed/spectating. A silent holder mutes it instead of stopping it (vanilla's moving
 * minecart sound uses the same {@code shouldAlwaysPlay} + volume pair), so the hum resumes the moment the holder is
 * audible again; {@link SaviorLightsaberAnimation} replaces a hum the sound engine itself dropped.
 * 绑定持有者的循环嗡鸣。每客户端刻刷新坐标，因此跟随玩家并对其他玩家随距离衰减。内置 OGG 已做首尾交叉淡化无缝循环，
 * 且以非流式播放，由声音引擎无缝循环。该物品堆离开双手、持有者被移除或旁观时立即停止。持有者静音时只把音量降为 0 而不停止
 * （原版移动矿车音效用的是同样的 {@code shouldAlwaysPlay} 加音量组合），持有者恢复发声时嗡鸣立即恢复；声音引擎自己丢弃的
 * 嗡鸣由 {@link SaviorLightsaberAnimation} 替换。
 */
public final class SaviorLightsaberHumSoundInstance extends MovingSoundInstance {
    /** Slightly under the ignition transient so the hum sits behind it. / 略低于点火瞬态，让嗡鸣垫在后面。 */
    private static final float HUM_VOLUME = 0.7F;
    private final PlayerEntity holder;
    private final ItemStack stack;
    private final long startedAt;

    public SaviorLightsaberHumSoundInstance(PlayerEntity holder, ItemStack stack, long startedAt) {
        super(SparkWitchSounds.SAVIOR_LIGHTSABER_HUM, SoundCategory.PLAYERS, SoundInstance.createRandom());
        this.holder = holder;
        this.stack = stack;
        this.startedAt = startedAt;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = volumeFor(holder);
        this.pitch = 1.0F;
        this.attenuationType = AttenuationType.LINEAR;
        this.x = holder.getX();
        this.y = holder.getY();
        this.z = holder.getZ();
    }

    @Override
    public void tick() {
        if (holder.isRemoved() || holder.isSpectator() || !isStillHeld()) {
            setDone();
            return;
        }
        this.x = holder.getX();
        this.y = holder.getY();
        this.z = holder.getZ();
        this.volume = volumeFor(holder);
    }

    /** Stops the hum; safe to call more than once. / 停止嗡鸣；可重复调用。 */
    public void stop() {
        setDone();
    }

    /** World tick this hum was started, for the retry throttle. / 该嗡鸣开始时的世界刻，用于重试节流。 */
    public long startedAt() {
        return startedAt;
    }

    private boolean isStillHeld() {
        return holder.getMainHandStack() == stack || holder.getOffHandStack() == stack;
    }

    private static float volumeFor(PlayerEntity holder) {
        return holder.isSilent() ? 0.0F : HUM_VOLUME;
    }

    /** Plays (muted) even at zero volume, so a silent holder keeps the loop alive. / 音量为 0 时也播放（静音），让静音的持有者保持循环。 */
    @Override
    public boolean shouldAlwaysPlay() {
        return true;
    }
}
