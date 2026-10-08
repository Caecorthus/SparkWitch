package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * Client-only reads of a player's USEC rifle for the scope, fire input, HUD and arm pose. Presentation and intent
 * only: the server re-validates every shot and every scope flag, so nothing here is trusted.
 * 仅客户端：读取玩家的 USEC 狙击步枪状态，供开镜、开火输入、HUD 与手持姿势使用。只用于表现与意图：服务端复核每次开火
 * 与每个开镜标记，因此这里的任何结果都不被信任。
 */
public final class UsecRifleClient {
    private UsecRifleClient() {
    }

    public static boolean isRifle(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof UsecRifleItem;
    }

    public static boolean holdsRifleInMainHand(PlayerEntity player) {
        return player != null && isRifle(player.getMainHandStack());
    }

    /**
     * Holding use with the rifle in the main hand ({@code UsecRifleItem.use} never starts from the offhand). This is
     * the raised-rifle state the scope profile and the server check share; the {@code usec_scope} intent additionally
     * requires the shared scope view gate ({@code UsecScopeInput}).
     * 主手持狙击步枪并按住使用键（{@code UsecRifleItem.use} 从不从副手开始）。开镜配置与服务端检查共用这一“举枪”状态；
     * {@code usec_scope} 意图还要求共享的开镜视角条件（{@code UsecScopeInput}）。
     */
    public static boolean isUsingRifleInMainHand(PlayerEntity player) {
        return player != null && player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND
                && isRifle(player.getActiveItem());
    }
}
