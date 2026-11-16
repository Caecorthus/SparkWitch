package dev.caecorthus.sparkwitch.client.planner;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeTargetOcclusion;
import dev.caecorthus.sparkwitch.client.dominator.DominatorClientModule;
import dev.caecorthus.sparkwitch.client.hud.CachedHudText;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the Master Planner's owner-only two-row readout ({@link PlannerHudRules}) in the bottom-right corner: the
 * next-task countdown above, the recommendation below. Called only by the {@code RoleHudDispatcher.renderPlanner}
 * layer, which owns the F1 and confirmed-server gates. Display only: it reads the local player's raw synced role, the
 * owner-only {@link PlannerComponent} (ready tick against the client world clock, the next-task seconds the server
 * publishes), the synced Wathe shop balance and the shared crosshair pick ({@link PlannerTargeting}, with BEAR
 * smoke applied to the hint only); the target is named as other players see it ({@link DominatorClientModule#visibleName}: psycho scramble, Morphling disguise, Jester
 * moment). It never predicts anything; the server alone accepts a recommendation. Role-owned presentation: never the
 * action bar and never the {@code gui.sparkwitch.skills} panel.
 * 在右下角绘制规划大师仅本人可见的两行读数（{@link PlannerHudRules}）：上行为下个任务倒计时，下行为推荐。只由
 * {@code RoleHudDispatcher.renderPlanner} 层调用，F1 与已确认服务器门槛由该层负责。仅用于展示：读取本地玩家同步的真实职业、
 * 仅本人同步的 {@link PlannerComponent}（依据客户端世界时钟的就绪刻、服务端发布的下个任务秒数）、同步的 Wathe 商店余额以及
 * 共用的准星选择（{@link PlannerTargeting}，BEAR 烟雾只作用于提示）；目标名字按其他玩家看到的样子显示（{@link DominatorClientModule#visibleName}：
 * 疯魔乱码、变形者伪装、小丑时刻）。从不预测任何结果；是否接受推荐只由服务端决定。属于职业自有展示：从不使用动作栏，也从不
 * 进入 {@code gui.sparkwitch.skills} 面板。
 */
public final class PlannerHud {
    // Render-thread caches, one per row; a row's Text and widths depend only on its line (and named target), its key
    // text, the Language, the font renderer and the glyph options (see CachedHudText).
    // 渲染线程缓存，每行一个；每行的文本与宽度只取决于该行（及所写目标）、键名文本、Language、字体渲染器与字形选项（见
    // CachedHudText）。
    private static final CachedHudText<Row> NEXT_TASK = new CachedHudText<>(PlannerHud::text,
            row -> row.line().number(), row -> row.line().measuredNumber());
    private static final CachedHudText<Row> SKILL = new CachedHudText<>(PlannerHud::text,
            row -> row.line().number(), row -> row.line().measuredNumber());

    private PlannerHud() {
    }

    /** One cached row: the pure line plus the aimed player's visible name when it names one. / 一行缓存：纯行加目标名。 */
    private record Row(PlannerHudRules.Line line, @Nullable Text target) {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        // Cheapest exit first: every other client leaves at the raw-role check (getRole stays raw, so a Black Raven
        // acting as the planner never gets her rows).
        // 先做开销最小的判断：其他所有客户端都在真实职业检查处返回（getRole 始终是真实身份，扮演规划大师的黑羽鸦不会得到她的行）。
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        boolean planner = PlannerRules.isPlanner(game.getRole(player));
        if (!planner) {
            return;
        }
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        boolean gameActive = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!PlannerHudRules.showsHud(client.options.hudHidden, gameActive, game.getFade(), trainHudActive, planner,
                GameFunctions.isPlayerPlayingAndAlive(player), player.isSpectator(),
                NoellesTaotieSeekerBridge.isSwallowed(player))) {
            return;
        }
        // Fail closed: no synced component, no rows. / 失败即关闭：没有同步组件就不画。
        PlannerComponent state = PlannerComponent.KEY.getNullable(player);
        if (state == null) {
            return;
        }
        int cooldownTicks = state.skillCooldownRemaining(client.world.getTime());
        int balance = PlayerShopComponent.KEY.get(player).getBalance();
        // The crosshair pick runs only while a press could go through. / 只在按下可能生效时才计算准星目标。
        PlayerEntity aimed = PlannerHudRules.wantsTarget(cooldownTicks, balance)
                ? PlannerTargeting.aimedPlayer(player)
                : null;
        // A player hidden by BEAR smoke does not light the target hint (hint only, the Taser rule): the press still
        // sends the unfiltered pick and the server decides.
        // 被 BEAR 烟雾挡住的玩家不点亮目标提示（仅提示，与电击枪规则相同）：按下仍发送未过滤的选择，由服务端决定。
        PlayerEntity target = aimed != null && !SmokeTargetOcclusion.hides(player, aimed) ? aimed : null;
        PlannerHudRules.Line nextLine = PlannerHudRules.nextTaskLine(state.nextTaskSeconds());
        PlannerHudRules.Line skillLine = PlannerHudRules.skillLine(cooldownTicks, balance, target != null);
        TextRenderer renderer = client.textRenderer;
        NEXT_TASK.update(new Row(nextLine, null), null, renderer);
        SKILL.update(new Row(skillLine, target == null ? null : DominatorClientModule.visibleName(target)),
                skillLine.keyHint() ? SparkWitchClient.abilityKeyText() : null, renderer);

        // The corner row is taken by the shared witch skill line (defensive: the planner has no witch skill id) or by
        // the Saint's Karma line, drawn flush in the corner for her; both rows then move up one row (Hanna rule).
        // 右下角这一行可能被共享魔女技能行占用（防御性处理：规划大师没有魔女技能 id），或被对她紧贴角落绘制的圣徒业报行占用；
        // 此时两行一同上移一行（汉娜的规则）。
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        boolean sharedLineOccupied = witch.getActiveSkillId() != null
                || witch.getSaintState().karmaCooldownTicks() > 0;
        // Both rows rise together: measured with the wider row. / 两行一同抬升：按较宽的一行测量。
        int measured = Math.max(NEXT_TASK.measuredWidth(), SKILL.measuredWidth());
        int width = context.getScaledWindowWidth();
        int lowerY = PlannerHudRules.lowerRowY(width, context.getScaledWindowHeight(), measured, renderer.fontHeight,
                sharedLineOccupied);
        int upperY = PlannerHudRules.upperRowY(lowerY, renderer.fontHeight);
        context.drawTextWithShadow(renderer, NEXT_TASK.text(), PlannerHudRules.rowX(width, NEXT_TASK.width()),
                upperY, nextLine.color());
        context.drawTextWithShadow(renderer, SKILL.text(), PlannerHudRules.rowX(width, SKILL.width()), lowerY,
                skillLine.color());
    }

    /**
     * Arguments in order: the key name, the target's visible name, the number, each only when the row has it.
     * 参数顺序：按键名、目标可见名、数字，各自仅在该行带有时出现。
     */
    private static Text text(Row row, int number, @Nullable Text keyText) {
        PlannerHudRules.Line line = row.line();
        List<Object> args = new ArrayList<>(3);
        if (line.keyHint()) {
            args.add(keyText == null ? Text.empty() : keyText);
        }
        if (line.namesTarget()) {
            args.add(row.target() == null ? Text.empty() : row.target());
        }
        if (line.hasNumber()) {
            args.add(number);
        }
        return Text.translatable(line.key(), args.toArray());
    }
}
