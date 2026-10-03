package dev.caecorthus.sparkwitch.client.mixin.blind;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindAttuneDucking;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * C8 Attune ducking on the Blind's own client, at the two vanilla volume seams SparkAssist also uses: the start volume
 * computed inside {@code play(SoundInstance)} and the per-sound recompute {@code getAdjustedVolume(SoundInstance)}
 * (ticking sounds every tick, every playing source on {@code updateSoundVolume}). Both multiply the inner
 * {@code getAdjustedVolume(F, SoundCategory)} result: in {@code play} they chain with SparkAssist's expression modifier,
 * and in the recompute they run before SparkAssist's cancelling RETURN callback, so the factors compose in any mixin
 * order and each sound is scaled once. Options are never written. Factor 1 unless Attune is active.
 * 盲人自己客户端上的 C8 凝神降音，位于 SparkAssist 同样使用的两个原版音量接缝：{@code play(SoundInstance)} 中计算的
 * 起始音量，以及逐声音重算的 {@code getAdjustedVolume(SoundInstance)}（可 tick 的声音每刻调用，{@code updateSoundVolume}
 * 时对所有正在播放的声源调用）。两处都乘在内部 {@code getAdjustedVolume(F, SoundCategory)} 的结果上：在 {@code play}
 * 中与 SparkAssist 的表达式修改器串联，在重算中先于 SparkAssist 会取消的 RETURN 回调执行，因此无论 mixin 顺序如何
 * 系数都相乘叠加，且每个声音只缩放一次。从不写入选项。凝神未生效时系数为 1。
 */
@Mixin(SoundSystem.class)
public abstract class BlindAttuneSoundSystemMixin {
    @ModifyExpressionValue(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F")
    )
    private float sparkwitch$duckAmbientOnPlay(float volume, @Local(argsOnly = true) SoundInstance sound) {
        return volume * BlindAttuneDucking.factor(sound);
    }

    @ModifyExpressionValue(
            method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F")
    )
    private float sparkwitch$duckAmbientOnUpdate(float volume, @Local(argsOnly = true) SoundInstance sound) {
        return volume * BlindAttuneDucking.factor(sound);
    }
}
