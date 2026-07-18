package dev.caecorthus.sparkwitch.client.vendetta;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.UseVendettaKnifeC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeRules;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaPlayerComponent;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Resolves the release target client-side; the payload remains untrusted server input. */
public final class VendettaKnifeClientUse {
    private VendettaKnifeClientUse() {
    }

    public static void onStoppedUsing(
            ItemStack stack,
            World world,
            LivingEntity user,
            int remainingUseTicks,
            int maxUseTicks
    ) {
        if (!world.isClient || user.isSpectator() || !(user instanceof PlayerEntity attacker)
                || maxUseTicks - remainingUseTicks < VendettaKnifeRules.MINIMUM_HOLD_TICKS) {
            return;
        }
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.maybeGet(attacker).orElse(null);
        UUID killerUuid = component == null ? null : component.getBoundKillerUuid();
        if (killerUuid == null || !component.isActive() || !component.isKnifeAvailable()) {
            return;
        }

        // Client targeting is feedback only; the server repeats role, UUID, distance, and sight validation.
        // 客户端瞄准仅用于反馈；服务端会重新校验身份、UUID、距离与视线。
        HitResult collision = ProjectileUtil.getCollision(
                attacker,
                entity -> entity instanceof PlayerEntity target
                        && !target.isSpectator()
                        && killerUuid.equals(target.getUuid()),
                VendettaKnifeRules.MAX_RANGE
        );
        if (collision instanceof EntityHitResult entityHit) {
            ClientPlayNetworking.send(new UseVendettaKnifeC2SPacket(entityHit.getEntity().getId()));
        }
    }
}
