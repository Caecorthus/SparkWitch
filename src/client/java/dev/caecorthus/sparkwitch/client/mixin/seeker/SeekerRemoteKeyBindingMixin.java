package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Remote-view key allowlist on KeyBinding#isPressed/#wasPressed; first statement is the SeekerRemoteViewClient.isActive() gate.
 * TODO(WP-10a): stub until the owning work package adds its injectors. / 待 WP-10a 添加注入器。
 * 遥控视角按键白名单（KeyBinding#isPressed/#wasPressed）；第一条语句是 SeekerRemoteViewClient.isActive() 门槛。
 */
@Mixin(KeyBinding.class)
public abstract class SeekerRemoteKeyBindingMixin {
}
