package dev.caecorthus.sparkwitch.client.mixin.bear;

import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to a vanilla sound {@link Source}'s OpenAL source name, so {@code BearHearingAudio} can raise
 * {@code AL_MAX_GAIN} for a scaled sound. Read only on the sound executor thread, inside the setup consumer vanilla
 * already runs there. Separate from the Hunter's accessor on purpose (no cross-role coupling).
 * 只读访问原版声源 {@link Source} 的 OpenAL 声源名，供 {@code BearHearingAudio} 为受缩放声音提高 {@code AL_MAX_GAIN}。只在声音
 * 执行线程上、原版本就在该线程运行的设置回调内读取。刻意与猎人的访问器分开（不跨职业耦合）。
 */
@Mixin(Source.class)
public interface HearingSourceAccessor {
    @Accessor("pointer")
    int sparkwitch$getHearingSourcePointer();
}
