package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold;

import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Side-neutral rules for the Grand Witch recruitment hold.
 * 大魔女招募定身的两端通用规则。
 */
public final class RecruitmentHoldRules {
    /**
     * Slowness VII: -105 % movement speed, which the attribute clamps to zero.
     * 缓慢 VII：移动速度 -105%，属性下限将其钳制为零。
     */
    public static final int SLOWNESS_AMPLIFIER = 6;

    /**
     * Blocks the held player may drift from the anchor before the server pulls them back.
     * 被定身玩家偏离锚点超过该格数时，服务端才会将其拉回。
     */
    public static final double DRIFT_THRESHOLD = 0.5;

    private RecruitmentHoldRules() {
    }

    /**
     * Horizontal drift or a rise above the anchor. Falling is left to gravity, so an anchor taken in mid-air never
     * rubber-bands the recruit every few ticks.
     * 水平偏移或高于锚点。下落交给重力处理，因此在半空取得的锚点不会让被招募者每隔几刻就被拉回一次。
     */
    public static boolean drifted(double dx, double dy, double dz) {
        return dx * dx + dz * dz > DRIFT_THRESHOLD * DRIFT_THRESHOLD || dy > DRIFT_THRESHOLD;
    }

    /**
     * Wathe deaths that still apply to a held recruit: a disconnect ({@code wathe:escaped}) must resolve normally, and
     * {@code /kill} ({@code wathe:vanilla_death}) stays an operator's explicit action. Every other reason is
     * cancelled, forced or not.
     * 仍会作用于被定身新共犯的 Wathe 死亡：断线（{@code wathe:escaped}）必须照常结算，{@code /kill}
     * （{@code wathe:vanilla_death}）仍是管理员的显式操作。其余死因无论是否强制均被取消。
     */
    public static boolean piercesHold(@Nullable Identifier deathReason) {
        return GameConstants.DeathReasons.ESCAPED.equals(deathReason)
                || GameConstants.DeathReasons.VANILLA_DEATH.equals(deathReason);
    }

    /**
     * Vanilla invisibility still renders held items; they vanish with the body for every non-spectator viewer.
     * 原版隐身仍会渲染手持物；对所有非旁观者，手持物随身体一起消失。
     */
    public static boolean hidesHeldItems(boolean holderHeld, boolean viewerSpectating) {
        return holderHeld && !viewerSpectating;
    }
}
