package dev.caecorthus.sparkwitch.mixin.hanna;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaSilence;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Server authority for the Hanna float's silence: the mute gate on the two methods every public world sound funnels
 * through ({@code PlayerEntity#playSound}, every {@code World#playSound} overload and {@code playSoundFromEntity} end
 * here). When {@link HannaSilence} says the sound is a floating player's own, the vanilla method is not called at all,
 * so no client receives it; every other sound runs vanilla exactly once with its original arguments.
 * <p>Ordering: a {@code @WrapMethod} wraps the whole target method, including every {@code HEAD} injector of every
 * mixin (MixinExtras applies it after all other injectors). The Blind's read-only perception seam
 * ({@code mixin/blind/ServerWorldBlindPerceptionMixin}, HEAD of the same two methods) therefore never runs for a muted
 * sound and stays untouched and non-cancellable; two HEAD injectors would not give that order by construction.
 * 汉娜浮游静音的服务端权威：所有公开世界声音都会经过的两个方法上的静音闸门（{@code PlayerEntity#playSound}、所有
 * {@code World#playSound} 重载与 {@code playSoundFromEntity} 最终都到这里）。当 {@link HannaSilence} 判定声音属于浮游玩家自身时，
 * 原版方法根本不被调用，任何客户端都收不到；其他声音以原参数恰好执行一次原版方法。
 * <p>顺序：{@code @WrapMethod} 包裹整个目标方法，包括所有 mixin 的所有 {@code HEAD} 注入（MixinExtras 在其他注入器之后应用它）。
 * 因此盲人的只读感知接缝（{@code mixin/blind/ServerWorldBlindPerceptionMixin}，同样两个方法的 HEAD）对被静音的声音从不运行，
 * 且保持原样、不可取消；两个 HEAD 注入无法从结构上保证这一顺序。
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldHannaSilenceMixin {
    @WrapMethod(
            method = "playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/registry/entry/RegistryEntry;"
                    + "Lnet/minecraft/sound/SoundCategory;FFJ)V"
    )
    private void sparkwitch$muteFloatingHannaWorldSound(@Nullable PlayerEntity except, double x, double y, double z,
                                                        RegistryEntry<SoundEvent> sound, SoundCategory category,
                                                        float volume, float pitch, long seed,
                                                        Operation<Void> original) {
        if (!HannaSilence.interceptWorldSound((ServerWorld) (Object) this, except, x, y, z, sound, category, volume,
                pitch, seed)) {
            original.call(except, x, y, z, sound, category, volume, pitch, seed);
        }
    }

    @WrapMethod(
            method = "playSoundFromEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;"
                    + "Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V"
    )
    private void sparkwitch$muteFloatingHannaEntitySound(@Nullable PlayerEntity except, Entity entity,
                                                         RegistryEntry<SoundEvent> sound, SoundCategory category,
                                                         float volume, float pitch, long seed,
                                                         Operation<Void> original) {
        if (!HannaSilence.interceptEntitySound((ServerWorld) (Object) this, except, entity, sound, category, volume,
                pitch, seed)) {
            original.call(except, entity, sound, category, volume, pitch, seed);
        }
    }
}
