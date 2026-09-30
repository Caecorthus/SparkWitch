package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.doctor4t.wathe.api.event.DoorInteraction;
import org.jetbrains.annotations.Nullable;

/**
 * Pure neutral-master-key door rule for the Insider: the Corrupt Cop's doors (D6). Mirrors NoellesRoles' neutral-key
 * branch ({@code Noellesroles.java:1343-1381}, pinned b58fa5f) and SparkStrength's neutral branch
 * ({@code CorruptCopRules.neutralMasterKeyDoorResult}), so with SparkStrength loaded both listeners agree and the first
 * non-PASS answer wins either way.
 * 内应的中立万能钥匙开门纯规则：与黑警能开的门相同（D6）。镜像 NoellesRoles 的中立钥匙分支
 * （{@code Noellesroles.java:1343-1381}，钉在 b58fa5f）与 SparkStrength 的中立分支
 * （{@code CorruptCopRules.neutralMasterKeyDoorResult}），因此装有 SparkStrength 时两个监听结果一致，无论谁先返回非 PASS。
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
     * Train doors and key-locked doors. Wathe only builds SMALL_DOOR and TRAIN_DOOR contexts, so this equals the
     * Corrupt Cop's "train doors plus key-locked room doors"; unlocked room doors already open by hand.
     * 列车门与上锁的门。Wathe 只会构建 SMALL_DOOR 与 TRAIN_DOOR 两种上下文，因此等同于黑警的“列车门 + 上锁的房间门”；
     * 未上锁的房间门本来就能徒手打开。
     */
    public static boolean opensWithMasterKey(@Nullable DoorInteraction.DoorType doorType, boolean requiresKey) {
        return doorType == DoorInteraction.DoorType.TRAIN_DOOR || requiresKey;
    }
}
