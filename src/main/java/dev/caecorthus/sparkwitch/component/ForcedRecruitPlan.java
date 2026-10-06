package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.ForcedRecruit;
import org.jetbrains.annotations.Nullable;

/**
 * The decision for one {@code /sparkwitch:forceAccompliceRole} request, computed before anything is stored: either
 * {@link Accepted} (then applied with {@code WitchWorldComponent#applyForcedRecruit}) or one of the refusals.
 * 一次 {@code /sparkwitch:forceAccompliceRole} 请求的判定，在写入任何状态之前计算：要么是 {@link Accepted}（随后通过
 * {@code WitchWorldComponent#applyForcedRecruit} 应用），要么是某种拒绝。
 */
public sealed interface ForcedRecruitPlan
        permits ForcedRecruitPlan.Accepted, ForcedRecruitPlan.OrderPassed, ForcedRecruitPlan.SpecialHeld {

    /**
     * Store {@code recruit} at {@code order}. {@code movedFrom} is the player's previous pending order (0 when none or
     * unchanged); {@code replaced} is another player's entry this one displaces at {@code order}, if any.
     * 将 {@code recruit} 存入 {@code order}。{@code movedFrom} 是该玩家之前待生效的序号（没有或未变化时为 0）；
     * {@code replaced} 是被本条目在 {@code order} 处顶替的其他玩家条目（若有）。
     */
    record Accepted(ForcedRecruit recruit, int order, int movedFrom, @Nullable ForcedRecruit replaced)
            implements ForcedRecruitPlan {
        public boolean moved() {
            return movedFrom > 0;
        }
    }

    /**
     * The running round has already made {@code recruitedCount} recruitments, so {@code order} can never apply.
     * 本局已完成 {@code recruitedCount} 次招募，{@code order} 已经过去，不可能再生效。
     */
    record OrderPassed(int order, int recruitedCount) implements ForcedRecruitPlan {
    }

    /**
     * Another player's pending entry ({@code holder} at {@code holderOrder}) already holds this special accomplice.
     * 其他玩家的待生效条目（{@code holderOrder} 处的 {@code holder}）已持有该特殊共犯。
     */
    record SpecialHeld(ForcedRecruit holder, int holderOrder) implements ForcedRecruitPlan {
    }
}
