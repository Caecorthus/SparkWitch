package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Snaps the eye height instantly when the focus switches.
 * TODO(WP-10b): stub until the owning work package adds its injectors. / 待 WP-10b 添加注入器。
 * 切换焦点时瞬间对齐眼高。
 */
@Mixin(Camera.class)
public abstract class SeekerRemoteCameraMixin {
}
