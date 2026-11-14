package dev.caecorthus.sparkwitch.client.ferryman;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanRules;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.net.FerrymanFerryC2SPayload;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.net.FerrymanPardonC2SPayload;
import dev.doctor4t.wathe.api.event.CanTargetBody;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Client senders for the Charon: the shared ability key asks the server to pardon a held kill, and the secondary key
 * (2) ferries the body under the Charon's own body-only ray. Both send a request only; the server owns every decision
 * (role, cooldown, controlled-state deny lists, fake bodies, freshness, reach, line of sight).
 * 卡戎的客户端发送端：共享技能键请求服务端宽恕被扣住的击杀，第二技能键（2）摆渡卡戎自身只针对遗体的射线所指的遗体。
 * 两者都只发请求，所有判定（职业、冷却、受控状态拒绝列表、假尸体、新鲜度、距离、视线）由服务端拥有。
 */
public final class FerrymanClient {
    private FerrymanClient() {
    }

    public static void register() {
        FerrymanClientHighlights.register();
        FerrymanHud.register();
        LostSoulOrderHud.register();
        SecondaryAbilityRegistry.register(SparkWitchRoles.FERRYMAN_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                ClientPlayerEntity player = client.player;
                if (!isActiveLocalCharon(player) || !ClientPlayNetworking.canSend(FerrymanFerryC2SPayload.ID)) {
                    return;
                }
                PlayerBodyEntity body = aimedBody(player);
                if (body != null) {
                    ClientPlayNetworking.send(new FerrymanFerryC2SPayload(body.getId()));
                }
            }
        });
    }

    /** Shared ability key press, dispatched by {@code SparkWitchClient} for the exact role only. / 共享技能键按下，由主入口按精确职业分发。 */
    public static void onAbilityKey(MinecraftClient client) {
        if (client.player == null || !ClientPlayNetworking.canSend(FerrymanPardonC2SPayload.ID)) {
            return;
        }
        ClientPlayNetworking.send(new FerrymanPardonC2SPayload());
    }

    /** True while the local player really holds the Charon role. / 本地玩家确实持有卡戎职业时为 true。 */
    public static boolean isLocalFerryman(MinecraftClient client) {
        return isLocalCharonRole(client.player);
    }

    private static boolean isLocalCharonRole(@Nullable ClientPlayerEntity player) {
        return player != null
                && FerrymanRules.isFerryman(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /**
     * The client mirror of the server's acting gate ({@code FerrymanParticipation.isActiveFerryman} plus the ferry's
     * spectator / swallow checks): the raw synced Charon role on a confirmed SparkWitch server, in an ACTIVE round,
     * playing and alive, not a spectator (so not swallowed) and not in creative. Presentation and request trimming
     * only; the server re-checks everything. Cheapest test first: every other client leaves at the role check.
     * 服务端行动门槛（{@code FerrymanParticipation.isActiveFerryman} 加上摆渡的旁观 / 吞噬检查）的客户端镜像：已确认的
     * SparkWitch 服务器上、ACTIVE 回合中、真实同步职业为卡戎、在局存活、非旁观（因此未被吞下）且非创造模式。只用于展示
     * 与过滤无效请求；服务端会重新检查全部条件。先做开销最小的判断：其他所有客户端都在职业检查处返回。
     */
    public static boolean isActiveLocalCharon(@Nullable ClientPlayerEntity player) {
        return isLocalCharonRole(player)
                && SparkWitchServerConnection.isConfirmedServer()
                && GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && !NoellesTaotieSeekerBridge.isSwallowed(player);
    }

    /**
     * Reach of the client ray. The server measures {@link FerrymanRules#FERRY_RANGE} from the Charon's feet to the
     * body's position, while the ray starts at the eyes and the vanilla crosshair stops at 3 blocks; adding the current
     * eye height lets the ray reach every body the server would accept, wherever on it the Charon aims. A body the ray
     * finds past 4 blocks is still sent, and the server answers "too far".
     * 客户端射线长度。服务端以 {@link FerrymanRules#FERRY_RANGE} 度量卡戎脚下到遗体位置的距离，而射线从眼睛出发、原版准星
     * 只到 3 格；加上当前眼高后，无论卡戎瞄准遗体的哪一处，射线都能够到服务端会接受的每一具遗体。射线找到的超过 4 格的
     * 遗体仍会发送，由服务端回复“够不着”。
     */
    static double rayReach(double eyeHeight) {
        return FerrymanRules.FERRY_RANGE + Math.max(0.0, eyeHeight);
    }

    /**
     * The body the secondary key would ferry: the first {@link PlayerBodyEntity} on a body-only ray from the eyes
     * (blocks and closed doors stop it, players standing over the body do not), limited to bodies Wathe's
     * {@link CanTargetBody} seam lets this player target, as the server's first ferry check and Wathe's own body name
     * tag ask it. Mirrors {@code KidnapperTargeting.findAimedBody}; the server re-validates the id it receives.
     * 第二技能键将摆渡的遗体：从眼睛发出、只针对遗体的射线上的第一具 {@link PlayerBodyEntity}（方块与关闭的门会挡住它，站在
     * 遗体上方的玩家不会），且仅限 Wathe 的 {@link CanTargetBody} 接缝允许该玩家选中的遗体，与服务端第一项摆渡判定及 Wathe
     * 自身遗体名牌的询问方式相同。参照 {@code KidnapperTargeting.findAimedBody}；服务端会重新校验收到的 id。
     */
    static @Nullable PlayerBodyEntity aimedBody(ClientPlayerEntity player) {
        HitResult hit = ProjectileUtil.getCollision(
                player,
                entity -> entity instanceof PlayerBodyEntity body
                        && !body.isRemoved()
                        && CanTargetBody.EVENT.invoker().canTarget(player, body),
                rayReach(player.getEyeY() - player.getY())
        );
        return hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof PlayerBodyEntity body
                ? body
                : null;
    }
}
