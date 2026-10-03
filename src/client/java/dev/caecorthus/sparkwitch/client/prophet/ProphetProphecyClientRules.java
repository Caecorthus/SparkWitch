package dev.caecorthus.sparkwitch.client.prophet;

import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetDeathCauseGroup;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.ProphecyRecord;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import org.jetbrains.annotations.Nullable;

/**
 * Pure presentation and send gates for the Prophecy screen. They only mirror the server so the UI does not offer
 * a click the server would refuse; they grant no authority.
 * 预言界面的纯展示与发送门禁。它们只镜像服务端规则，避免界面提供必被拒绝的操作，本身没有任何裁决权。
 */
public final class ProphetProphecyClientRules {
    private ProphetProphecyClientRules() {
    }

    public enum VictimStatus {
        UNGUESSED,
        PENDING,
        SOLVED_KILLER,
        SOLVED_NO_KILLER;

        public boolean solved() {
            return this == SOLVED_KILLER || this == SOLVED_NO_KILLER;
        }
    }

    public static boolean maySend(boolean confirmedServer, boolean channelAvailable, boolean liveProphet) {
        return confirmedServer && channelAvailable && liveProphet;
    }

    public static VictimStatus status(@Nullable ProphecyRecord record) {
        if (record == null) {
            return VictimStatus.UNGUESSED;
        }
        return switch (record.outcome()) {
            case REVEALED_KILLER -> VictimStatus.SOLVED_KILLER;
            case NO_KILLER -> VictimStatus.SOLVED_NO_KILLER;
            case PENDING -> record.excluded().isEmpty() ? VictimStatus.UNGUESSED : VictimStatus.PENDING;
        };
    }

    public static boolean isExcluded(@Nullable ProphecyRecord record, ProphetDeathCauseGroup group) {
        return record != null && group != null && record.excluded().contains(group);
    }

    /** A cause cell is clickable for a selected, unsolved victim unless it was already excluded. / 选中未猜中死者时，未被排除的死因可点击。 */
    public static boolean isCauseSelectable(boolean victimSelected, @Nullable ProphecyRecord record,
                                            ProphetDeathCauseGroup group) {
        return victimSelected && !status(record).solved() && !isExcluded(record, group);
    }

    public static boolean hasEnoughMoney(int balance) {
        return balance >= ProphetRules.PROPHECY_COIN_COST;
    }

    public static boolean canConfirm(boolean submitted, boolean sessionPending, boolean victimSelected,
                                     @Nullable ProphecyRecord record, @Nullable ProphetDeathCauseGroup group,
                                     int balance) {
        return !submitted && sessionPending && group != null
                && isCauseSelectable(victimSelected, record, group) && hasEnoughMoney(balance);
    }
}
