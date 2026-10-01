package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.screen.TarotDivinationSelectorScreen;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationClientState;
import dev.caecorthus.sparkwitch.client.text.WitchRoleDisplayTexts;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseEconomy;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseSyncCodec;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Owner-client disguise presentation state: reacts to acting-role changes by closing stale private views
 * (shop screen, instinct mode, SparkWitch role caches) and exposes the synced view to the book and HUD.
 * The view comes only from the owner-only {@code sparkwitch:black_raven_disguise} sync; the client never
 * predicts a switch.
 * 拥有者客户端的伪装展示状态：扮演职业变化时关闭过期的私有视图（商店界面、本能模式、SparkWitch 职业缓存），
 * 并向感知册与 HUD 提供同步视图。视图只来自仅拥有者可见的伪装组件同步；客户端从不预测切换。
 */
public final class BlackRavenDisguiseClientState {
    /**
     * CanSeeMoney runs listeners in phase order and the first non-null answer wins, so this phase precedes the
     * default phase where the disguise role's native listeners (and the raw-role fallback) live.
     * CanSeeMoney 按阶段顺序执行且首个非 null 结果生效，因此该阶段排在伪装职业原生监听器（及真实职业回退）所在的默认阶段之前。
     */
    public static final Identifier DISGUISE_MONEY_PHASE = SparkWitch.id("black_raven_disguise_money");

    private static boolean registered;

