package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Yields the camera / resets on onSetCameraEntity and onPlayerRespawn, on the main thread only (never at HEAD).
 * TODO(WP-10a): stub until the owning work package adds its injectors. / 待 WP-10a 添加注入器。
 * 在 onSetCameraEntity 与 onPlayerRespawn 时让出相机或重置，且只在主线程执行（绝不在 HEAD）。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class SeekerRemoteNetworkHandlerMixin {
}
