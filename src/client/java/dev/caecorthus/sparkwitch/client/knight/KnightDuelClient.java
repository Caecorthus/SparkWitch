package dev.caecorthus.sparkwitch.client.knight;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.fiend.FiendMomentHudRules;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.net.KnightDuelStopSoundS2CPayload;
import dev.caecorthus.sparkwitch.net.KnightDuelTargetC2SPayload;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightRules;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelRules;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Knight duel client wiring: the shared ability-key branch, the duel HUD and the stop-track receiver. The Knight never
 * registers a key of its own: G is NoellesRoles' shared {@code abilityBind}, captured by {@code WitchAbilityKeyBridge}
 * and routed by role in {@code SparkWitchClient} (a second binding on the same key would shadow it for every role).
 * Duel state is read only from the synced {@link KnightDuelWorldComponent}; the server decides everything.
 * 骑士决斗客户端接线：共享技能键分支、决斗 HUD 与停止音乐接收器。骑士从不注册自有按键：G 是 NoellesRoles 共享的
 * {@code abilityBind}，由 {@code WitchAbilityKeyBridge} 捕获并在 {@code SparkWitchClient} 中按职业分发（同一按键上的第二个
 * 绑定会让所有职业的该键失效）。决斗状态只读取已同步的 {@link KnightDuelWorldComponent}；一切由服务端裁决。
 */
public final class KnightDuelClient {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    /** Half the vanilla hotbar width (182 / 2) plus a 4 px gap. / 原版快捷栏宽度的一半（182 / 2）加 4 像素间距。 */
    private static final int HOTBAR_HALF_WIDTH = 91 + 4;
    /**
     * Bottom offset of the lifted line: above Wathe's stamina row and its cooldown number (FiendDash/Seeker precedent).
     * 抬升后该行的底部偏移：位于 Wathe 体力行及其冷却数字之上（沿用魔人疾驰与搜寻者）。
     */
    private static final int LIFTED_BOTTOM_OFFSET = 41;
    private static final int ROW_GAP = 2;
    private static boolean registered;

    private KnightDuelClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register(KnightDuelClient::renderHud);
        // Play payload handlers already run on the client thread: stop at once instead of queueing behind later
        // packets. / 播放阶段的数据包处理器已在客户端线程运行：立即停止，不排到后续数据包之后。
        ClientPlayNetworking.registerGlobalReceiver(KnightDuelStopSoundS2CPayload.ID, (payload, context) ->
                context.client().getSoundManager().stopSounds(KnightDuelRules.SF_SOUND_ID, SoundCategory.MASTER));
    }

    /**
     * Whether the Knight branch owns a shared ability-key press: either duelist of a running duel (any role; their G
     * does nothing until the duel ends, owner decision 2026-10-30, and the server drops their G-skill payloads) or a
     * Knight. Checked before every other role branch.
     * 骑士分支是否接管一次共享技能键按下：进行中决斗的任一方（任何职业；决斗结束前其 G 键无效，所有者 2026-10-30 决定，
     * 服务端也会丢弃其 G 键技能包）或骑士。先于其他所有职业分支判定。
     */
    public static boolean ownsAbilityKey(ClientPlayerEntity player, @Nullable Role role) {
        return KnightDuelWorldComponent.KEY.get(player.getWorld()).isDuelist(player.getUuid())
                || KnightRules.isKnight(role);
    }

    /**
     * A Knight's press opens the target panel; a duelist's press is consumed and does nothing. The server
     * re-validates the pick, the price and every lock.
     * 骑士按下时打开目标面板；决斗者的按下被吞掉且无效果。服务端会重新校验目标、价格与所有锁。
     */
    public static void onAbilityKey(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.currentScreen != null
                || !ClientPlayNetworking.canSend(KnightDuelTargetC2SPayload.ID)
                || !mayOpenSelector(player)) {
            return;
        }
        client.setScreen(new KnightDuelScreen(player));
    }

    private static boolean mayOpenSelector(ClientPlayerEntity player) {
        return isIdleKnight(player)
                && PlayerShopComponent.KEY.get(player).getBalance() >= KnightDuelRules.COST;
    }

    /** A living, playing, non-dueling Knight in an ACTIVE round. / ACTIVE 回合中存活、参与且未在决斗的骑士。 */
    private static boolean isIdleKnight(ClientPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && KnightRules.isKnight(game.getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && !WraithClientState.isRestricted(player)
                && !KnightDuelWorldComponent.KEY.get(player.getWorld()).isDuelist(player.getUuid());
    }

    /**
     * Two role-owned lines. The idle Knight's hint takes the shared bottom-right skill-line slot (the Knight has no
     * witch skill), lifted above the hotbar column when it would reach it. The duel line is shown to BOTH duelists,
     * whose own role HUDs may hold the bottom-right slot, so it is a centred top line one row below the Fiend Moment
     * countdown, never on top of another HUD.
     * 两行职业自有展示。空闲骑士的提示占用共享的右下角技能行位置（骑士没有魔女技能），会伸入快捷栏列时抬升到其上方。
     * 决斗行对双方决斗者都显示，而他们自己的职业 HUD 可能占用右下角，因此改为屏幕顶部居中、位于魔人时刻倒计时下一行，
     * 绝不压在其他 HUD 上。
     */
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback still fires under F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        if (player == null || client.world == null || client.options.hudHidden
                || !SparkWitchServerConnection.isConfirmedServer()
                || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        if (KnightDuelWorldComponent.KEY.get(player.getWorld()).isDuelist(player.getUuid())) {
            if (GameFunctions.isPlayerPlayingAndAlive(player)) {
                Text text = Text.translatable("hud.sparkwitch.knight.duel");
                int x = (context.getScaledWindowWidth() - renderer.getWidth(text)) / 2;
                int y = FiendMomentHudRules.LINE_Y + renderer.fontHeight + ROW_GAP;
                context.drawTextWithShadow(renderer, text, x, y, pulseColor());
            }
            return;
        }
        if (!isIdleKnight(player)) {
            return;
        }
        Text text = PlayerShopComponent.KEY.get(player).getBalance() >= KnightDuelRules.COST
                ? Text.translatable("hud.sparkwitch.knight.hint", SparkWitchClient.abilityKeyText())
                : Text.translatable("hud.sparkwitch.knight.need_money");
        int width = context.getScaledWindowWidth();
        int x = Math.max(0, width - RIGHT_PADDING - renderer.getWidth(text));
        int bottomOffset = x < width / 2 + HOTBAR_HALF_WIDTH ? LIFTED_BOTTOM_OFFSET : BOTTOM_PADDING;
        int y = context.getScaledWindowHeight() - bottomOffset - renderer.fontHeight;
        context.drawTextWithShadow(renderer, text, x, y, pulseColor());
    }

    private static int pulseColor() {
        float pulse = (float) (0.75D + 0.15D * Math.sin(System.currentTimeMillis() % 2000L / 2000.0D * Math.PI * 2.0D));
        int alpha = (int) (170.0F * pulse);
        return (alpha << 24) | 0xFFC800;
    }
}
