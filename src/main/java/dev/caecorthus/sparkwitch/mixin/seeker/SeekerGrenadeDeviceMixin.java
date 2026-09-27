package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.doctor4t.wathe.entity.GrenadeEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wathe grenade (incl. Bomb Maniac): onCollision HEAD blast (sphere + line of sight) breaks devices; server only.
 * TODO(WP-04): stub until the owning work package adds its injectors. / 待 WP-04 添加注入器。
 * Wathe 手雷（含炸弹狂人）：onCollision HEAD 处的爆炸（球 + 视线）打坏设备；仅服务端。
 */
@Mixin(GrenadeEntity.class)
public abstract class SeekerGrenadeDeviceMixin {
}
