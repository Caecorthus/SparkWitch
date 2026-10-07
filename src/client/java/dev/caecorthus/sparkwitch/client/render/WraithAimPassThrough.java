package dev.caecorthus.sparkwitch.client.render;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Lets the local non-spectator player's aimed actions pass through active Wraiths during client-side target selection.
 * 让本地非旁观玩家的瞄准类操作在客户端选靶时穿过激活的冤魂。
 *
 * <p>Wathe's gun/knife selectors only reject spectator/creative players and the vanilla crosshair only rejects
 * spectators. An active Wraith stays in adventure mode, so it is picked first and SparkFactionAPI then cancels the
 * whole action on the server, which means the real target behind is never hit. Only the client selection changes
 * here; the server-side SparkFactionAPI affect policy stays the authority and backstop.
 * Wathe 枪/刀选靶只排除旁观/创造模式玩家，原版准星只排除旁观者；冤魂保持冒险模式会先被选中，随后服务端
 * SparkFactionAPI 整次取消，后面的真实目标永远打不到。这里只改客户端选靶，服务端判定仍是权威兜底。</p>
 *
 * <p>The synced Wraith component is the only reliable remote signal: {@code noellesroles:no_collision}
 * and invisibility also appear on living players. The bound killer keeps aiming at its own active Vendetta,
 * mirroring the server isolation policy's exact-pair exception.
 * 只能用同步的冤魂组件判断远端玩家：no_collision 与隐身也会出现在存活玩家身上。
 * 绑定凶手仍可瞄准自己的仇杀客，与服务端隔离策略的精确配对例外一致。</p>
 */
public final class WraithAimPassThrough {
    // Shooter whose Wathe/NoellesRoles selector is running on this thread. / 当前线程正在选靶的射手。
    private static final ThreadLocal<PlayerEntity> SELECTING_SHOOTER = new ThreadLocal<>();

    private WraithAimPassThrough() {
    }

    /**
     * Runs a target selector with the shooter's pass-through scope open, so a mod replacing the selector's
     * raycast (e.g. SparkTraits Marksman) is covered as well.
     * 在射手的穿透作用域内执行选靶；其他模组替换的选靶射线（如 SparkTraits 精确枪手）同样生效。
     */
    public static <T> T selectAs(PlayerEntity shooter, Supplier<T> selector) {
        PlayerEntity previous = SELECTING_SHOOTER.get();
        SELECTING_SHOOTER.set(shooter);
        try {
            return selector.get();
        } finally {
            if (previous == null) {
                SELECTING_SHOOTER.remove();
            } else {
                SELECTING_SHOOTER.set(previous);
            }
        }
    }

    /** Filters only the scoped shooter's raycast; every other caller keeps its predicate. / 仅过滤作用域内射手的射线。 */
    public static Predicate<Entity> filterScopedSelection(Entity source, Predicate<Entity> predicate) {
        PlayerEntity shooter = SELECTING_SHOOTER.get();
        if (shooter == null || shooter != source) {
            return predicate;
        }
        return candidate -> predicate.test(candidate) && !passesThrough(shooter, candidate);
    }

    /**
     * Filters the vanilla crosshair for the local non-spectator player; spectators keep click-to-spectate.
     * 仅对本地非旁观玩家过滤原版准星；旁观者保留点击附身。
     */
    public static Predicate<Entity> filterCrosshair(Entity camera, Predicate<Entity> predicate) {
        if (!(camera instanceof PlayerEntity viewer)
                || viewer.isSpectator()
                || viewer != MinecraftClient.getInstance().player) {
            return predicate;
        }
        return candidate -> predicate.test(candidate) && !passesThrough(viewer, candidate);
    }

    /**
     * Drops a sleeping active Wraith from Wathe's bed-hit fallback, so a shot or stab at its bed becomes an
     * ordinary miss on the local client. Server-side callers are left untouched.
     * 从 Wathe 床铺命中回退中去掉正在睡觉的激活冤魂，使本地客户端对其床铺的射击或刀刺成为普通落空；服务端调用不受影响。
     */
    public static Optional<PlayerEntity> filterSleepingTarget(World world, Optional<PlayerEntity> sleeper) {
        if (world == null || !world.isClient || sleeper == null || sleeper.isEmpty()) {
            return sleeper;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || viewer.isSpectator() || !passesThrough(viewer, sleeper.get())) {
            return sleeper;
        }
        return Optional.empty();
    }

    /**
     * Mirrors the server isolation policy: another player's active Wraith is never an aim target,
     * except the Vendetta viewed by its own bound killer.
     * 与服务端隔离策略一致：他人的激活冤魂不作为瞄准目标，绑定凶手看自己的仇杀客除外。
     */
    static boolean passesThrough(PlayerEntity viewer, Entity candidate) {
        return candidate instanceof PlayerEntity target
                && target != viewer
                && WraithClientState.isActive(target)
                && !VendettaClientPresentation.isBoundKillerViewingVendetta(viewer, target);
    }
}
