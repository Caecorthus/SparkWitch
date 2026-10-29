package dev.caecorthus.sparkwitch.client.mixin.hunter;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.hunter.DoubleBarrelShotAudio;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * Gives the stereo double-barrel shot ({@code HunterRules.SHOT_SOUND_ID}) direction and distance fade. OpenAL Soft
 * never spatializes a multi-channel buffer by default, so the sounds.json attenuation was ignored. The first
 * {@code Channel$SourceManager#run} in {@code play(SoundInstance)} queues vanilla's source setup (pitch, volume, linear
 * clamped attenuation, position, relative) on the sound executor before a later run attaches the buffer and plays; for
 * the shot only, its consumer is wrapped to also enable {@code AL_SOURCE_SPATIALIZE_SOFT}. Every other sound gets
 * vanilla's consumer unchanged (other stereo sounds, such as the Pig Chase voice, stay non-positional on purpose). The
 * call itself always proceeds.
 * 让立体声的双管猎枪枪声（{@code HunterRules.SHOT_SOUND_ID}）具有方向与距离衰减。OpenAL Soft 默认从不对多声道缓冲做
 * 空间化，因此 sounds.json 的衰减距离被忽略。{@code play(SoundInstance)} 中第一次 {@code Channel$SourceManager#run}
 * 会把原版的声源设置（音调、音量、线性截断衰减、位置、相对坐标）排入声音执行线程，早于之后附加缓冲并播放的 run；仅对
 * 枪声包装其回调，额外开启 {@code AL_SOURCE_SPATIALIZE_SOFT}。其他声音原样得到原版回调（皮革追杀语音等其他立体声
 * 刻意保持非定位）。调用本身总会执行。
 */
@Mixin(SoundSystem.class)
public abstract class DoubleBarrelShotSoundSystemMixin {
    @WrapOperation(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/Channel$SourceManager;run(Ljava/util/function/Consumer;)V",
                    ordinal = 0)
    )
    private void sparkwitch$spatializeDoubleBarrelShot(
            Channel.SourceManager sourceManager,
            Consumer<Source> setup,
            Operation<Void> original,
            @Local(argsOnly = true) SoundInstance sound
    ) {
        original.call(sourceManager, DoubleBarrelShotAudio.wrapSetup(sound, setup));
    }
}
