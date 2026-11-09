package dev.caecorthus.sparkwitch.registry;

import com.mojang.serialization.Codec;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisor;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Cylinder;
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

    /**
     * Per-stack marker of the revolver temporarily issued by the Dominator's forced shot: only that stack is
     * confiscated when the sequence ends.
     * 支配者强制开枪临时发放的左轮手枪逐物品堆标记：序列结束时只没收带此标记的物品堆。
     */
    public static final ComponentType<Boolean> FORCED_SHOT_GUN = register("forced_shot_gun",
            ComponentType.<Boolean>builder()
                    .codec(Codec.BOOL)
                    .packetCodec(PacketCodecs.BOOL));

    /**
     * Frozen contract (BEAR WP0): the M32A1's six-chamber cylinder ({@link M32Cylinder}), persistent and synced to
     * clients (HUD). Every launcher stack carries it as a default component (empty).
     * 冻结契约（BEAR WP0）：M32A1 的六弹膛转轮（{@link M32Cylinder}），持久化并同步给客户端（HUD）。每个发射器物品堆都以默认组件
     * （空）携带它。
     */
    public static final ComponentType<M32Cylinder> M32_CYLINDER = register("m32_cylinder",
            ComponentType.<M32Cylinder>builder()
                    .codec(M32Cylinder.CODEC)
                    .packetCodec(M32Cylinder.PACKET_CODEC));

    /**
     * Frozen contract (BEAR WP0): the Rys-T visor state and glass cracks ({@link RysTVisor}), persistent and synced to
     * every client through equipment sync (others see the visor and cracks). Default on every helmet stack.
     * 冻结契约（BEAR WP0）：Rys-T 面罩状态与玻璃裂纹（{@link RysTVisor}），持久化并经装备同步发给所有客户端（他人也能看到面罩与
     * 裂纹）。每个头盔物品堆都带有其默认值。
     */
    public static final ComponentType<RysTVisor> RYS_T_VISOR = register("rys_t_visor",
            ComponentType.<RysTVisor>builder()
                    .codec(RysTVisor.CODEC)
                    .packetCodec(RysTVisor.PACKET_CODEC));

    private SparkWitchDataComponents() {
    }

    private static <T> ComponentType<T> register(String path, ComponentType.Builder<T> builder) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, SparkWitch.id(path), builder.build());
    }

    /** Forces class initialization during mod init. / 在模组初始化时强制加载本类。 */
    public static void register() {
    }
}
