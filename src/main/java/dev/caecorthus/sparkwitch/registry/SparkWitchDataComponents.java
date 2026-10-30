package dev.caecorthus.sparkwitch.registry;

import com.mojang.serialization.Codec;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** SparkWitch item data components. / SparkWitch 物品数据组件。 */
public final class SparkWitchDataComponents {
    /**
     * Per-stack marker of the knife issued to a Knight duel target ({@code KnightDuelKnives}): only that stack is
     * restricted to stabbing the Knight, and every marked stack is confiscated when the duel ends.
     * 发给骑士决斗对手的小刀的逐物品堆标记（{@code KnightDuelKnives}）：只有该物品堆被限制为只能刺骑士，
     * 决斗结束时所有带标记的物品堆都会被收回。
     */
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
