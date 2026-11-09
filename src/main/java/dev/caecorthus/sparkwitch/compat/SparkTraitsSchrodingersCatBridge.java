package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Optional SparkTraits seam for 薛定谔之猫. Reflects only the public facade
 * {@code SparkTraitsApi.replaceTraitsIneligibleForCurrentRole(ServerPlayerEntity, BiConsumer)}; never
 * {@code sparktraits.impl}. Called after the new role, kit and balance are written: the provider removes every trait
 * the new role could not have rolled, redraws as many and syncs. Fails closed to "keep traits": an absent, older or
 * failing provider changes nothing, and nothing is thrown. Only the visible names reach the visitor, filtered to
 * {@code Text}.
 * 薛定谔之猫的可选 SparkTraits 接缝。仅反射公共门面
 * {@code SparkTraitsApi.replaceTraitsIneligibleForCurrentRole}，从不触及 {@code sparktraits.impl}。在新身份、装备与余额
 * 写入之后调用：由提供方移除新身份开局无法抽到的全部词条、补抽同等数量并同步。失败关闭为“保留词条”：提供方缺失、过旧
 * 或出错时不做任何改动，且绝不抛出。只有可见的词条名会传给回调，并只保留 {@code Text}。
 */
public final class SparkTraitsSchrodingersCatBridge {
    private static final OptionalStaticMethod REPLACE = OptionalStaticMethod.of("sparktraits",
                    "dev.caecorthus.sparktraits.api.SparkTraitsApi", "replaceTraitsIneligibleForCurrentRole",
                    ServerPlayerEntity.class, BiConsumer.class)
            .warnOnce(failure -> SparkWitch.LOGGER.warn(
                    "SparkTraits replaceTraitsIneligibleForCurrentRole unavailable; a transformed cat keeps its traits",
                    failure));

    private SparkTraitsSchrodingersCatBridge() {
    }

    public static void replaceIneligibleTraits(@Nullable ServerPlayerEntity player,
                                               BiConsumer<List<Text>, List<Text>> visitor) {
        if (player == null) {
            return;
        }
        BiConsumer<Object, Object> filtered = (lost, gained) -> visitor.accept(texts(lost), texts(gained));
        REPLACE.callVoid(player, filtered);
    }

    private static List<Text> texts(Object names) {
        if (!(names instanceof List<?> list)) {
            return List.of();
        }
        List<Text> texts = new ArrayList<>(list.size());
        for (Object name : list) {
            if (name instanceof Text text) {
                texts.add(text);
            }
        }
        return texts;
    }
}
