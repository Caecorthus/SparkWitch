package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Optional: fog consistency with the body's blindness/darkness while viewing (keep empty if the signal-lost panel is used).
 * TODO(WP-10b): stub until the owning work package adds its injectors. / 待 WP-10b 添加注入器。
 * 可选：观看期间雾效与本体失明/黑暗保持一致（若采用信号丢失面板则保持为空）。
 */
@Mixin(BackgroundRenderer.class)
public abstract class SeekerRemoteFogMixin {
}
