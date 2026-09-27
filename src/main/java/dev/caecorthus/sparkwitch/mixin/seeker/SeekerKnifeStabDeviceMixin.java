package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.doctor4t.wathe.util.KnifeStabPayload;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Server knife-stab receiver: breaks a validated device target.
 * TODO(WP-04): stub until the owning work package adds its injectors. / 待 WP-04 添加注入器。
 * 服务端刀刺接收器：打坏经校验的设备目标。
 */
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false)
public abstract class SeekerKnifeStabDeviceMixin {
}
