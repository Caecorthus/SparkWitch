package dev.caecorthus.sparkwitch.mixin.seeker;

import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Third wrapper on ServerPlayNetworkAddon#receive: drops SeekerRemoteRules.BLOCKED_WHILE_VIEWING payloads from locked players.
 * TODO(WP-09): stub until the owning work package adds its injectors. / 待 WP-09 添加注入器。
 * ServerPlayNetworkAddon#receive 上的第三个包装器：丢弃被锁定玩家发送的 SeekerRemoteRules.BLOCKED_WHILE_VIEWING 数据包。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class SeekerSessionPayloadGuardMixin {
}
