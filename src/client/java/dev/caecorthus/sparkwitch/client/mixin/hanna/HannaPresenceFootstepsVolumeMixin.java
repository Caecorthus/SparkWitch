package dev.caecorthus.sparkwitch.client.mixin.hanna;

import dev.caecorthus.sparkwitch.client.hanna.HannaFootstepSilence;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional Presence Footsteps seam (mod id {@code presencefootsteps}, shipped by the modpack; SparkTraits'
 * {@code CautiousPresenceFootstepsVolumeMixin} precedent): PF plays other players' movement sounds straight through the
 * client {@code SoundManager}, so the server-side silence of a floating Hanna never reaches them. Every PF sound takes
 * its volume from {@code SoundEngine#getVolumeForSource(LivingEntity)}; answering 0 at HEAD for a floating player
 * ({@link HannaFootstepSilence}) keeps her steps, jumps and landings silent for everyone but herself. {@code @Pseudo},
 * {@code remap = false}, {@code require = 0}: with PF absent or the method renamed nothing is applied and nothing is
 * logged (fail closed, no hard dependency). Client-only presentation; it never touches PF's config.
 * 可选的 Presence Footsteps 接缝（模组 id {@code presencefootsteps}，整合包自带；沿用 SparkTraits 的
 * {@code CautiousPresenceFootstepsVolumeMixin}）：PF 直接经由客户端 {@code SoundManager} 播放其他玩家的移动音效，服务端对浮游中
 * 汉娜的静音影响不到它。PF 的每个声音都从 {@code SoundEngine#getVolumeForSource(LivingEntity)} 取音量；对浮游中的玩家在 HEAD
 * 返回 0（{@link HannaFootstepSilence}），使她的脚步、起跳与落地对除她自己以外的所有人无声。{@code @Pseudo}、
 * {@code remap = false}、{@code require = 0}：没有 PF 或方法改名时什么都不应用、也不产生日志（失败即关闭，无硬依赖）。
 * 纯客户端展示，从不改动 PF 的配置。
 */
@Pseudo
@Mixin(targets = "eu.ha3.presencefootsteps.sound.SoundEngine", remap = false)
public abstract class HannaPresenceFootstepsVolumeMixin {
    @Inject(method = "getVolumeForSource", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void sparkwitch$silenceFloatingHannaFootsteps(LivingEntity source, CallbackInfoReturnable<Float> cir) {
        if (HannaFootstepSilence.mutes(MinecraftClient.getInstance().player, source)) {
            cir.setReturnValue(0.0F);
        }
    }
}
