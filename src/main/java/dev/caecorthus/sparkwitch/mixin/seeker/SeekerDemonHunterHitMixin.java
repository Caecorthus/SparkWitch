package dev.caecorthus.sparkwitch.mixin.seeker;

import org.agmas.noellesroles.demonhunter.DemonHunterShootC2SPacket;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Server Demon Hunter pistol receiver (NoellesRoles): breaks a validated device target; NoellesRoles' kill path is unchanged.
 * TODO(WP-04b): stub until the owning work package adds its injectors. / 待 WP-04b 添加注入器。
 * 服务端猎魔枪接收器（NoellesRoles）：打坏经校验的设备目标；NoellesRoles 的击杀逻辑不变。
 */
@Mixin(value = DemonHunterShootC2SPacket.Receiver.class, remap = false)
public abstract class SeekerDemonHunterHitMixin {
}
