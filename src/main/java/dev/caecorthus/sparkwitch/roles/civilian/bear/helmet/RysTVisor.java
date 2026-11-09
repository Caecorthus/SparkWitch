package dev.caecorthus.sparkwitch.roles.civilian.bear.helmet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.ArrayList;
import java.util.List;

/**
 * Frozen contract (WP0): the Rys-T helmet's visor state, stored on the helmet stack as the synced, persistent data
 * component {@code sparkwitch:rys_t_visor}, so the wearer and every other client (equipment sync) see the same visor
 * and cracks. {@code down} is the face shield lowered (H3/H9); {@code cracks} are the bullet holes on its glass in hit
 * order, oldest first, at most {@link BearRules#MAX_VISOR_CRACKS}; they last for the helmet's life (D4). The compact
 * constructor copies the list immutably and keeps only the newest {@code MAX_VISOR_CRACKS}. The server writes it
 * (toggle, crack); clients only read it.
 * 冻结契约（WP0）：Rys-T 头盔的面罩状态，以同步且持久化的数据组件 {@code sparkwitch:rys_t_visor} 存放在头盔物品上，
 * 因此佩戴者与其他所有客户端（装备同步）看到相同的面罩与裂纹。{@code down} 表示面罩放下（H3/H9）；{@code cracks} 为玻璃上
 * 的弹孔，按命中先后排列（最早的在前），至多 {@link BearRules#MAX_VISOR_CRACKS} 个，在头盔存续期间一直保留（D4）。紧凑构造器
 * 以不可变方式复制列表，并只保留最新的 {@code MAX_VISOR_CRACKS} 个。服务端写入（开合、裂纹）；客户端只读。
 */
public record RysTVisor(boolean down, List<VisorCrack> cracks) {
    /** A new helmet: visor down, clean glass. / 新头盔：面罩放下、玻璃完好。 */
    public static final RysTVisor DEFAULT = new RysTVisor(true, List.of());

    public static final Codec<RysTVisor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("down", true).forGetter(RysTVisor::down),
            VisorCrack.CODEC.listOf().optionalFieldOf("cracks", List.of()).forGetter(RysTVisor::cracks)
    ).apply(instance, RysTVisor::new));

    public static final PacketCodec<ByteBuf, RysTVisor> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, RysTVisor::down,
            PacketCodecs.collection(ArrayList::new, VisorCrack.PACKET_CODEC, BearRules.MAX_VISOR_CRACKS),
            visor -> new ArrayList<>(visor.cracks()),
            RysTVisor::new
    );

    public RysTVisor {
        List<VisorCrack> kept = cracks == null ? List.of() : cracks.stream()
                .filter(crack -> crack != null)
                .toList();
        if (kept.size() > BearRules.MAX_VISOR_CRACKS) {
            kept = kept.subList(kept.size() - BearRules.MAX_VISOR_CRACKS, kept.size());
        }
        cracks = List.copyOf(kept);
    }

    /** Visor up ↔ down; cracks unchanged. / 面罩抬起与放下互换；裂纹不变。 */
    public RysTVisor toggle() {
        return new RysTVisor(!down, cracks);
    }

    /**
     * Appends {@code crack}; when the glass already holds {@link BearRules#MAX_VISOR_CRACKS}, the oldest is dropped.
     * A null crack changes nothing. / 追加 {@code crack}；玻璃上已有 {@link BearRules#MAX_VISOR_CRACKS} 个时丢弃最早的一个。
     * 传入 null 不做改变。
     */
    public RysTVisor withCrack(VisorCrack crack) {
        if (crack == null) {
            return this;
        }
        List<VisorCrack> next = new ArrayList<>(cracks.size() + 1);
        next.addAll(cracks);
        next.add(crack);
        return new RysTVisor(down, next);
    }
}
