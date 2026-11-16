package dev.caecorthus.sparkwitch.client.criminalreasoner;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.client.hud.CriminalReasonerHudRenderer;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.ConfirmCriminalReasonerC2SPacket;
import dev.caecorthus.sparkwitch.net.CriminalReasonerInvisibilityC2SPacket;
import dev.caecorthus.sparkwitch.net.OpenCriminalReasonerS2CPacket;
import dev.caecorthus.sparkwitch.net.RequestCriminalReasonerC2SPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerRules;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerRuntime;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client half of the Criminal Reasoner (presentation and requests only; the server decides everything): the reasoning
 * menu on the shared ability key, the invisibility charge on the secondary ability key, the instinct hide while he is
 * invisible, and his HUD rows.
 * 犯罪推理学家的客户端（只负责展示与请求，一切由服务端裁决）：主技能键打开推理菜单，第二技能键消耗隐身充能，隐身期间的
 * 本能屏蔽，以及他的 HUD 行。
 */
public final class CriminalReasonerClientModule {

    /**
     * Instinct hide priority while a Reasoner is invisible: above every SparkWitch outline, the Specter's mark (1000)
     * included, so no instinct sees him (PR rule "隐身期间任何本能透视都看不见他"). Wathe keeps the highest priority.
     * 推理学家隐身期间的本能隐藏优先级：高于所有 SparkWitch 描边（包括幽魂标记的 1000），因此任何本能都看不见他
     * （PR 规则“隐身期间任何本能透视都看不见他”）。Wathe 保留优先级最高的结果。
     */
    public static final int INVISIBLE_HIDE_PRIORITY = 1_100;

    private static boolean registered;
    private static boolean pendingRequest;
    private static long pendingSince;

    private CriminalReasonerClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(CriminalReasonerClientModule::hideInvisibleReasoner);
        ClientPlayNetworking.registerGlobalReceiver(OpenCriminalReasonerS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    pendingRequest = false;
                    MinecraftClient client = context.client();
                    // A late answer must not pop a menu over another screen or for a player who can no longer act.
                    // 迟到的回包不得盖住其他界面，也不得为已无法行动的玩家打开菜单。
                    if (client.currentScreen != null || !isActiveReasoner(client)) {
                        return;
                    }
                    client.setScreen(new CriminalReasonerScreen(payload));
                }));
        SecondaryAbilityRegistry.register(CriminalReasonerRules.ROLE_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                // Only a press that can work is sent: a charge left and not invisible already. The server re-checks.
                // 只发送可能生效的按键：仍有充能且尚未隐身。服务端会再次检查。
                ClientPlayerEntity player = client.player;
                if (client.currentScreen != null || !isActiveReasoner(client)
                        || player.hasStatusEffect(StatusEffects.INVISIBILITY)
                        || !ClientPlayNetworking.canSend(CriminalReasonerInvisibilityC2SPacket.ID)) {
                    return;
                }
                CriminalReasonerComponent component = CriminalReasonerComponent.KEY.getNullable(player);
                if (component == null || component.getInvisibilityCharges() <= 0) {
                    return;
                }
                ClientPlayNetworking.send(new CriminalReasonerInvisibilityC2SPacket());
            }

            @Override
            public void reset() {
                pendingRequest = false;
            }
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> CriminalReasonerHudRenderer.render(context));
    }

    /**
     * Instinct highlights are evaluated on the client only, so the hide lives here. Only an invisible Criminal Reasoner
     * (role from Wathe's synced component, which every client holds) is hidden; other invisible players keep whatever
     * outline their own rules give them. Off a confirmed SparkWitch server nothing is hidden.
     * 本能高亮只在客户端求值，因此屏蔽放在这里。只隐藏处于隐身中的犯罪推理学家（职业取自 Wathe 同步给每个客户端的组件）；
     * 其他隐身玩家保持各自规则给出的描边。不在已确认的 SparkWitch 服务器上时不隐藏任何人。
     */
    private static GetInstinctHighlight.@Nullable HighlightResult hideInvisibleReasoner(@Nullable Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer() || !(target instanceof PlayerEntity player)
                || !player.isInvisible() || !CriminalReasonerRuntime.isReasoner(player)) {
            return null;
        }
        return new GetInstinctHighlight.HighlightResult(-1, false, INVISIBLE_HIDE_PRIORITY);
    }

    /** Primary ability key: ask the server for the roster. 主技能键：向服务端要名单。 */
    public static void requestSelection(MinecraftClient client) {
        // A request that never came back must not lock the key forever. / 没有回音的请求不能永久锁死这个键。
        if (pendingRequest && System.currentTimeMillis() - pendingSince > 5000L) {
            pendingRequest = false;
        }
        if (pendingRequest
                || client.currentScreen != null
                || !ClientPlayNetworking.canSend(RequestCriminalReasonerC2SPacket.ID)
                || !isActiveReasoner(client)) {
            return;
        }
        pendingRequest = true;
        pendingSince = System.currentTimeMillis();
        ClientPlayNetworking.send(new RequestCriminalReasonerC2SPacket());
    }

    public static void confirm(MinecraftClient client, UUID sessionId, UUID victimId, UUID suspectId) {
        if (!ClientPlayNetworking.canSend(ConfirmCriminalReasonerC2SPacket.ID) || !isActiveReasoner(client)) {
            return;
        }
        ClientPlayNetworking.send(new ConfirmCriminalReasonerC2SPacket(sessionId, victimId, suspectId));
    }

    public static void clear() {
        pendingRequest = false;
    }

    /**
     * The local player is an acting Reasoner: confirmed server, ACTIVE round, exact role, playing and alive, not
     * spectating and not swallowed (Wathe keeps a dead player's role until the round is finalized).
     * 本地玩家是可行动的推理学家：已确认的服务器、ACTIVE 回合、精确职业、在局存活、非旁观且未被吞下（死亡玩家的职业会保留到
     * 对局结算）。
     */
    public static boolean isActiveReasoner(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null
                && SparkWitchServerConnection.isConfirmedServer()
                && GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && CriminalReasonerRuntime.isReasoner(player)
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator()
                && !NoellesTaotieSeekerBridge.isSwallowed(player);
    }
}
