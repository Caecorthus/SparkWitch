package dev.caecorthus.sparkwitch.client.knight;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.net.CrossSwordStabC2SPayload;
import dev.caecorthus.sparkwitch.roles.civilian.knight.CrossSwordItem;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client target pick for the cross sword. {@link #aimedDuelTarget} is the one selector: the release sender uses it, and
 * the Wathe crosshair gates on it, so the knife reticle appears whenever the duel target is within knife reach (the
 * release additionally requires the 11-tick hold). The duel state is read only from the auto-synced
 * {@link KnightDuelWorldComponent}; the server revalidates every stab.
 * 十字剑的客户端选靶。{@link #aimedDuelTarget} 是唯一的选靶方法：松手发送使用它，Wathe 准星也以它为准，因此决斗目标
 * 进入刀距时刀准星就会出现（松手还须举满 11 tick）。决斗状态只读取自动同步的 {@link KnightDuelWorldComponent}；服务端
 * 会重新校验每次刺击。
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
