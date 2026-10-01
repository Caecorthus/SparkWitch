package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.doctor4t.wathe.api.event.DoorInteraction;
import org.jetbrains.annotations.Nullable;

/**
 * Pure neutral-master-key door rule for the Insider (D6): train doors and key-locked room doors, SparkWitch-owned so it
 * works without SparkStrength. The Corrupt Cop gets the same doors only with SparkStrength: NoellesRoles alone lets the
 * Corrupt Cop's neutral key open key-locked room doors ({@code Noellesroles.java:1343-1381}, pinned b58fa5f), and its
 * train-door access comes from SparkStrength's any-neutral rule ({@code CorruptCopRules.neutralMasterKeyDoorResult}).
 * The check order matches both. This listener never conflicts with theirs: for an Insider each of them answers either
 * PASS or the same ALLOW/DENY as this rule (NoellesRoles only DENY while the key cools; SparkStrength ALLOW/DENY on
 * train doors, PASS on key-locked room doors), and {@code DoorInteraction} takes the first non-PASS answer.
 * 内应的中立万能钥匙开门纯规则（D6）：列车门与上锁的房间门，由 SparkWitch 自有，不装 SparkStrength 也能用。黑警只有在装有
 * SparkStrength 时才能开同样的门：仅 NoellesRoles 时黑警的中立钥匙只能开上锁的房间门（{@code Noellesroles.java:1343-1381}，
 * 钉在 b58fa5f），其列车门能力来自 SparkStrength 的“任意中立”规则（{@code CorruptCopRules.neutralMasterKeyDoorResult}）。
 * 检查顺序与两者一致。本监听与它们从不冲突：对内应而言，它们要么返回 PASS，要么返回与本规则相同的 ALLOW/DENY
 * （NoellesRoles 只在钥匙冷却时返回 DENY；SparkStrength 对列车门返回 ALLOW/DENY，对上锁的房间门返回 PASS），
 * 而 {@code DoorInteraction} 取第一个非 PASS 结果。
 */
public final class InsiderDoorRules {
    private InsiderDoorRules() {
    }

    /**
     * PASS unless an Insider holds the neutral master key on a closed, intact door it can open; then DENY while the
     * key cools down, else ALLOW (the caller starts the cooldown). PASS keeps Wathe's own locked/jammed feedback.
     * 仅当内应手持中立万能钥匙、门关闭且未被炸开或卡住、且属于可开的门时才处理：钥匙冷却中返回 DENY，否则 ALLOW
     * （由调用方开始冷却）。其余情况 PASS，保留 Wathe 自身的锁门/卡门反馈。
     */
    public static DoorInteraction.DoorInteractionResult masterKeyDoorResult(
            boolean holdsNeutralMasterKey,
            boolean insider,
            @Nullable DoorInteraction.DoorType doorType,
            boolean blasted,
            boolean jammed,
            boolean open,
            boolean requiresKey,
            boolean keyCoolingDown
    ) {
        if (!holdsNeutralMasterKey || !insider || blasted || jammed || open) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        if (!opensWithMasterKey(doorType, requiresKey)) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        return keyCoolingDown
                ? DoorInteraction.DoorInteractionResult.DENY
                : DoorInteraction.DoorInteractionResult.ALLOW;
    }

    /**
     * Train doors and key-locked doors. Wathe only builds SMALL_DOOR and TRAIN_DOOR contexts, so this is "train doors
     * plus key-locked room doors", the Corrupt Cop's doors with SparkStrength loaded; unlocked room doors already open
     * by hand.
     * 列车门与上锁的门。Wathe 只会构建 SMALL_DOOR 与 TRAIN_DOOR 两种上下文，因此即“列车门 + 上锁的房间门”，也就是装有
     * SparkStrength 时黑警能开的门；未上锁的房间门本来就能徒手打开。
     */
    public static boolean opensWithMasterKey(@Nullable DoorInteraction.DoorType doorType, boolean requiresKey) {
        return doorType == DoorInteraction.DoorType.TRAIN_DOOR || requiresKey;
    }
}
