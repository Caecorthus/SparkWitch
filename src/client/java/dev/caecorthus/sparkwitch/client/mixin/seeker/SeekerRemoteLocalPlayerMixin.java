package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Keeps the frozen body sending movement packets while the camera entity is the remote focus (isCamera).
 * TODO(WP-10a): stub until the owning work package adds its injectors. / 待 WP-10a 添加注入器。
 * 相机实体为遥控焦点时，让冻结的本体照常发送移动包（isCamera）。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class SeekerRemoteLocalPlayerMixin {
}
