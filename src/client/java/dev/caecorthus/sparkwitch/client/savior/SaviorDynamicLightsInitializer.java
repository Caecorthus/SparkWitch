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
 * faint light. The class is referenced only from the {@code lambdynlights:initializer} entrypoint, so it is never
 * loaded when LDH is absent.
 * 用按玩家计算的光源替代静态 LambDynamicLights 物品定义：手持光剑在刀刃至少伸出一帧后才发光，救世主手枪则
 * 始终发出微弱光。该类只由 {@code lambdynlights:initializer} 入口引用，未安装 LDH 时不会被加载。
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
