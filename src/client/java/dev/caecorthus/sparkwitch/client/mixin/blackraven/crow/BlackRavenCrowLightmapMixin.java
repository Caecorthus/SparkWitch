package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowVision;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client-only permanent night vision through the crow (plan §2.9): while the camera is the crow the local player flies,
 * the lightmap update reads night vision as present at full strength and the darkness effect as absent. No status
 * effect is ever added (Wathe clears everyone's night vision when a blackout ends). Only the lightmap's own reads are
 * wrapped; the player's real effects are untouched. Pass-through otherwise; chains with Wathe's lightmap wrappers.
 * 透过乌鸦的纯客户端永久夜视（计划 §2.9）：镜头为本地玩家操控的乌鸦时，光照贴图更新把夜视视为满强度存在、把黑暗效果视为不存在。从不添加
 * 状态效果（Wathe 熄灯结束时会清除所有人的夜视）。只包装光照贴图自己的读取；玩家真实效果不变。其他情况原样传递，可与 Wathe 的光照包装串联。
 */
@Mixin(LightmapTextureManager.class)
public abstract class BlackRavenCrowLightmapMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"))
    private boolean sparkwitch$crowNightVision(ClientPlayerEntity player, RegistryEntry<StatusEffect> effect,
                                              Operation<Boolean> original) {
        if (effect == StatusEffects.NIGHT_VISION && BlackRavenCrowVision.grantsNightVision()) {
            return true;
        }
        return original.call(player, effect);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F"))
    private float sparkwitch$crowNightVisionStrength(LivingEntity entity, float tickDelta, Operation<Float> original) {
        if (BlackRavenCrowVision.grantsNightVision()) {
            return 1.0F;
        }
        return original.call(entity, tickDelta);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/LightmapTextureManager;getDarknessFactor(F)F"))
    private float sparkwitch$crowNoDarknessFactor(LightmapTextureManager manager, float tickDelta,
                                                 Operation<Float> original) {
        if (BlackRavenCrowVision.grantsNightVision()) {
            return 0.0F;
        }
        return original.call(manager, tickDelta);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/LightmapTextureManager;getDarkness(Lnet/minecraft/entity/LivingEntity;FF)F"))
    private float sparkwitch$crowNoDarkness(LightmapTextureManager manager, LivingEntity entity, float factor,
                                           float tickDelta, Operation<Float> original) {
        if (BlackRavenCrowVision.grantsNightVision()) {
            return 0.0F;
        }
        return original.call(manager, entity, factor, tickDelta);
    }
}
