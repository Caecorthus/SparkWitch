package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.doctor4t.wathe.item.KnifeItem;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Client knife-stab target: nearest-wins device targeting for the charged right-click stab.
 * TODO(WP-04): stub until the owning work package adds its injectors. / 待 WP-04 添加注入器。
 * 客户端刀刺目标：右键蓄力刺击的“最近者命中”设备瞄准。
 */
@Mixin(KnifeItem.class)
public abstract class SeekerKnifeTargetMixin {
}
