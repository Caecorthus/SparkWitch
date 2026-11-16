package dev.caecorthus.sparkwitch.client.fisher;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeTargetOcclusion;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Crosshair hint for a main-hand Swordfish, mirroring Wathe's knife hint (same 3-block {@code getKnifeTarget}, so a
 * nearer Seeker device and Wraith pass-through behave as for the knife). Presentation only: the server decides the stab.
 * 主手剑鱼的准星提示，与 Wathe 刀的提示一致（同样 3 格的 {@code getKnifeTarget}，更近的搜寻者设备与冤魂穿透与刀相同）。
 * 仅用于展示：是否刺中由服务端决定。
 */
public final class SwordfishClientTargeting {
    public static final Identifier TARGET_CROSSHAIR = Identifier.of("wathe", "hud/crosshair_target");
    public static final Identifier ATTACK_GLYPH = Identifier.of("wathe", "hud/knife_attack");

    private SwordfishClientTargeting() {
    }

    public static boolean showsTarget(ClientPlayerEntity player) {
        return player != null
                && player.getMainHandStack().isOf(SparkWitchItems.swordfish())
                // Hint only (the stab asks getKnifeTarget itself): a target hidden by BEAR smoke does not light it.
                // 仅提示（刺击自行调用 getKnifeTarget）：被 BEAR 烟雾挡住的目标不点亮准星。
                && SmokeTargetOcclusion.visibleHit(player, KnifeItem.getKnifeTarget(player)) instanceof EntityHitResult;
    }
}
