package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Cancels input handling, attack, use, pick and block breaking while viewing.
 * TODO(WP-10a): stub until the owning work package adds its injectors. / 待 WP-10a 添加注入器。
 * 观看期间取消输入处理、攻击、使用、选取与方块破坏。
 */
@Mixin(MinecraftClient.class)
public abstract class SeekerRemoteMinecraftClientMixin {
}
