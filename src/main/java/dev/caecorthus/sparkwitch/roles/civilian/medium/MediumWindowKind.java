package dev.caecorthus.sparkwitch.roles.civilian.medium;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * What the dead are voting on in the Medium's world vote window. The ordinal is the stable wire and NBT index.
 * 通灵师世界投票窗口中死者正在投票的内容。序数即稳定的线上与 NBT 序号。
 */
public enum MediumWindowKind {
    /** 通灵: a yes/no question; choices {@link MediumRules#VOTE_NO} and {@link MediumRules#VOTE_YES}. / 通灵：是非提问。 */
    QUESTION,
    /** 幽灵的加护: one of {@link MediumBlessing}; the choice is its index. / 幽灵的加护：选项为 {@link MediumBlessing} 的序号。 */
    BLESSING;

    public static final PacketCodec<ByteBuf, MediumWindowKind> PACKET_CODEC =
            PacketCodecs.VAR_INT.xmap(MediumWindowKind::byIndex, MediumWindowKind::index);

    public int index() {
        return ordinal();
    }

    /** An unknown index reads as {@link #QUESTION}, the idle default. / 未知序号按空闲默认值 {@link #QUESTION} 读取。 */
    public static MediumWindowKind byIndex(int index) {
        return index == BLESSING.ordinal() ? BLESSING : QUESTION;
    }
}
