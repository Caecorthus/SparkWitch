package dev.caecorthus.sparkwitch.mixin.seeker;

import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Accessor for NoellesRoles' private dynamic swallow cooldown (calculatedSwallowCooldown).
 * TODO(WP-06): stub until the owning work package adds its injectors. / 待 WP-06 添加注入器。
 * 访问 NoellesRoles 私有的动态吞噬冷却（calculatedSwallowCooldown）。
 */
@Mixin(value = TaotiePlayerComponent.class, remap = false)
public interface SeekerTaotieCooldownAccessor {
}
