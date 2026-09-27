package dev.caecorthus.sparkwitch.client.mixin.seeker;

import org.agmas.noellesroles.client.demonhunter.DemonHunterClientHelper;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Client Demon Hunter pistol target (NoellesRoles client helper): nearest-wins device targeting.
 * TODO(WP-04b): stub until the owning work package adds its injectors. / 待 WP-04b 添加注入器。
 * 客户端猎魔枪目标（NoellesRoles 客户端辅助类）：“最近者命中”的设备瞄准。
 */
@Mixin(value = DemonHunterClientHelper.class, remap = false)
public abstract class SeekerDemonHunterTargetMixin {
}
