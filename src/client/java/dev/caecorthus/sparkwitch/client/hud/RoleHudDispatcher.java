package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenHudRenderer;
import dev.caecorthus.sparkwitch.client.curser.CurserHudRenderer;
import dev.caecorthus.sparkwitch.client.curser.CurserHudRules;
import dev.caecorthus.sparkwitch.client.guardianangel.GuardianAngelHudRenderer;
import dev.caecorthus.sparkwitch.client.kidnapper.KidnapperControlledHudRenderer;
import dev.caecorthus.sparkwitch.client.magician.MagicianStatusHudRenderer;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.client.saboteur.SaboteurHudRenderer;
import dev.caecorthus.sparkwitch.client.saboteur.SaboteurHudRules;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaHudRenderer;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintHudLayoutRules;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintRules;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRole;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The role HUD panels behind the two {@code InGameHud} TAIL seams, as two hard-coded ordered layer lists. The order is
 * the former per-role mixins' order in {@code sparkwitch.client.mixins.json}: they shared one config and priority 1000,
 * so Mixin applied, and therefore ran, their TAIL injects in declaration order. Each layer keeps its former mixin's gate
 * verbatim and reads the player itself; there is no shared preamble, so layers that never checked the confirmed server
 * (Vendetta, Kidnapper, Magician) still do not. No layer is wrapped: a throwing layer aborts the rest of the frame, as
 * a throwing inject did.
 * 两个 {@code InGameHud} TAIL 接口背后的职业 HUD 面板，以两份写死的有序层列表组织。顺序即原各职业 mixin 在
 * {@code sparkwitch.client.mixins.json} 中的声明顺序：它们同属一个配置且优先级均为 1000，Mixin 按声明顺序应用并执行
 * 这些 TAIL 注入。每层逐字保留原 mixin 的门槛并自行读取玩家；没有共享前置判定，因此原本不检查已确认服务器的层
 * （仇杀客、绑匪、魔术师）仍不检查。各层不做异常包裹：某层抛出异常时与原注入一样中止本帧其余绘制。
 */
public final class RoleHudDispatcher {
    private RoleHudDispatcher() {
    }

    /**
     * Layers at {@code InGameHud.render} TAIL (still drawn under F1), in the former JSON order.
     * {@code InGameHud.render} TAIL 处的各层（F1 下仍绘制），按原 JSON 顺序。
     */
    public static void renderTail(InGameHud hud, DrawContext context) {
        renderCurser(context);
        renderGuardianAngel(context);
        renderOrthopedist(context);
        renderSaint(hud, context);
        renderVendetta(context);
        renderKidnapperControlled(context);
        renderSaboteur(context);
    }

    /**
     * Layers at {@code InGameHud.renderMainHud} TAIL (hidden with F1), in the former JSON order.
     * {@code InGameHud.renderMainHud} TAIL 处的各层（F1 时隐藏），按原 JSON 顺序。
     */
    public static void renderMainHudTail(DrawContext context) {
        renderWitchMana(context);
        renderWitchSkill(context);
        renderMagician(context);
        renderBlackRaven(context);
    }

