package dev.caecorthus.sparkwitch.registry;

import com.mojang.serialization.Codec;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * SparkWitch item data components. {@code sparkwitch:special} is a per-stack marker: the same item id can carry it
 * or not, so the system can tell a specifically marked stack apart from ordinary copies of the same item.
 * SparkWitch 物品数据组件。{@code sparkwitch:special} 是逐物品堆的标记：同一个物品 id 可以带或不带它，
 * 系统因此能区分被特别标记的物品堆与同类的普通物品。
 */
public final class SparkWitchDataComponents {
    public static final ComponentType<Boolean> SPECIAL = register("special",
            ComponentType.<Boolean>builder()
                    .codec(Codec.BOOL)
                    .packetCodec(PacketCodecs.BOOL));
    /** Marks the knife handed to a Knight duel opponent. / 标记发给骑士决斗对手的小刀。 */
    public static final ComponentType<Boolean> DUEL_KNIFE = register("duel_knife",
            ComponentType.<Boolean>builder()
                    .codec(Codec.BOOL)
                    .packetCodec(PacketCodecs.BOOL));

    private SparkWitchDataComponents() {
    }

    private static <T> ComponentType<T> register(String path, ComponentType.Builder<T> builder) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, SparkWitch.id(path), builder.build());
    }

    /** Forces class initialization during mod init. / 在模组初始化时强制加载本类。 */
    public static void register() {
    }
}
