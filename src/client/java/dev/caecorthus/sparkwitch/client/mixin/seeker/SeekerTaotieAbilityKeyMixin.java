package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Taotie G key: KeyBinding#wasPressed RETURN; when the crosshair is on a Search Car, suppress NoellesRoles' swallow and queue seeker_car_swallow.
 * TODO(WP-06): stub until the owning work package adds its injectors. / 待 WP-06 添加注入器。
 * 饕餮 G 键：KeyBinding#wasPressed RETURN 注入；准星落在搜寻小车上时压制 NoellesRoles 的吞噬并排队发送 seeker_car_swallow。
 */
@Mixin(KeyBinding.class)
public abstract class SeekerTaotieAbilityKeyMixin {
}
