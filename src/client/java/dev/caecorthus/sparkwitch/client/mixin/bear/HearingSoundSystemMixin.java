package dev.caecorthus.sparkwitch.client.mixin.bear;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.bear.hearing.BearHearingAudio;
import dev.caecorthus.sparkwitch.client.bear.hearing.BearHearingClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * BEAR hearing (H7, E2): the local player's gear factor times any ricochet muffle scales every scaled sound's gain at
 * the same two seams Holy Flash and the Blind's Attune use (the category-volume result inside {@code play} and inside
 * the per-sound recompute), composing multiplicatively with them. Gain only: vanilla already derived the attenuation
 * range from the raw volume, so range never changes. The first {@code Channel$SourceManager#run} in {@code play} (the
 * vanilla source setup, which the double-barrel spatializer also wraps) additionally raises {@code AL_MAX_GAIN} for
 * scaled sounds so a factor above 1 is audible. BEAR's own re-volume push only sets gains and never stops a source
 * (see {@code sparkwitch$bearHearingGainOnlyPush}). Render thread; the wrapped consumer runs on the sound executor.
 * BEAR 听力（H7、E2）：本地玩家的装备倍数乘以跳弹发闷，在圣光弹与盲人凝神所用的同两处接缝（{@code play} 内与逐声音重算内的
 * 类别音量结果）缩放每个受影响声音的增益，与它们相乘组合。只改增益：原版已用原始音量算出衰减范围，因此范围不变。{@code play}
 * 中第一次 {@code Channel$SourceManager#run}（原版声源设置，双管猎枪空间化也包装它）额外为受缩放声音提高
 * {@code AL_MAX_GAIN}，使大于 1 的倍数可被听到。BEAR 自己的重设音量推送只设增益、从不停止声源（见
 * {@code sparkwitch$bearHearingGainOnlyPush}）。渲染线程；包装后的回调在声音执行线程运行。
 */
@Mixin(SoundSystem.class)
public abstract class HearingSoundSystemMixin {
    @ModifyExpressionValue(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F")
    )
    private float sparkwitch$bearHearingOnPlay(float volume, @Local(argsOnly = true) SoundInstance sound) {
        return volume * BearHearingClient.volumeFactor(sound);
    }

    @ModifyExpressionValue(
            method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F")
    )
    private float sparkwitch$bearHearingOnUpdate(float volume, @Local(argsOnly = true) SoundInstance sound) {
        return volume * BearHearingClient.volumeFactor(sound);
    }

    @WrapOperation(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/Channel$SourceManager;run(Ljava/util/function/Consumer;)V",
                    ordinal = 0)
    )
    private void sparkwitch$bearHearingMaxGain(
            Channel.SourceManager sourceManager,
            Consumer<Source> setup,
            Operation<Void> original,
            @Local(argsOnly = true) SoundInstance sound
    ) {
        original.call(sourceManager, BearHearingAudio.wrapSetup(sound, setup));
    }

    /**
     * Vanilla's per-source body of {@code updateSoundVolume} ({@code method_19754}, client thread) hands the sound
     * executor an action that STOPS the source when the recomputed volume is {@code <= 0}. During BEAR's own push
     * ({@link BearHearingClient#isGainOnlyPush()}) that action is replaced by a gain-only one, so a silent looping
     * "always play" sound (skateboard, drone, minecart) is neither stopped nor dropped; every other caller keeps
     * vanilla's action unchanged.
     * 原版 {@code updateSoundVolume} 的逐声源主体（{@code method_19754}，客户端线程）交给声音执行线程的操作会在重算音量
     * {@code <= 0} 时停止声源。在 BEAR 自己的推送期间（{@link BearHearingClient#isGainOnlyPush()}）把该操作换成只设增益，使无声的
     * 循环“始终播放”声音（滑板、无人机、矿车）既不被停止也不被丢弃；其他调用方保持原版操作不变。
     */
    @WrapOperation(
            method = "method_19754(Lnet/minecraft/client/sound/SoundInstance;Lnet/minecraft/client/sound/Channel$SourceManager;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/Channel$SourceManager;run(Ljava/util/function/Consumer;)V")
    )
    private void sparkwitch$bearHearingGainOnlyPush(
            Channel.SourceManager sourceManager,
            Consumer<Source> action,
            Operation<Void> original,
            @Local float volume
    ) {
        original.call(sourceManager, BearHearingClient.isGainOnlyPush() ? BearHearingAudio.gainOnly(volume) : action);
    }
}
