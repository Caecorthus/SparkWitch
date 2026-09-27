package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Renders the owner's own body when seen from the car or camera.
 * TODO(WP-10b): stub until the owning work package adds its injectors. / 待 WP-10b 添加注入器。
 * 从小车或摄像头视角看时渲染拥有者自己的本体。
 */
@Mixin(WorldRenderer.class)
public abstract class SeekerRemoteWorldRendererMixin {
}
