package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Intentionally empty (optional slot of WP-10b): a body under blindness or darkness is handled by the opaque
 * "signal lost" panel in {@code SeekerCctvOverlay} plus the blanked {@code SeekerViewFilter}, not by fog, so vanilla
 * and Wathe fog stay untouched. Kept registered so the mixin config stays stable.
 * 有意保持为空（WP-10b 的可选项）：本体失明或处于黑暗时，由 {@code SeekerCctvOverlay} 的不透明“信号丢失”面板
 * 与被遮黑的 {@code SeekerViewFilter} 处理，而不是雾效，因此原版与 Wathe 的雾保持不变。仍保留注册以保持
 * mixin 配置稳定。
 */
@Mixin(BackgroundRenderer.class)
public abstract class SeekerRemoteFogMixin {
}
