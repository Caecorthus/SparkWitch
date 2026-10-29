package dev.caecorthus.sparkwitch.client.mixin.hunter;

import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to a vanilla sound {@link Source}'s OpenAL source name, so {@code DoubleBarrelShotAudio} can switch
 * on {@code AL_SOURCE_SPATIALIZE_SOFT} for the double-barrel shot. Only read on the sound executor thread, inside the
 * setup consumer vanilla already runs there; vanilla generates a fresh AL source per sound and deletes it on release,
 * so the property never carries over to another sound.
 * 只读访问原版声源 {@link Source} 的 OpenAL 声源名，供 {@code DoubleBarrelShotAudio} 为双管猎枪枪声开启
 * {@code AL_SOURCE_SPATIALIZE_SOFT}。只在声音执行线程上、原版本就在该线程运行的设置回调内读取；原版为每个声音新建
 * AL 声源并在释放时删除，因此该属性不会延续到其他声音。
 */
@Mixin(Source.class)
public interface DoubleBarrelSourceAccessor {
    @Accessor("pointer")
    int sparkwitch$getDoubleBarrelSourcePointer();
}
