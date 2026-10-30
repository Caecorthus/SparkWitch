package dev.caecorthus.sparkwitch.client.knight;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.net.CrossSwordStabC2SPayload;
import dev.caecorthus.sparkwitch.roles.civilian.knight.CrossSwordItem;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client target pick for the cross sword. {@link #aimedDuelTarget} is the one selector: the release sender and the
 * Wathe crosshair both use it, so the knife reticle shows exactly when a release would send a stab. The duel state is
 * read only from the auto-synced {@link KnightDuelWorldComponent}; the server revalidates every stab.
 * 十字剑的客户端选靶。{@link #aimedDuelTarget} 是唯一的选靶方法：松手发送与 Wathe 准星共用，因此刀准星出现的时刻
 * 恰好就是松手会发送刺击的时刻。决斗状态只读取自动同步的 {@link KnightDuelWorldComponent}；服务端会重新校验每次刺击。
 */
public final class CrossSwordClientStab {
    private CrossSwordClientStab() {
    }

    /**
     * The duel target this Knight's cross sword would hit from the client's own view: a
     * {@link CrossSwordItem#STAB_RANGE}-block ray that only considers the duel target (blocks still stop it). Other
     * players never shield the target, since duelists pass through everyone (owner, 2026-10-30).
     * 骑士十字剑按客户端自身视角会刺中的决斗目标：{@link CrossSwordItem#STAB_RANGE} 格射线只考虑决斗目标
     * （方块仍会阻挡）。决斗者与所有人互相穿透（所有者，2026-10-30），因此其他玩家无法替目标挡剑。
     */
    public static @Nullable PlayerEntity aimedDuelTarget(PlayerEntity knight) {
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(knight.getWorld());
        if (!duel.isKnight(knight.getUuid())) {
            return null;
        }
        UUID targetId = duel.target();
        HitResult hit = ProjectileUtil.getCollision(knight,
                entity -> entity instanceof PlayerEntity candidate && candidate != knight
                        && candidate.getUuid().equals(targetId) && GameFunctions.isPlayerAliveAndSurvival(candidate),
                CrossSwordItem.STAB_RANGE);
        return hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof PlayerEntity target
                ? target : null;
    }

    /**
     * Crosshair gate: the local player is raising the main-hand cross sword long enough and has the target in reach.
     * 准星门槛：本地玩家正以主手举起十字剑且已举够时长，目标在可刺范围内。
     */
    public static boolean canStabNow(ItemStack mainHandStack) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.isSpectator() || !mainHandStack.isOf(SparkWitchItems.crossSword())
                || !player.isUsingItem() || player.getActiveHand() != Hand.MAIN_HAND
                || player.getActiveItem() != mainHandStack) {
            return false;
        }
        int heldTicks = mainHandStack.getMaxUseTime(player) - player.getItemUseTimeLeft();
        return CrossSwordItem.qualifiedHold(heldTicks) && aimedDuelTarget(player) != null;
    }

    /**
     * Installed as {@link CrossSwordItem.ClientRelease}: runs inside the client's {@code onStoppedUsing}, after vanilla
     * has already sent the release action, and sends {@code sparkwitch:cross_sword_stab} for the aimed target.
     * 作为 {@link CrossSwordItem.ClientRelease} 安装：在客户端 {@code onStoppedUsing} 中运行，此时原版已发送松手动作，
     * 随后为瞄准的目标发送 {@code sparkwitch:cross_sword_stab}。
     */
    static void onRelease(PlayerEntity player, ItemStack stack, int heldTicks) {
        if (player != MinecraftClient.getInstance().player || player.isSpectator()
                || player.getActiveHand() != Hand.MAIN_HAND || player.getMainHandStack() != stack
                || !CrossSwordItem.qualifiedHold(heldTicks)) {
            return;
        }
        PlayerEntity target = aimedDuelTarget(player);
        if (target != null) {
            ClientPlayNetworking.send(new CrossSwordStabC2SPayload(target.getId()));
        }
    }
}
