package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.doctor4t.wathe.util.GunShootPayload;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Native Vigilante gun parity for the Seeker: OR-wrap of the four isRole calls in Wathe's gun receiver (never a Redirect).
 * TODO(WP-07): stub until the owning work package adds its injectors. / 待 WP-07 添加注入器。
 * 搜寻者享有原生义警枪械豁免：对 Wathe 枪械接收器中四处 isRole 调用做“或”包装（不使用 Redirect）。
 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class SeekerPoliceGunMixin {
}
