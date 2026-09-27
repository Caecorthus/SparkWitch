package dev.caecorthus.sparkwitch.client.mixin.seeker;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Cancels attack/interact calls and clears (never releases) item use while viewing.
 * TODO(WP-10a): stub until the owning work package adds its injectors. / 待 WP-10a 添加注入器。
 * 观看期间取消攻击与交互调用，并清除（绝不释放）物品使用。
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class SeekerRemoteInteractionMixin {
}
