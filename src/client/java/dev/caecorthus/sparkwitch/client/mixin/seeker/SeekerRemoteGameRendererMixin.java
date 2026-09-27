package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Forces a MISS crosshair target and hides the hand while viewing.
 * TODO(WP-10b): stub until the owning work package adds its injectors. / 待 WP-10b 添加注入器。
 * 观看期间强制准星目标为 MISS 并隐藏手部。
 */
@Mixin(GameRenderer.class)
public abstract class SeekerRemoteGameRendererMixin {
}
