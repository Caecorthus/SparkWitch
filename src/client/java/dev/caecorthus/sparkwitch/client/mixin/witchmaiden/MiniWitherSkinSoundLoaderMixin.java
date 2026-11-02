package dev.caecorthus.sparkwitch.client.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.sound.MiniWitherSoundOverrides;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Serves the private {@code sparkwitch_skin:} sound files of {@code MiniWitherSkinSoundManagerMixin} from the skin
 * folder instead of resource packs. The future goes into vanilla's own {@code loadedSounds} cache, keyed by the
 * generation-stamped id, so {@code SoundLoader#close} (sound engine stop, after every source stopped) is the only
 * place their OpenAL buffers are deleted; a skin change never frees a buffer a playing source may use. Other ids,
 * preloads and streams take vanilla's path. Client thread, like vanilla's own cache access.
 * 从皮肤文件夹（而非资源包）提供 {@code MiniWitherSkinSoundManagerMixin} 的私有 {@code sparkwitch_skin:} 声音文件。
 * future 放入原版自己的 {@code loadedSounds} 缓存，以带代号的 id 为键，因此只有 {@code SoundLoader#close}（声音引擎
 * 停止、所有声源已停止之后）会删除它们的 OpenAL 缓冲；切换皮肤绝不释放正在播放的声源可能使用的缓冲。其他 id、预加载
 * 与流式声音走原版路径。与原版缓存访问一样在客户端线程。
 */
@Mixin(SoundLoader.class)
public abstract class MiniWitherSkinSoundLoaderMixin {
    @Shadow
    @Final
    private Map<Identifier, CompletableFuture<StaticSound>> loadedSounds;

    @Inject(
            method = "loadStatic(Lnet/minecraft/util/Identifier;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$loadMiniWitherSkinSound(Identifier id,
                                                    CallbackInfoReturnable<CompletableFuture<StaticSound>> cir) {
        if (MiniWitherSoundOverrides.serves(id)) {
            cir.setReturnValue(loadedSounds.computeIfAbsent(id, MiniWitherSoundOverrides::loadAsync));
        }
    }
}
