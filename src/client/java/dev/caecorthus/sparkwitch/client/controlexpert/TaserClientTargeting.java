package dev.caecorthus.sparkwitch.client.controlexpert;

import dev.caecorthus.sparkwitch.compat.SparkTraitsControlExpertBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertRules;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTaserTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.TaserItem;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Client crosshair hint for the Taser. It reuses the server's ray geometry and Marksman range but filters candidates
 * by public state only, so the hint can never reveal a hidden role, trait or faction veto; the server still decides
 * every hit, and players it treats as transparent may still light the hint.
 * 电击枪的客户端准星提示。复用服务端的射线几何与精确枪手射程，但只按公开状态过滤候选者，因此提示永远不会
 * 暴露隐藏的职业、词条或阵营否决；是否命中始终由服务端决定，服务端视为透明的玩家仍可能点亮提示。
 */
public final class TaserClientTargeting {
    public static final Identifier TARGET_CROSSHAIR = Identifier.of("wathe", "hud/crosshair_target");

    private TaserClientTargeting() {
    }

    /** Ready Taser in the main hand of the local Control Expert with a visible player on the ray. / 本地控场专家主手持有就绪的电击枪且射线上有可见玩家。 */
    public static boolean showsTargetCrosshair(ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof TaserItem taser)
                || player.getItemCooldownManager().isCoolingDown(taser)
                || !ControlExpertRules.isControlExpert(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return false;
        }
        double range = ControlExpertRules.taserRange(SparkTraitsControlExpertBridge.marksmanRangeMultiplier(player));
        return ControlExpertTaserTargeting.findTarget(player, range, player.clientWorld.getPlayers(),
                TaserClientTargeting::isVisibleCandidate) != null;
    }

    /** Same public filter as Wathe's own gun crosshair. / 与 Wathe 自身枪械准星相同的公开过滤。 */
    static boolean isVisibleCandidate(PlayerEntity candidate) {
        return candidate.isAlive() && GameFunctions.isPlayerAliveAndSurvival(candidate) && !candidate.isInvisible();
    }
}
