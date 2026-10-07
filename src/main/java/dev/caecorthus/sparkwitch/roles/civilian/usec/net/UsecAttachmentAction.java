package dev.caecorthus.sparkwitch.roles.civilian.usec.net;

import org.jetbrains.annotations.Nullable;

/**
 * Stable wire ids of the attachment-screen actions carried by {@link UsecAttachmentC2SPacket}: the id is the ordinal,
 * so only append new actions at the end. "ATTACHED" actions act on the magazine inserted in the rifle; "LOOSE" actions
 * act on a loose magazine whose slot travels in the packet's {@code rifleSlot} field.
 * {@link UsecAttachmentC2SPacket} 携带的配件界面动作的稳定线上 id：id 即序号，新动作只能追加在末尾。
 * 「ATTACHED」动作作用于插在步枪上的弹匣；「LOOSE」动作作用于散装弹匣，其栏位通过数据包的 {@code rifleSlot} 字段传递。
 */
public enum UsecAttachmentAction {
    INSERT_MAGAZINE,
    REMOVE_MAGAZINE,
    ATTACH_SUPPRESSOR,
    DETACH_SUPPRESSOR,
    CHAMBER_ROUND,
    UNLOAD_CHAMBER,
    LOAD_ROUND_ATTACHED,
    UNLOAD_ROUND_ATTACHED,
    LOAD_ROUND_LOOSE,
    UNLOAD_ROUND_LOOSE;

    private static final UsecAttachmentAction[] VALUES = values();

    /** Stable wire id. / 稳定的线上 id。 */
    public int id() {
        return ordinal();
    }

    /** Tolerant lookup: unknown ids return null. / 宽容查找：未知 id 返回 null。 */
    public static @Nullable UsecAttachmentAction fromId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    /** True for the actions on a loose magazine. / 作用于散装弹匣的动作返回 true。 */
    public boolean targetsLooseMagazine() {
        return this == LOAD_ROUND_LOOSE || this == UNLOAD_ROUND_LOOSE;
    }
}
