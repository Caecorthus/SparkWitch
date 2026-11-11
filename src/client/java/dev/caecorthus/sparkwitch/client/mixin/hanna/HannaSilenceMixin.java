package dev.caecorthus.sparkwitch.client.mixin.hanna;

import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaComponent;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A floating player makes no sound.
 * <p>Everything they do (steps, item use, doors) is played through {@code playSoundFromEntity}, so filtering that one
 * entry point covers "任何行动将会静音" without touching the sound engine.
 * 浮游中的玩家不会发出任何声音。
 * <p>他们的所有动作（脚步、使用物品、开门）都走 {@code playSoundFromEntity}，
 * 过滤这一个入口即可覆盖「任何行动将会静音」，不必改动声音引擎。
 */
@Mixin(ClientWorld.class)
public abstract class HannaSilenceMixin {

    @Inject(method = "playSoundFromEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$silenceFloatingPlayer(PlayerEntity except, Entity entity,
                                                  RegistryEntry<SoundEvent> sound, SoundCategory category,
                                                  float volume, float pitch, long seed, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player && HannaComponent.KEY.get(player).getFloatSeconds() > 0) {
            ci.cancel();
        }
    }
}
