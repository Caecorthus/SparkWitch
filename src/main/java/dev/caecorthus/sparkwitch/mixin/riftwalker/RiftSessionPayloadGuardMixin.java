package dev.caecorthus.sparkwitch.mixin.riftwalker;

import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Reserved slot (G0) for P2: drops blocked C2S payloads (skills, shop, abilities, item drops) from a player inside a
 * Rift Gate, re-checked on the server thread, following {@code SeekerSessionPayloadGuardMixin} with a Riftwalker-owned
 * deny-list. Empty, and therefore inert, until P2 lands.
 * 为 P2 预留的 mixin（G0）：丢弃门内玩家发送的被禁 C2S 数据包（技能、商店、能力、丢物品），并在服务端线程重新校验，
 * 参照 {@code SeekerSessionPayloadGuardMixin}，使用隙行者自有的拦截名单。P2 落地前为空，因此无效果。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class RiftSessionPayloadGuardMixin {
}
