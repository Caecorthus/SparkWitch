package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.doctor4t.wathe.item.DerringerItem;
import dev.doctor4t.wathe.item.RevolverItem;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Client gun target: wraps getGunTarget in RevolverItem/DerringerItem#use (vanilla override, remap = true) for nearest-wins device targeting.
 * TODO(WP-04): stub until the owning work package adds its injectors. / 待 WP-04 添加注入器。
 * 客户端枪械目标：在 RevolverItem/DerringerItem#use（原版覆写，remap = true）中包装 getGunTarget，实现“最近者命中”的设备瞄准。
 */
@Mixin({RevolverItem.class, DerringerItem.class})
public abstract class SeekerGunTargetMixin {
}
