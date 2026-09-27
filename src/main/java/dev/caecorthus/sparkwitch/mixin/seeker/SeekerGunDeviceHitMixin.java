package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.doctor4t.wathe.util.GunShootPayload;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Server gun receiver: breaks a validated device target at the recordItemUse anchor.
 * TODO(WP-04): stub until the owning work package adds its injectors. / 待 WP-04 添加注入器。
 * 服务端枪械接收器：在 recordItemUse 锚点处打坏经校验的设备目标。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class SeekerGunDeviceHitMixin {
}
