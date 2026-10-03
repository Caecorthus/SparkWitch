package dev.caecorthus.sparkwitch.client.mixin.saint;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.saint.HolyFlashAudioClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Holy Flash muffle on the flashed player's own client, at the two vanilla volume seams SparkAssist also uses: the
 * start volume computed inside {@code play(SoundInstance)} and the per-sound recompute
 * {@code getAdjustedVolume(SoundInstance)} (ticking sounds every tick, every playing source on
 * {@code updateSoundVolume}). Both multiply the inner {@code getAdjustedVolume(F, SoundCategory)} result, so they chain
 * with SparkAssist's expression modifier in {@code play}, run before its cancelling RETURN callback in the recompute,
 * and compose with any other ducking mixin on the same seams. The factor is 1 without a flash and for the tinnitus
 * loop itself, never at or below 0, and options are never written.
 * 被闪玩家自己客户端上的圣光弹压音，位于 SparkAssist 同样使用的两个原版音量接缝：{@code play(SoundInstance)} 中计算的
 * 起始音量，以及逐声音重算的 {@code getAdjustedVolume(SoundInstance)}（可 tick 的声音每刻调用，{@code updateSoundVolume}
 * 时对所有正在播放的声源调用）。两处都乘在内部 {@code getAdjustedVolume(F, SoundCategory)} 的结果上：在 {@code play}
 * 中与 SparkAssist 的表达式修改器串联，在重算中先于其会取消的 RETURN 回调执行，并与同一接缝上的其他降音 mixin
 * 相乘叠加。无闪光时以及耳鸣循环本身系数为 1，系数永远大于 0，从不写入选项。
 */
@Mixin(SoundSystem.class)
public abstract class HolyFlashSoundSystemMixin {
    @ModifyExpressionValue(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F")
    )
    private float sparkwitch$muffleHolyFlashOnPlay(float volume, @Local(argsOnly = true) SoundInstance sound) {
        return volume * HolyFlashAudioClient.muffleFactor(sound);
    }

    @ModifyExpressionValue(
            method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F")
    )
    private float sparkwitch$muffleHolyFlashOnUpdate(float volume, @Local(argsOnly = true) SoundInstance sound) {
        return volume * HolyFlashAudioClient.muffleFactor(sound);
    }
}
