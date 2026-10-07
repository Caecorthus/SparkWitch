package dev.caecorthus.sparkwitch.roles.killer.magician;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** 公开扩展契约：第二层武器/职业可注册自己的录制动作和播放执行逻辑。 */
public interface MagicianPlaybackActionAdapter {
    boolean supports(ItemStack stack);
    void execute(ServerPlayerEntity owner, MagicianPlaybackFakePlayer proxy, MagicianPlaybackEntity visible, ItemStack stack);
}
