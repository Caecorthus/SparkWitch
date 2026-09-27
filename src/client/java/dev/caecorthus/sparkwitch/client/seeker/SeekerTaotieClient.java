package dev.caecorthus.sparkwitch.client.seeker;

import dev.caecorthus.sparkwitch.client.hooks.WitchAbilityKeyBridge;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SeekerControlExpertBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarSwallowC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.taotie.SeekerTaotieRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.jetbrains.annotations.Nullable;

/**
 * Taotie client (client-side prediction only; the server re-validates everything in {@code SeekerTaotieService}):
 * decides whether a shared ability-key press belongs to the Search Car under the vanilla crosshair, queues
 * {@code seeker_car_swallow} for the END client tick, and draws a role-owned hint line under the crosshair. It never
 * touches NoellesRoles' Taotie HUD lines (the Saint HUD reserves three of them) and never renders in the witch skill
 * panel. A client that cannot send the payload, or is not on a confirmed SparkWitch server, leaves NoellesRoles alone.
 * 饕餮客户端（仅客户端预测；服务端在 {@code SeekerTaotieService} 中重新校验一切）：判断一次共享技能键按下是否属于
 * 原版准星上的搜寻小车，把 {@code seeker_car_swallow} 排到客户端 END 刻发送，并在准星下方绘制职业自有的提示行。
 * 它从不改动 NoellesRoles 的饕餮 HUD 行（圣徒 HUD 为其预留三行），也从不在魔女技能面板中渲染。无法发送该数据包
 * 或未连接到已确认的 SparkWitch 服务器时，不干预 NoellesRoles。
 */
public final class SeekerTaotieClient {
    private static final int NO_CAR = -1;
    /** Hint offset below the crosshair, clear of NoellesRoles' bottom-right stack. / 提示在准星下方的偏移。 */
    private static final int HINT_OFFSET_Y = 14;
    private static final int FALLBACK_COLOR = 0xFFFFFF;

    private static boolean registered;
    private static int queuedCarId = NO_CAR;

    private SeekerTaotieClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(SeekerTaotieClient::flush);
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                render(context);
            }
        });
    }

    /** Connection lifecycle edges drop an unsent request. / 连接生命周期节点丢弃未发送的请求。 */
    public static void reset() {
        queuedCarId = NO_CAR;
    }

    /** At most one request per tick; a newer claim replaces an older one. / 每刻至多一个请求，新的认领替换旧的。 */
    public static void queueSwallow(int carEntityId) {
        if (carEntityId >= 0) {
            queuedCarId = carEntityId;
        }
    }

    /**
     * Called from {@code SeekerTaotieAbilityKeyMixin} with the original {@code wasPressed} result. Returns the claimed
     * car's entity id, or {@code -1} to leave the press to NoellesRoles. The key identity is checked first so every
     * other binding's per-tick poll stays free.
     * 由 {@code SeekerTaotieAbilityKeyMixin} 以原始 {@code wasPressed} 结果调用。返回被认领小车的实体 id，返回
     * {@code -1} 则把按键留给 NoellesRoles。先检查按键身份，其他按键的每刻轮询因此没有开销。
     */
    public static int claimPress(KeyBinding keyBinding, boolean pressed) {
        if (!pressed || keyBinding == null || keyBinding != NoellesrolesClient.abilityBind) {
            return NO_CAR;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) {
            return NO_CAR;
        }
        SeekerCarEntity car = aimedCar(client, player);
        boolean claimed = SeekerTaotieRules.claimsPress(true, true,
                SparkWitchServerConnection.isConfirmedServer() && ClientPlayNetworking.canSend(SeekerCarSwallowC2SPacket.ID),
                () -> isTaotieReady(player),
                () -> car != null);
        return claimed ? car.getId() : NO_CAR;
    }

    static void flush(MinecraftClient client) {
        if (queuedCarId == NO_CAR) {
            return;
        }
        int carId = queuedCarId;
        queuedCarId = NO_CAR;
        if (!SparkWitchServerConnection.isConfirmedServer()
                || client.player == null
                || client.getNetworkHandler() == null
                || !ClientPlayNetworking.canSend(SeekerCarSwallowC2SPacket.ID)) {
            return;
        }
        ClientPlayNetworking.send(new SeekerCarSwallowC2SPacket(carId));
    }

    static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback still fires under F1; follow Wathe's HUD visibility too.
        // HudRenderCallback 在 F1 下仍会触发；同时跟随 Wathe 的 HUD 显示状态。
        if (player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        if (WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()) {
            return;
        }
        if (!isTaotieReady(player) || aimedCar(client, player) == null) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        Text hint = Text.translatable("hud.sparkwitch.seeker.taotie_hint", WitchAbilityKeyBridge.keyText());
        int x = (context.getScaledWindowWidth() - renderer.getWidth(hint)) / 2;
        int y = context.getScaledWindowHeight() / 2 + HINT_OFFSET_Y;
        context.drawTextWithShadow(renderer, hint, x, y, taotieColor());
    }

    private static boolean isTaotieReady(ClientPlayerEntity player) {
        return SeekerTaotieRules.taotieReady(
                NoellesTaotieSeekerBridge.isTaotie(player),
                GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator() && !player.isCreative(),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                GrandWitchFearService.isPlayerFeared(player),
                SeekerControlExpertBridge.isStunned(player),
                NoellesTaotieSeekerBridge.swallowCooldown(player));
    }

    /**
     * The Search Car under vanilla's crosshair (which also hits players, so it is the nearest thing on the ray) within
     * swallow reach. / 原版准星上的搜寻小车（准星也会命中玩家，因此它是射线上最近的目标），且在吞噬距离内。
     */
    @Nullable
    private static SeekerCarEntity aimedCar(MinecraftClient client, ClientPlayerEntity player) {
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof SeekerCarEntity car)
                || !car.isAlive()) {
            return null;
        }
        boolean inReach = SeekerTaotieRules.withinClientReach(
                player.getEyePos().distanceTo(entityHit.getPos()),
                player.squaredDistanceTo(car),
                NoellesTaotieSeekerBridge.swallowDistanceSquared());
        return inReach ? car : null;
    }

    private static int taotieColor() {
        Role taotie = Noellesroles.TAOTIE;
        return taotie == null ? FALLBACK_COLOR : taotie.color();
    }
}
