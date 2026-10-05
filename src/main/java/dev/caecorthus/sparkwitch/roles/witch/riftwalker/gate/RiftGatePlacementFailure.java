package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

/**
 * Why the server refused a gate placement, in check order; each value names the actionbar message the placer sees.
 * A refusal never consumes the item or anything else.
 * 服务端拒绝放置的原因（按检查顺序）；每个值对应放置者看到的动作栏提示。拒绝时从不消耗物品或其他任何东西。
 */
public enum RiftGatePlacementFailure {
    /**
     * Round not active, not a living Riftwalker, stunned, Kidnapper-controlled, interaction-locked or no match bound.
     * 当前不能放置。
     */
    UNAVAILABLE("message.sparkwitch.riftwalker.place.unavailable"),
    /** The placer is inside a gate. / 放置者在门内。 */
    INSIDE_GATE("message.sparkwitch.riftwalker.place.inside_gate"),
    /** No floor within reach under the feet. / 脚下没有可用的地面。 */
    NO_FLOOR("message.sparkwitch.riftwalker.place.no_floor"),
    /**
     * Below Wathe's fall line ({@code playArea.minY}) or above its moving-train cull line; the rest of the play area is
     * ignored since 2026-10-05.
     * 低于 Wathe 坠落线（{@code playArea.minY}）或高于行驶剔除线；自 2026-10-05 起忽略 play area 的其余部分。
     */
    OUT_OF_BOUNDS("message.sparkwitch.riftwalker.place.out_of_bounds"),
    /** The slab would intersect a block. / 薄板会与方块相交。 */
    BLOCKED("message.sparkwitch.riftwalker.place.blocked"),
    /** Water (or any fluid) in the slab. / 薄板内有水（或任何流体）。 */
    IN_FLUID("message.sparkwitch.riftwalker.place.in_fluid"),
    /** A block in the standing cell in front of the gate (C15: no dead exit). / 门正前方一格内有方块（C15：不放死门）。 */
    FRONT_BLOCKED("message.sparkwitch.riftwalker.place.front_blocked"),
    /** Another gate closer than the minimum spacing, or a Seeker device within one block. / 离其他门或搜寻者设备太近。 */
    TOO_CLOSE("message.sparkwitch.riftwalker.place.too_close"),
    /** A door, vent hatch, seat, bed or interactive block within one block. / 1 格内有门、通风口、座位、床或可交互方块。 */
    NEAR_INTERACTIVE("message.sparkwitch.riftwalker.place.near_interactive");

    /** Success actionbar (argument: gate number). / 成功提示（参数：门编号）。 */
    public static final String PLACED_MESSAGE_KEY = "message.sparkwitch.riftwalker.place.success";

    private final String messageKey;

    RiftGatePlacementFailure(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
