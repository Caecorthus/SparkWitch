package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Remembers the effective duration of each weapon cooldown (after SparkTraits modifiers) and halves that remembered
 * value when the Savior observes a non-Savior death. The sword's hard left-click lock already lives in the client
 * swing mixin; this only changes numbers server-side.
 * 记录每把武器本次进入冷却时的实际时长（经过 SparkTraits 倍率后），救世主目击非自己所杀死亡时按该时长的一半削减。
 * 光剑冷却期硬禁左键已在客户端挥动 mixin 里；这里只改服务端数值。
 */
public final class SaviorCooldowns {
    /** Player UUID → weapon item → the effective cooldown duration of the current entry. / 玩家→武器→本次冷却实际时长。 */
    private static final Map<UUID, Map<Item, Integer>> ENTRIES = new HashMap<>();

    private SaviorCooldowns() {
    }

    /**
     * Sets the cooldown and remembers its effective duration after Traits modifiers. Every Savior weapon cooldown goes
     * through this one writer; the post-use values are the SFA nominals in {@code SparkWitchItemCooldownNominals}.
     * 写入冷却并记录倍率后的实际时长。救世主武器冷却只经由此写入；使用后数值即 {@code SparkWitchItemCooldownNominals} 中的
     * SFA 标准冷却。
     */
    public static void writeCooldown(ServerPlayerEntity player, Item item, int ticks) {
        player.getItemCooldownManager().set(item, ticks);
        int effective = SparkTraitsKillerBridge.getItemCooldownRemaining(player, item);
        ENTRIES.computeIfAbsent(player.getUuid(), ignored -> new HashMap<>())
                .put(item, effective > 0 ? effective : ticks);
    }

    /** Halves both weapons' remembered entries; weapons not currently cooling are ignored. / 两把武器各减本次时长的一半；未冷却的忽略。 */
    public static void reduceAfterObservedKill(ServerPlayerEntity savior) {
        Map<Item, Integer> entries = ENTRIES.get(savior.getUuid());
        if (entries == null) {
            return;
        }
        reduce(savior, SparkWitchItems.saviorPistol(), entries);
        reduce(savior, SparkWitchItems.saviorLightsaber(), entries);
    }

    private static void reduce(ServerPlayerEntity savior, Item item, Map<Item, Integer> entries) {
        int remaining = SparkTraitsKillerBridge.getItemCooldownRemaining(savior, item);
        if (remaining <= 0) {
            return;
        }
        int entry = entries.getOrDefault(item, 0);
        if (entry <= 0) {
            return;
        }
        int next = Math.max(0, remaining - entry / 2);
        if (!SparkTraitsKillerBridge.setExactItemCooldownRemaining(savior, item, next)) {
            savior.getItemCooldownManager().set(item, next);
        }
    }

    public static void clear(ServerPlayerEntity player) {
        ENTRIES.remove(player.getUuid());
    }
}