    /** Was CurserHudMixin: the active promoted Curser only. / 原 CurserHudMixin：仅活动中的晋升诅咒师。 */
    private static void renderCurser(DrawContext context) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            boolean exactCurserRole = GameWorldComponent.KEY.get(player.getWorld()).getRole(player)
                    == SparkWitchRoles.curser();
            if (CurserHudRules.shouldRender(
                    SparkWitchServerConnection.isConfirmedServer(),
                    exactCurserRole,
                    WraithClientState.isActive(player),
                    WraithClientState.isPromoted(player)
            )) {
                CurserHudRenderer.render(context, player);
            }
        }
    }

    /**
     * Was GuardianAngelHudMixin: the promoted Guardian Angel's private primary-skill line.
     * 原 GuardianAngelHudMixin：晋升守护天使仅本人可见的主技能提示。
     */
    private static void renderGuardianAngel(DrawContext context) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !SparkWitchApi.isWraithActive(player)) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (GuardianAngelRules.isGuardianAngel(role)) {
            GuardianAngelHudRenderer.render(context, player);
        }
    }

    /** Was OrthopedistHudMixin: the Orthopedist line. / 原 OrthopedistHudMixin：骨科大夫提示。 */
    private static void renderOrthopedist(DrawContext context) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        // Widened by the Black Raven acting overlay; getRole stays raw. / 黑羽鸦扮演覆盖层会放宽此判定；getRole 仍为真实身份。
        if (GameWorldComponent.KEY.get(player.getWorld()).isRole(player, SparkWitchRoles.orthopedist())) {
            OrthopedistHudRenderer.render(context, player);
        }
    }

    /**
     * Was SaintHudMixin: Saint and Karma lines stacked above, not in place of, the current role ability HUD. Text is
     * measured and drawn with {@code hud.getTextRenderer()}, the instance the mixin's {@code @Shadow} returned.
     * 原 SaintHudMixin：将圣徒与业障状态叠放在当前职业技能 HUD 上方而非覆盖。文字使用 {@code hud.getTextRenderer()}
     * 测量与绘制，即原 mixin 通过 {@code @Shadow} 取得的同一实例。
     */
    private static void renderSaint(InGameHud hud, DrawContext context) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }

        List<Text> lines = SaintHudRenderer.getHudLines(player);
        if (lines.isEmpty()) {
            return;
        }

        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        Identifier roleId = role == null ? null : role.identifier();
        boolean hasSparkWitchSkill = WitchPlayerComponent.KEY.get(player).getActiveSkillId() != null;
        TextRenderer textRenderer = hud.getTextRenderer();
        int reservedHeight = SaintHudLayoutRules.reservedBottomHeight(
                roleId,
                textRenderer.fontHeight,
                hasSparkWitchSkill
        );
        for (Text line : lines) {
            int lineHeight = textRenderer.getWrappedLinesHeight(line, Integer.MAX_VALUE);
            int drawX = context.getScaledWindowWidth()
                    - textRenderer.getWidth(line)
                    - SaintHudLayoutRules.rightPadding(roleId, hasSparkWitchSkill);
            int drawY = SaintHudLayoutRules.drawY(
                    context.getScaledWindowHeight(),
                    lineHeight,
                    reservedHeight
            );
            context.drawTextWithShadow(textRenderer, line, drawX, drawY, SaintRules.COLOR);
            reservedHeight = context.getScaledWindowHeight() - drawY;
        }
    }

    /**
     * Was VendettaHudMixin: the promoted Vendetta's private reveal timer; {@code hasActiveOwnerState} is the whole gate.
     * 原 VendettaHudMixin：晋升仇杀客仅本人可见的透视计时；{@code hasActiveOwnerState} 即全部门槛。
     */
    private static void renderVendetta(DrawContext context) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (VendettaClientPresentation.hasActiveOwnerState(player)) {
            VendettaHudRenderer.render(context, player);
        }
    }

    /**
     * Was KidnapperControlledHudMixin: the drugged controller's screen over the finished HUD; the renderer gates.
     * 原 KidnapperControlledHudMixin：在完整 HUD 绘制结束后覆盖迷药控制者的画面；门槛在渲染器内。
     */
    private static void renderKidnapperControlled(DrawContext context) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            KidnapperControlledHudRenderer.render(context, player);
        }
    }

    /** Was SaboteurHudMixin: the active promoted Saboteur only. / 原 SaboteurHudMixin：仅活动中的升变破坏者。 */
    private static void renderSaboteur(DrawContext context) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        boolean exactSaboteurRole = role != null && SaboteurRole.ID.equals(role.identifier());
        if (SaboteurHudRules.shouldRender(
                SparkWitchServerConnection.isConfirmedServer(),
                exactSaboteurRole,
                WraithClientState.isPromoted(player)
        )) {
            SaboteurHudRenderer.render(context, player);
        }
    }

    /**
     * Was WitchManaHudMixin: SparkWitch mana without changing Wathe's money renderer, then the persistent divination
     * snapshot and the reading slip in the same pass.
     * 原 WitchManaHudMixin：追加 SparkWitch 魔力值且不改动 wathe 的金币渲染器，随后在同一流程绘制持久占卜快照与结果条。
     */
    private static void renderWitchMana(DrawContext context) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            WitchManaHudRenderer.render(context, player);
            TarotDivinationHudRenderer.render(context);
            TarotReadingSlipRenderer.render(context);
        }
    }

    /** Was WitchSkillHudMixin: the SparkWitch active skill line. / 原 WitchSkillHudMixin：SparkWitch 主动技能提示。 */
    private static void renderWitchSkill(DrawContext context) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            WitchSkillHudRenderer.render(context, player);
        }
    }

    /**
     * Was MagicianHudMixin: the Magician status line; only a player check here, the renderer gates the rest.
     * 原 MagicianHudMixin：魔术师状态行；此处只检查玩家，其余门槛在渲染器内。
     */
    private static void renderMagician(DrawContext context) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            MagicianStatusHudRenderer.render(context, player);
        }
    }

    /** Was BlackRavenHudMixin: Black Raven's role-owned mode row. / 原 BlackRavenHudMixin：黑羽鸦角色自有模式行。 */
    private static void renderBlackRaven(DrawContext context) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            BlackRavenHudRenderer.render(context, player);
        }
    }
}
