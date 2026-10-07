package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsUsecBridge;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Frozen contract: the single "max + exact" writer of the {@code usec_rifle} item cooldown (round-start lock and bolt
 * cycle). A longer running cooldown (round start, Saint Karma, Fiend aura) is never shortened; SparkTraits' exact write
 * bypasses Fast Hands and the vanilla set runs only when it returns false. Nothing else writes the rifle cooldown.
 * 冻结契约：{@code usec_rifle} 物品冷却（开局锁定与拉栓）“取大 + 精确”的唯一写入方。绝不缩短正在运行的更长冷却
 * （开局、圣徒业报、魔人光环）；SparkTraits 精确写入可绕过快手，仅当其返回 false 时才执行原版写入。其他地方不写狙击枪冷却。
 */
public final class UsecCooldowns {
    private UsecCooldowns() {
    }

    /** Server only: the 60 s round-start lock. / 仅服务端：开局 60 秒锁定。 */
    public static boolean roundStart(ServerPlayerEntity player) {
        return writeFloorExact(player, UsecRules.ROUND_START_COOLDOWN_TICKS);
    }

    /**
     * Server only: the bolt cycle after a shot, a magazine change into an empty chamber, or a single round chambered.
     * SparkTraits Fast Reload shortens it ×0.7.
     * 仅服务端：开枪后、空膛换弹匣后或单发压膛后的拉栓。SparkTraits 快速装填使其 ×0.7。
     */
    public static boolean bolt(ServerPlayerEntity player) {
        return writeFloorExact(player, boltTicks(SparkTraitsUsecBridge.hasFastReload(player)));
    }

    /** Bolt ticks with or without Fast Reload (40 or 28). / 有无快速装填时的拉栓刻数（40 或 28）。 */
    public static int boltTicks(boolean fastReload) {
        return fastReload
                ? (int) Math.round(UsecRules.BOLT_TICKS * UsecRules.FAST_RELOAD_BOLT_FACTOR)
                : UsecRules.BOLT_TICKS;
    }

    /**
     * Server only. Writes {@code max(existing, ticks)} exactly once; returns true when a new value was written.
     * 仅服务端。恰好写入一次 {@code max(既有, ticks)}；写入了新值时返回 true。
     */
    public static boolean writeFloorExact(ServerPlayerEntity player, int ticks) {
        if (player == null || ticks <= 0) {
            return false;
        }
        Item rifle = SparkWitchItems.usecRifle();
        return write(remainingTicks(player), ticks,
                merged -> SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, rifle, merged),
                merged -> player.getItemCooldownManager().set(rifle, merged));
    }

    /** Pure write rule (unit-testable). / 纯写入规则（可单测）。 */
    static boolean write(int existingTicks, int requestedTicks, IntPredicate exactWriter, IntConsumer vanillaWriter) {
        int existing = Math.max(0, existingTicks);
        int merged = Math.max(existing, Math.max(0, requestedTicks));
        if (merged <= existing) {
            return false;
        }
        if (!exactWriter.test(merged)) {
            vanillaWriter.accept(merged);
        }
        return true;
    }

    /**
     * Remaining ticks on the rifle item cooldown; 0 when none. Side-neutral (the client reads its synced cooldown for
     * the HUD bolt indicator).
     * 狙击枪物品冷却剩余刻数；无冷却时为 0。两端通用（客户端读取已同步的冷却用于 HUD 拉栓指示）。
     */
    public static int remainingTicks(PlayerEntity player) {
        if (player == null) {
            return 0;
        }
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (!(manager instanceof ItemCooldownManagerAccessor accessor)) {
            return 0;
        }
        Object entry = accessor.sparkwitch$getEntries().get(SparkWitchItems.usecRifle());
        if (!(entry instanceof ItemCooldownEntryAccessor cooldown)) {
            return 0;
        }
        return Math.max(0, cooldown.sparkwitch$getEndTick() - accessor.sparkwitch$getTick());
    }
}
