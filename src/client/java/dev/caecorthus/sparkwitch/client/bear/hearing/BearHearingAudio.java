package dev.caecorthus.sparkwitch.client.bear.hearing;

import dev.caecorthus.sparkwitch.client.mixin.bear.HearingSourceAccessor;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.Source;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * OpenAL side of the BEAR hearing boost (Q1). OpenAL clamps a source's distance-attenuated gain to its
 * {@code AL_MAX_GAIN}, 1 by default, so the earplugs' factor of 1.5 alone would only help sounds already quieter from
 * distance. For every scaled sound the vanilla setup consumer (first {@code Channel$SourceManager#run} in
 * {@code SoundSystem#play}, sound executor thread) is wrapped to also raise the new source's {@code AL_MAX_GAIN} to
 * {@link BearHearingSoundRules#MAX_GAIN_CEILING}. Only the gain the volume seams computed decides the loudness: a gain
 * at or below 1 is unaffected by the higher cap. OpenAL Soft 1.23.1 (LWJGL 3.3.3, Minecraft 1.21.1) accepts the value
 * and amplifies by exactly the gain (pinned by {@code BearHearingOpenAlTest}). Vanilla generates a fresh AL source per
 * sound and deletes it on release, so the cap never carries over. Any LWJGL/AL failure disables the raise for the
 * session (logged once): the boost then clamps at 1, vanilla's behaviour.
 * BEAR 听力增强（Q1）的 OpenAL 部分。OpenAL 会把声源经距离衰减后的增益钳制到其 {@code AL_MAX_GAIN}（默认 1），因此仅靠耳塞
 * 的 1.5 倍只能让本就因距离变小的声音变响。对每个受缩放的声音，包装原版的声源设置回调（{@code SoundSystem#play} 中第一次
 * {@code Channel$SourceManager#run}，声音执行线程），额外把新声源的 {@code AL_MAX_GAIN} 提高到
 * {@link BearHearingSoundRules#MAX_GAIN_CEILING}。响度只由音量接缝算出的增益决定：小于等于 1 的增益不受提高后的上限影响。
 * OpenAL Soft 1.23.1（LWJGL 3.3.3，Minecraft 1.21.1）接受该值并按增益精确放大（由 {@code BearHearingOpenAlTest} 固定）。原版为
 * 每个声音新建 AL 声源并在释放时删除，因此上限不会延续。任何 LWJGL/AL 失败都会在本次会话中停用提高（只记录一次）：增强随之
 * 钳制在 1，即原版行为。
 */
public final class BearHearingAudio {
    private static final Logger LOGGER = LoggerFactory.getLogger("SparkWitch/BearHearingAudio");
    private static volatile boolean unavailable;

    private BearHearingAudio() {
    }

    /**
     * Vanilla's setup plus the raised cap for a scaled sound; {@code setup} itself for any other sound. Render thread
     * (decides from the sound id and category); the returned consumer runs on the sound executor.
     * 受缩放声音返回原版设置加提高后的上限；其他声音原样返回 {@code setup}。渲染线程调用（按声音 id 与类别判定）；返回的回调
     * 在声音执行线程运行。
     */
    public static Consumer<Source> wrapSetup(@Nullable SoundInstance sound, Consumer<Source> setup) {
        if (sound == null || unavailable || !BearHearingSoundRules.scales(sound.getId(), sound.getCategory())) {
            return setup;
        }
        return source -> {
            setup.accept(source);
            raiseMaxGain(source);
        };
    }

    /** Sound executor thread only. / 仅限声音执行线程。 */
    static void raiseMaxGain(Source source) {
        if (unavailable) {
            return;
        }
        try {
            applyMaxGain(((HearingSourceAccessor) source).sparkwitch$getHearingSourcePointer());
        } catch (RuntimeException | LinkageError error) {
            unavailable = true;
            LOGGER.warn("Could not raise the OpenAL max gain; BEAR earplugs stay capped at normal volume", error);
        }
    }

    /**
     * Sets {@code AL_MAX_GAIN} of the AL source {@code pointer} to the ceiling; needs a current AL context.
     * 把 AL 声源 {@code pointer} 的 {@code AL_MAX_GAIN} 设为上限；需要当前 AL 上下文。
     */
    static void applyMaxGain(int pointer) {
        AL10.alSourcef(pointer, AL10.AL_MAX_GAIN, BearHearingSoundRules.MAX_GAIN_CEILING);
    }
}
