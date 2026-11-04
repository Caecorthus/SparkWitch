package dev.caecorthus.sparkwitch.client.dominator;

import dev.caecorthus.sparkwitch.client.emma.EmmaVisibleName;
import dev.caecorthus.sparkwitch.client.hud.DominatorHudRenderer;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotS2CPacket;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-only presentation for the Dominator: the always-on dark green mark outline that does not need the instinct
 * key, the bottom-right HUD line ({@link DominatorHudRenderer}, from a {@code HudRenderCallback} like the other
 * role-owned lines), and the aimed-player lookup and name the HUD shows. Everything reads the owner-synced
 * {@link DominatorPlayerComponent}; the outline is answered per viewer, so no other client ever learns who is marked.
 * The server alone validates and resolves every mark ({@code DominatorSkillService}).
 * 支配者仅本人可见的展示：无需本能键的常亮深绿色标记描边、右下角 HUD 行（{@link DominatorHudRenderer}，与其他职业自有
 * 提示行一样由 {@code HudRenderCallback} 绘制），以及 HUD 所显示的准星目标与名字。全部读取只同步给本人的
 * {@link DominatorPlayerComponent}；描边按观察者裁决，其他客户端无从得知被标记者。标记的校验与解析只由服务端负责
 * （{@code DominatorSkillService}）。
 */
public final class DominatorClientModule {
    /**
     * The Seeker mark's level ({@code SeekerRules.MARK_OUTLINE_PRIORITY}): above SparkWitch's 90 outlines, below
     * {@code skip()} (100), Black Raven (101) and suppression (102), so a hiding skip always wins over the mark.
     * 与搜寻者标记同级：高于 SparkWitch 的 90 级描边，低于 {@code skip()}（100）、黑鸦（101）与压制（102），
     * 因此隐藏用的 skip 总能压过标记。
     */
    private static final int MARK_OUTLINE_PRIORITY = 95;
    private static boolean registered;

    private DominatorClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(DominatorClientModule::highlight);
        HudRenderCallback.EVENT.register((context, tickCounter) -> DominatorHudRenderer.render(context));
        ClientPlayNetworking.registerGlobalReceiver(DominatorForcedShotS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> DominatorForcedShotClientState.lock(payload.lockTicks())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> DominatorForcedShotClientState.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DominatorForcedShotClientState.reset());
    }

    /**
     * The local player is the Dominator (raw synced role, never an acting overlay) on a confirmed SparkWitch server and
     * passes the shared caster gate ({@link DominatorRules#isActiveCaster}: alive, not spectating, not swallowed).
     * Role check first, so every other client leaves at once.
     * 本地玩家在已确认的 SparkWitch 服务器上是支配者（真实同步身份，绝非扮演覆盖层），且通过共用施放者门槛
     * （{@link DominatorRules#isActiveCaster}：存活、非旁观、未被吞下）。先查职业，其他客户端立即返回。
     */
    public static boolean isActiveDominator(@Nullable ClientPlayerEntity player) {
        return player != null
                && DominatorRules.isDominator(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && SparkWitchServerConnection.isConfirmedServer()
                && DominatorRules.isActiveCaster(player);
    }

    /**
     * Whether the viewer may see {@code target} through the mark outline or by name on the HUD: the common mark
     * filter ({@link DominatorRules#isMarkable}: participant, alive, not spectating, not invisible or invisible to the
     * viewer, not swallowed) plus the client-only hidden states of {@link #passesClientGates}.
     * 观察者能否通过标记描边或 HUD 名字看到 {@code target}：通用标记过滤（{@link DominatorRules#isMarkable}：参与者、存活、
     * 非旁观、未隐身且对观察者不隐身、未被吞下）加上 {@link #passesClientGates} 的仅客户端隐藏状态。
     */
    public static boolean isShownTo(ClientPlayerEntity viewer, PlayerEntity target) {
        return DominatorRules.isMarkable(viewer, target) && passesClientGates(viewer, target);
    }

    /**
     * The player the HUD names: the shared 150-block through-wall ray, where a hidden candidate is transparent. The
     * server resolves the real mark with the common filter only, so this is presentation.
     * HUD 所指名的玩家：共用的 150 格穿墙射线，被隐藏的候选者视为透明。真正的标记由服务端只按通用过滤解析，此处仅为展示。
     */
    public static @Nullable PlayerEntity aimedPlayer(ClientPlayerEntity viewer) {
        return DominatorRules.findAimedPlayer(viewer, target -> passesClientGates(viewer, target));
    }

    /**
     * The name other players see (owner decision "按别人看到的来"): Wathe's psycho scramble first, as Wathe's own
     * name tag does for every viewer, then {@link EmmaVisibleName}, which resolves the NoellesRoles Morphling
     * disguise and corpse mode, SparkStrength's Morphling mark and Coroner disguises, the Jester moment and
     * invisibility. The Black Raven disguise changes the acting role, never the name, so it needs nothing here.
     * 其他玩家看到的名字（所有者决定“按别人看到的来”）：先套用 Wathe 对所有观察者都生效的疯魔名牌乱码，再交给
     * {@link EmmaVisibleName}，它解析 NoellesRoles 变形者伪装与尸体模式、SparkStrength 变形者标记与验尸官伪装、小丑时刻和
     * 隐身。黑羽鸦伪装只改变扮演职业、从不改名字，因此这里无需处理。
     */
    public static Text visibleName(PlayerEntity target) {
        if (PlayerPsychoComponent.KEY.get(target).getPsychoTicks() > 0) {
            return Text.literal("urscrewed").formatted(Formatting.OBFUSCATED, Formatting.DARK_RED);
        }
        return EmmaVisibleName.of(target);
    }

    /**
     * Client-only presentation gates, SeekerMarkClientHooks style: a creative target or a swallowed viewer (whose
     * view is the Taotie's stomach). SparkTraits instinct hiding is part of the common {@link DominatorRules#isMarkable}
     * filter, so the server never marks a target the HUD would refuse to name.
     * 仅客户端的展示门槛，与 SeekerMarkClientHooks 一致：目标为创造模式，或观察者自己被吞下（其视野在饕餮腹中）。SparkTraits
     * 本能隐藏属于通用的 {@link DominatorRules#isMarkable} 过滤，因此服务端绝不会标记 HUD 拒绝点名的目标。
     */
    private static boolean passesClientGates(ClientPlayerEntity viewer, PlayerEntity target) {
        return !target.isCreative()
                && !NoellesTaotieSeekerBridge.isSwallowed(viewer);
    }

    private static @Nullable GetInstinctHighlight.HighlightResult highlight(Entity target) {
        if (!(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || target == viewer
                || !DominatorPlayerComponent.KEY.get(viewer).isMarked(targetPlayer.getUuid())
                || !isActiveDominator(viewer)
                || !isShownTo(viewer, targetPlayer)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(DominatorRules.COLOR, MARK_OUTLINE_PRIORITY);
    }
}
