package dev.caecorthus.sparkwitch.client.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.lambdaurora.lambdynlights.api.DynamicLightHandlers;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Replaces the static LambDynamicLights item definition with a per-player luminance handler: the held lightsaber only
 * emits light once its blade has at least one ignition frame visible, while the held Savior pistol always emits a
 * faint light. External seam (optional LambDynamicLights, compile-only API): the class is named only by the
 * {@code lambdynlights:initializer} and legacy {@code dynamiclights} entrypoints in {@code fabric.mod.json}, which
 * only LambDynamicLights reads, so it is never loaded when LambDynamicLights is absent; the guard keeps a loader that
 * reads both keys from registering the handler twice.
 * 用按玩家计算的光源替代静态 LambDynamicLights 物品定义：手持光剑在刀刃至少伸出一帧后才发光，救世主手枪则
 * 始终发出微弱光。外部接缝（可选 LambDynamicLights，API 仅编译期）：该类只由 {@code fabric.mod.json} 中的
 * {@code lambdynlights:initializer} 与旧版 {@code dynamiclights} 入口指向，只有 LambDynamicLights 会读取它们，
 * 因此未安装 LambDynamicLights 时不会被加载；同时读取两个键的加载器也会被守卫挡住，不会重复注册光源。
 */
public final class SaviorDynamicLightsInitializer implements DynamicLightsInitializer {
    /** Full-bright blade luminance. / 刀刃满亮度。 */
    private static final int IGNITED_LUMINANCE = 15;
    /** The pistol glows faintly at all times while held. / 手枪手持时常亮，亮度较低。 */
    private static final int PISTOL_LUMINANCE = 3;
    private static boolean registered;

    @Override
    @SuppressWarnings("removal")
    public void onInitializeDynamicLights(ItemLightSourceManager manager) {
        if (registered) {
            return;
        }
        registered = true;
        DynamicLightHandlers.registerDynamicLightHandler(EntityType.PLAYER, SaviorDynamicLightsInitializer::luminance);
    }

    private static int luminance(PlayerEntity player) {
        return Math.max(bladeExtended(player) ? IGNITED_LUMINANCE : 0, holdsPistol(player) ? PISTOL_LUMINANCE : 0);
    }

    private static boolean bladeExtended(PlayerEntity player) {
        return SaviorLightsaberAnimation.isBladeExtended(player.getMainHandStack())
                || SaviorLightsaberAnimation.isBladeExtended(player.getOffHandStack());
    }

    private static boolean holdsPistol(PlayerEntity player) {
        return isPistol(player.getMainHandStack()) || isPistol(player.getOffHandStack());
    }

    private static boolean isPistol(ItemStack stack) {
        return stack.isOf(SparkWitchItems.saviorPistol());
    }
}
