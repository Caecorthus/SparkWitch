package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

/**
 * Pure placement geometry and validation for a Rift Gate at the user's feet (plan §5.1): facing snapped to the four
 * horizontal directions, spacing from other gates ({@code RiftwalkerRules.MIN_GATE_SPACING}), play-area and floor
 * rules, and the Riftwalker-owned copy of the Seeker camera neighbour table. Unit-testable without Minecraft
 * registries (records, ints, doubles, enums only). Owned by P1.
 * 在使用者脚下放置裂隙门的纯几何与校验规则（plan §5.1）：朝向吸附到四个水平方向、与其他门的间距
 * （{@code RiftwalkerRules.MIN_GATE_SPACING}）、play area 与地面规则，以及复制到本职业的搜寻者摄像头邻居表。
 * 无需 Minecraft 注册表即可单元测试（仅使用 record、int、double、enum）。归属 P1。
 */
public final class RiftGatePlacementRules {
    // TODO(P1): pure placement helpers (facing snap, spacing, footprint, neighbour table). / TODO(P1)：纯放置辅助方法。

    private RiftGatePlacementRules() {
    }
}