    private BlackRavenDisguiseClientState() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackRavenActingRole.setClientChangeListener(BlackRavenDisguiseClientState::onActingRoleChanged);
        CanSeeMoney.EVENT.addPhaseOrdering(DISGUISE_MONEY_PHASE, Event.DEFAULT_PHASE);
        CanSeeMoney.EVENT.register(DISGUISE_MONEY_PHASE, BlackRavenDisguiseClientState::canSeeMoney);
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isOf(SparkWitchItems.blackRavenMask())) {
                lines.addAll(maskTooltip(MinecraftClient.getInstance().player));
            }
        });
    }

    /** Owner's last synced view; EMPTY for anyone but the local player. / 拥有者最近的同步视图；非本地玩家为 EMPTY。 */
    public static BlackRavenDisguiseSyncCodec.View view(@Nullable PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player == null || player != client.player || !SparkWitchServerConnection.isConfirmedServer()) {
            return BlackRavenDisguiseSyncCodec.View.EMPTY;
        }
        return BlackRavenDisguiseComponent.KEY.get(player).clientView();
    }

    /** Local disguised Raven on a live round (the overlay itself also requires the raw role). / 本局存活的本地伪装黑羽鸦。 */
    public static boolean isDisguised(@Nullable PlayerEntity player) {
        return BlackRavenClientState.isEligible(player) && BlackRavenActingRole.isDisguised(player);
    }

    /**
     * Amendment 7: when the local acting role changes (enter, exit, switch), drop every private view of the old
     * identity. NoellesRoles views already re-gate on the local isRole; clearing is the preferred belt.
     * 修订 7：本地扮演职业变化（进入、退出、切换）时，清掉旧身份的所有私有视图。诺艾尔视图本就按本地 isRole 重新判定；
     * 这里的清理是额外保险。
     */
    public static void onActingRoleChanged(@Nullable Role previous, @Nullable Role current) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if (!client.isOnThread()) {
            client.execute(() -> onActingRoleChanged(previous, current));
            return;
        }
        if (client.currentScreen instanceof LimitedInventoryScreen
                || client.currentScreen instanceof TarotDivinationSelectorScreen) {
            client.setScreen(null);
        }
        BlackRavenClientState.reset();
        TarotDivinationClientState.clear();
        if (previous != null && previous == SparkWitchRoles.orthopedist()) {
            clearOrthopedistObserverViews(client.world, client.player);
        }
    }

    /** Client disconnect: forget the local acting entry. / 客户端断开连接：清除本地扮演条目。 */
    public static void clearClient() {
        BlackRavenActingRole.clearClient();
    }

    /** First-phase CanSeeMoney answer for the disguised local player only. / 仅对本地伪装玩家给出的首阶段金钱可见性。 */
    static @Nullable CanSeeMoney.Result canSeeMoney(@Nullable PlayerEntity player) {
        // The event is global, so an integrated server thread must never reach this client answer.
        // 该事件是全局的，集成服务端线程绝不能读到这个客户端结果。
        if (player == null
                || !player.getWorld().isClient
                || !SparkWitchServerConnection.isConfirmedServer()
                || !isDisguised(player)) {
            return null;
        }
        Boolean visible = BlackRavenDisguiseEconomy.moneyVisibleFor(player);
        if (visible == null) {
            return null;
        }
        return visible ? CanSeeMoney.Result.ALLOW : CanSeeMoney.Result.DENY;
    }

    static List<Text> maskTooltip(@Nullable ClientPlayerEntity player) {
        if (player == null || !BlackRavenClientState.isEligible(player)) {
            return List.of();
        }
        BlackRavenDisguiseSyncCodec.View view = view(player);
        if (!view.bound()) {
            return List.of();
        }
        List<Text> lines = new ArrayList<>();
        Identifier identity = view.disguised() ? view.acting() : BlackRavenDisguiseRules.BLACK_RAVEN_ID;
        lines.add(Text.translatable("tooltip.sparkwitch.black_raven_mask.identity", roleName(identity))
                .formatted(Formatting.GRAY));
        int seconds = BlackRavenDisguiseClientRules.statusSeconds(view);
        lines.add(switch (BlackRavenDisguiseClientRules.status(view)) {
            case LOCKED -> Text.translatable("tooltip.sparkwitch.black_raven_mask.locked", seconds)
                    .formatted(Formatting.DARK_GRAY);
            case COOLDOWN -> Text.translatable("tooltip.sparkwitch.black_raven_mask.cooldown", seconds)
                    .formatted(Formatting.DARK_GRAY);
            case READY -> Text.translatable("tooltip.sparkwitch.black_raven_mask.ready")
                    .styled(style -> style.withColor(BlackRavenRules.COLOR));
        });
        if (view.disguised()) {
            lines.add(Text.translatable("tooltip.sparkwitch.black_raven_mask.raven_wallet", view.ravenBalance())
                    .formatted(Formatting.GOLD));
        }
        return List.copyOf(lines);
    }

    /** Colored role display name for an identity id. / 身份 id 对应的带颜色职业名。 */
    public static MutableText roleName(@Nullable Identifier roleId) {
        if (roleId == null) {
            return Text.empty();
        }
        return WitchRoleDisplayTexts.roleName(RoleDisplayTextRules.roleTranslationKey(roleId.getPath()))
                .styled(style -> style.withColor(roleColor(roleId)));
    }

    public static int roleColor(@Nullable Identifier roleId) {
        if (roleId == null || BlackRavenDisguiseRules.BLACK_RAVEN_ID.equals(roleId)) {
            return BlackRavenRules.COLOR;
        }
        Role role = BlackRavenActingRole.resolveRole(roleId);
        return role == null ? BlackRavenRules.COLOR : role.color();
    }

    /**
     * Leaving the Orthopedist identity: other players' Bone Setting bits were only synced because the local player
     * acted as an Orthopedist observer. Clear them on the client so no stale observer data outlives the identity;
     * re-entry resyncs every player on the server.
     * 离开骨科大夫身份：其他玩家的正骨标记仅因本地玩家曾作为骨科大夫观察者才被同步。这里在客户端清除，避免旧观察数据残留；
     * 重新进入时服务端会为所有玩家重新同步。
     */
    private static void clearOrthopedistObserverViews(@Nullable ClientWorld world, @Nullable ClientPlayerEntity self) {
        if (world == null) {
            return;
        }
        for (AbstractClientPlayerEntity other : world.getPlayers()) {
            if (other == self) {
                continue;
            }
            OrthopedistPlayerComponent.KEY.get(other).clearObserverViewOnClient();
        }
    }
}
