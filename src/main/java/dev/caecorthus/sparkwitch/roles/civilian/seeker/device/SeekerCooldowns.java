package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCooldownReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Frozen contract: the single "max + exact" writer of the {@code seeker_car} item cooldown. A longer existing cooldown
 * is kept; SparkTraits' exact write is used first and the vanilla set only when it returns false (never both).
 * Only {@code SeekerStatusComponent#apply} calls {@link #writeFloorExact}; it is never written anywhere else.
 * 冻结契约：{@code seeker_car} 物品冷却“取大 + 精确”的唯一写入方。保留更长的既有冷却；
 * 优先使用 SparkTraits 精确写入，仅当其返回 false 时才回退原版写入（绝不写两次）。
 * 只有 {@code SeekerStatusComponent#apply} 调用 {@link #writeFloorExact}，其他地方从不写入。
 */
public final class SeekerCooldowns {
    private SeekerCooldowns() {
    }

    /**
     * Server only. {@code reason} is informational: the component stores it for the HUD label; the item cooldown
     * itself is authoritative.
     * 仅服务端。{@code reason} 仅供参考：组件保存它用作 HUD 标签；物品冷却本身才是权威。
     *
     * @return true when a new value was written. / 写入了新值时返回 true。
     */
    public static boolean writeFloorExact(ServerPlayerEntity player, int ticks, SeekerCooldownReason reason) {
        if (player == null || ticks <= 0) {
            return false;
        }
        Item car = SparkWitchItems.seekerCar();
        // SparkTraits bypasses Fast Hands itself and performs the vanilla write; only on false do we write vanilla.
        // SparkTraits 自行绕过快手并完成原版写入；仅当其返回 false 时才由我们写入原版冷却。
        return write(remainingTicks(player), ticks,
                merged -> SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, car, merged),
                merged -> player.getItemCooldownManager().set(car, merged));
    }

    /**
     * Pure write rule: nothing happens unless the merged value ({@link SeekerRules#mergeCooldownTicks}) is longer than
     * what already runs; then exactly one writer runs: the exact one, or the vanilla one only when the exact one
     * reports false.
     * 纯写入规则：只有取大后的值长于当前剩余时间才写入；随后恰好执行一个写入方：精确写入，或仅当其返回 false 时的原版写入。
     */
    static boolean write(int existingTicks, int requestedTicks, IntPredicate exactWriter, IntConsumer vanillaWriter) {
        int existing = Math.max(0, existingTicks);
        int merged = SeekerRules.mergeCooldownTicks(existing, requestedTicks);
        if (merged <= existing) {
            return false;
        }
        if (!exactWriter.test(merged)) {
            vanillaWriter.accept(merged);
        }
        return true;
    }

    /**
     * Remaining ticks on the car item cooldown; 0 when none. Side-neutral: the client reads its synced vanilla cooldown
     * (HUD, console), the server its authoritative one.
     * 小车物品冷却的剩余刻数；无冷却时为 0。两端通用：客户端读取已同步的原版冷却（HUD、控制台），服务端读取权威冷却。
     */
    public static int remainingTicks(PlayerEntity player) {
        if (player == null) {
            return 0;
        }
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (!(manager instanceof ItemCooldownManagerAccessor accessor)) {
            return 0;
        }
        Object entry = accessor.sparkwitch$getEntries().get(SparkWitchItems.seekerCar());
        if (!(entry instanceof ItemCooldownEntryAccessor cooldown)) {
            return 0;
        }
        return remaining(cooldown.sparkwitch$getEndTick(), accessor.sparkwitch$getTick());
    }

    static int remaining(int endTick, int currentTick) {
        return Math.max(0, endTick - currentTick);
    }
}
