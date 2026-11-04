package dev.caecorthus.sparkwitch.client.junker;

import dev.caecorthus.sparkwitch.client.hud.JunkerHudRenderer;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.JunkerSearchC2SPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Client half of the 拾荒者: loot vision through walls, the owner-only HUD, and the shared ability key (G, routed by
 * role in {@code SparkWitchClient}) that asks to search the corpse under the crosshair. All three are presentation
 * behind one gate, {@link #isActiveRagpicker}; the server alone validates and runs a search.
 * 拾荒者的客户端：透墙看见掉落物、仅本人可见的 HUD，以及共享技能键（G，由 {@code SparkWitchClient} 按职业分发）
 * 请求搜刮准星下的尸体。三者都是展示，共用同一门槛 {@link #isActiveRagpicker}；搜刮的校验与执行只由服务端决定。
 */
public final class JunkerClientModule {
    private static boolean registered;

    private JunkerClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(JunkerClientModule::highlightLoot);
        HudRenderCallback.EVENT.register((context, tickCounter) -> JunkerHudRenderer.render(context));
    }

    /**
     * Exactly a living, non-spectating, not-swallowed Ragpicker (real synced role, not an acting overlay) in an ACTIVE
     * round on a confirmed SparkWitch server: the first five of the server's search gates ({@code JunkerService}),
     * read the same way. Cheapest test first: every other client leaves at the role check.
     * 已确认的 SparkWitch 服务器上、ACTIVE 回合中、存活、非旁观、未被饕餮吞下的拾荒者（真实同步身份，非扮演覆盖层）：
     * 即服务端搜刮门槛（{@code JunkerService}）的前五项，读法相同。先做开销最小的判断：其他所有客户端都在职业检查处返回。
     */
    public static boolean isActiveRagpicker(@Nullable ClientPlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return JunkerRules.isRagpicker(game.getRole(player))
                && SparkWitchServerConnection.isConfirmedServer()
                && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator()
                && !NoellesTaotieSeekerBridge.isSwallowed(player);
    }

    /**
     * Dropped items within the vision radius glow through walls; every other entity keeps Wathe's default (null).
     * 视野半径内的掉落物透墙发光；其他实体保持 Wathe 默认（返回 null）。
     */
    @Nullable
    private static GetInstinctHighlight.HighlightResult highlightLoot(Entity target) {
        if (!(target instanceof ItemEntity)) {
            return null;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (!isActiveRagpicker(player)) {
            return null;
        }
        double range = JunkerRules.LOOT_VISION_RANGE;
        if (player.squaredDistanceTo(target) > range * range) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(JunkerRules.COLOR);
    }

    /**
     * Ability key: ask to search the corpse under the crosshair. Sends nothing unless the gate passes and the
     * crosshair is on a Wathe body; range, sight, cooldown and loot rules are the server's.
     * 技能键：请求搜刮准星下的尸体。门槛不通过或准星不在 Wathe 尸体上时不发送；距离、视线、冷却与战利品规则由服务端判定。
     */
    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (!isActiveRagpicker(player)
                || !(client.crosshairTarget instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof PlayerBodyEntity body)
                || !ClientPlayNetworking.canSend(JunkerSearchC2SPacket.ID)) {
            return;
        }
        ClientPlayNetworking.send(new JunkerSearchC2SPacket(body.getUuid()));
    }
}
