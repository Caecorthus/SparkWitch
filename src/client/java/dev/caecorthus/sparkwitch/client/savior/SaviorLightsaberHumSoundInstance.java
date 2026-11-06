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
 * hands, or the holder is removed/spectating.
 * 绑定持有者的循环嗡鸣。每客户端刻刷新坐标，因此跟随玩家并对其他玩家随距离衰减。内置 OGG 已做首尾交叉淡化无缝循环，
 * 且以非流式播放，由声音引擎无缝循环。该物品堆离开双手、持有者被移除或旁观时立即停止。
 */
public final class SaviorLightsaberHumSoundInstance extends MovingSoundInstance {
    /** Slightly under the ignition transient so the hum sits behind it. / 略低于点火瞬态，让嗡鸣垫在后面。 */
    private static final float HUM_VOLUME = 0.7F;
    private final PlayerEntity holder;
    private final ItemStack stack;

    public SaviorLightsaberHumSoundInstance(PlayerEntity holder, ItemStack stack) {
        super(SparkWitchSounds.SAVIOR_LIGHTSABER_HUM, SoundCategory.PLAYERS, SoundInstance.createRandom());
        this.holder = holder;
        this.stack = stack;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = HUM_VOLUME;
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
    }

    /** Stops the hum; safe to call more than once. / 停止嗡鸣；可重复调用。 */
    public void stop() {
        setDone();
    }

    private boolean isStillHeld() {
        return holder.getMainHandStack() == stack || holder.getOffHandStack() == stack;
    }

    @Override
    public boolean canPlay() {
        return !holder.isSilent();
    }
}
