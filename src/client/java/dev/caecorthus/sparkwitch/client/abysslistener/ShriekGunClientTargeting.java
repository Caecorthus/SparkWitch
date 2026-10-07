package dev.caecorthus.sparkwitch.client.abysslistener;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.gun.ShriekGunItem;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.gun.ShriekGunTargeting;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Client crosshair hint for the Shriek Gun. It reuses the server's beam geometry with current boxes but filters
 * candidates by public state only, so the hint lights the same way for allies and enemies and never reveals a faction,
 * role, trait or veto; the server still decides every hit, and players it treats as transparent may still light it.
 * 啸音铳的客户端准星提示。复用服务端的射线几何（使用当前箱体），但只按公开状态过滤候选者，因此对队友和敌人的提示完全
 * 相同，永远不会暴露阵营、职业、词条或否决；是否命中始终由服务端决定，服务端视为透明的玩家仍可能点亮提示。
 */
public final class ShriekGunClientTargeting {
    public static final Identifier TARGET_CROSSHAIR = Identifier.of("wathe", "hud/crosshair_target");

    private ShriekGunClientTargeting() {
    }

    /**
     * Ready Shriek Gun in the main hand of the local Abyss Listener with a visible player on the beam.
     * 本地聆渊者主手持有就绪的啸音铳，且射线上有可见玩家。
     */
    public static boolean showsTargetCrosshair(ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof ShriekGunItem gun)
                || player.getItemCooldownManager().isCoolingDown(gun)
                || !AbyssListenerRules.isAbyssListener(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return false;
        }
        return ShriekGunTargeting.findTarget(player, player.clientWorld.getPlayers(),
                ShriekGunClientTargeting::isVisibleCandidate) != null;
    }

    /** Same public filter as Wathe's own gun crosshair; no faction input. / 与 Wathe 自身枪械准星相同的公开过滤，不读取阵营。 */
    static boolean isVisibleCandidate(PlayerEntity candidate) {
        return candidate.isAlive() && GameFunctions.isPlayerAliveAndSurvival(candidate) && !candidate.isInvisible();
    }
}
