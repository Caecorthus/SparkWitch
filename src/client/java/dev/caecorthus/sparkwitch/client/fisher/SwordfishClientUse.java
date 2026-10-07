package dev.caecorthus.sparkwitch.client.fisher;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish.SwordfishRules;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish.SwordfishStabC2SPayload;
import dev.doctor4t.wathe.item.KnifeItem;
import dev.doctor4t.wathe.item.RevolverItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Client selection only; the server owns charge validation, acceptance and consumption.
 * 客户端仅选靶；蓄力校验、命中接纳与消耗均由服务端负责。 */
public final class SwordfishClientUse {
    private SwordfishClientUse() {
    }

    public static void onStoppedUsing(ItemStack stack, World world, LivingEntity user,
                                       int remainingUseTicks, int maxUseTicks) {
        if (!world.isClient || !(user instanceof PlayerEntity attacker) || attacker.isSpectator()
                || attacker.getActiveHand() != Hand.MAIN_HAND || attacker.getMainHandStack() != stack
                || !SwordfishRules.qualifiedHold(maxUseTicks - remainingUseTicks)
                || SparkTraitsKillerBridge.blocksWeaponAction(attacker, stack)) {
            return;
        }
        // Both static producers already carry the Wraith filter; the knife producer also selects nearer devices.
        // 两个静态选靶方法已有冤魂过滤；刀选靶还会优先选择更近的搜寻者设备。
        HitResult hit = KnifeItem.getKnifeTarget(attacker);
        if (hit instanceof EntityHitResult entityHit) {
            ClientPlayNetworking.send(new SwordfishStabC2SPayload(entityHit.getEntity().getId()));
        } else if (hit instanceof BlockHitResult blockHit) {
            RevolverItem.findSleepingPlayerOnBed(world, blockHit).ifPresent(target ->
                    ClientPlayNetworking.send(new SwordfishStabC2SPayload(target.getId())));
        }
    }
}
