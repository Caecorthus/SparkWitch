package dev.caecorthus.sparkwitch.client.mixin.witchmaiden;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.sound.MiniWitherSoundOverrides;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mini Wither skin sounds on this client only. {@code AbstractSoundInstance#getSoundSet} resolves every sound's set
 * through {@code SoundManager#get(Identifier)} before {@code SoundSystem#play} picks a {@code Sound}, so swapping the
 * returned set for the eight {@code sparkwitch:mini_wither.*} events replaces their files while the server-sent
 * position, category, volume, pitch and timing (and every SoundSystem volume mixin) stay as they are. Every other id,
 * and every slot without files, gets vanilla's set unchanged.
 * 仅在本客户端生效的小凋灵皮肤声音。{@code AbstractSoundInstance#getSoundSet} 在 {@code SoundSystem#play} 选取
 * {@code Sound} 之前经 {@code SoundManager#get(Identifier)} 解析每个声音的集合，因此为八个
 * {@code sparkwitch:mini_wither.*} 事件替换返回的集合即可替换其音频，而服务端下发的位置、类别、音量、音调与时机
 * （以及所有 SoundSystem 音量 mixin）保持不变。其他 id 与没有文件的槽位原样得到原版集合。
 */
@Mixin(SoundManager.class)
public abstract class MiniWitherSkinSoundManagerMixin {
    @ModifyReturnValue(
            method = "get(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/sound/WeightedSoundSet;",
            at = @At("RETURN")
    )
    private WeightedSoundSet sparkwitch$miniWitherSkinSounds(WeightedSoundSet original,
                                                             @Local(argsOnly = true) Identifier id) {
        return MiniWitherSoundOverrides.soundSet(id, original);
    }
}
