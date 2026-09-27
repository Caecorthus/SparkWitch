package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Routes mouse look to the remote focus and blocks the scroll wheel while viewing.
 * TODO(WP-10a): stub until the owning work package adds its injectors. / 待 WP-10a 添加注入器。
 * 观看期间将鼠标视角交给遥控焦点并禁止滚轮。
 */
@Mixin(Mouse.class)
public abstract class SeekerRemoteMouseMixin {
}
