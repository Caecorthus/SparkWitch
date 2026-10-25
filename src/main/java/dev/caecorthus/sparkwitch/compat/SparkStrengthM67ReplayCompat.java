package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackFakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * External seam: a Magician puppet replays a recorded SparkStrength M67 throw through SparkStrength's own server
 * session, run by the puppet's replay proxy (a Fabric fake player carrying the Magician's UUID). SparkStrength keeps
 * every throw rule (round, charge, cooldowns, stun, trait checks, the grenade, its kills and replay record), all read
 * on the proxy, so an under-charged or otherwise refused release still never throws. SparkWitch never compiles against
 * SparkStrength, so this is the only SparkWitch class that names
 * {@code annina.sparkstrength.item.m67.M67UseService}, and only its public static {@code refreshEquipment} and
 * {@code withReleaseAuthorization}, resolved once by reflection after the mod-loaded guard; absent, older or failing
 * SparkStrength leaves the replay as before (the puppet throws nothing). Server thread only.
 * The proxy differs from a real holder in two places, both covered here:
 * (1) SparkStrength counts a hand M67 it has not seen yet as just equipped, with a short equip lock that refuses the
 * use; the recorded use already waited that lock out (a refused use is never recorded), so the replayed use first lets
 * SparkStrength note the proxy's M67 and then drops the lock, like every other cooldown the replay clears;
 * (2) SparkStrength throws only on a release that comes through the vanilla RELEASE_USE_ITEM packet branch; the
 * recorded release is that packet, so it replays inside SparkStrength's own release scope. Any other end of the use
 * (a frame that stopped using) still cancels. SparkStrength also matches the held stack by object, which
 * {@code MagicianReplayFrame} keeps for an unchanged hand M67.
 * 外部接缝：魔术师皮套通过 SparkStrength 自身的服务端会话回放录制的 M67 投掷，由皮套的回放代理（携带魔术师 UUID 的 Fabric
 * 假玩家）执行。所有投掷规则（回合、蓄力、冷却、眩晕、天赋检查、手雷本身及其击杀与回放记录）仍归 SparkStrength，且都读取代理，
 * 因此蓄力不足或被拒的松手仍不会投出。SparkWitch 从不针对 SparkStrength 编译，因此只有本类点名
 * {@code annina.sparkstrength.item.m67.M67UseService}，且只用其 public static {@code refreshEquipment} 与
 * {@code withReleaseAuthorization}，在模组加载守卫之后经反射解析一次；SparkStrength 缺失、过旧或调用失败时回放保持原样
 * （皮套不投掷）。仅限服务端线程。
 * 代理与真实持有者有两处不同，均在此处理：
 * (1) SparkStrength 把尚未见过的手中 M67 视为刚装备，短暂的装备锁会拒绝使用；录制下来的使用已经等过这把锁（被拒的使用不会被录制），
 * 因此回放的使用先让 SparkStrength 记下代理的 M67，再像回放清除的其他冷却一样移除这把锁；
 * (2) SparkStrength 只在经原版 RELEASE_USE_ITEM 数据包分支的松手时投掷；录制的松手正是该数据包，因此在 SparkStrength 自身的
 * 松手作用域内回放。其他结束使用的方式（某帧已停止使用）仍按取消处理。SparkStrength 还按对象匹配手中堆叠，
 * {@code MagicianReplayFrame} 会为未变化的手中 M67 保留同一对象。
 */
public final class SparkStrengthM67ReplayCompat {
    private static final String MOD_ID = "sparkstrength";
    /** The M67 item id, checked before any reflection. / M67 物品 id，在任何反射之前检查。 */
    public static final Identifier M67_ITEM_ID = Identifier.of(MOD_ID, "m67");
    /**
     * Public SparkStrength members this seam reflects; SparkStrength does not declare them a cross-mod contract yet.
     * 本接缝反射的 SparkStrength public 成员；SparkStrength 尚未将其声明为跨模组契约。
     */
    static final String USE_SERVICE = "annina.sparkstrength.item.m67.M67UseService";
    static final String REFRESH_EQUIPMENT = "refreshEquipment";
    static final String RELEASE_SCOPE = "withReleaseAuthorization";

    private static volatile boolean disabled;

    private SparkStrengthM67ReplayCompat() {
    }

    /** Cheap item check, any side. / 廉价的物品判断，双端可用。 */
    public static boolean isM67(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && M67_ITEM_ID.equals(Registries.ITEM.getId(stack.getItem()));
    }

    /**
     * Before the replayed use of {@code stack}: for an M67, SparkStrength notes the proxy's equipment (starting its
     * equip lock), then the lock is dropped. Anything else is untouched.
     * 回放使用 {@code stack} 之前：若为 M67，先让 SparkStrength 记下代理的装备（会开始装备锁），再移除该锁。其他物品不受影响。
     */
    public static void beforeReplayedUse(MagicianPlaybackFakePlayer proxy, ItemStack stack) {
        Method refresh = isM67(stack) ? seam(true) : null;
        if (refresh == null) {
            return;
        }
        try {
            refresh.invoke(null, proxy);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            disabled = true;
            return;
        }
        proxy.getItemCooldownManager().remove(stack.getItem());
    }

    /**
     * The replayed RELEASE_USE_ITEM: inside SparkStrength's release scope while the proxy is using an M67, otherwise
     * (or when the seam fails before the release ran) a plain {@code stopUsingItem()} as before.
     * 回放的 RELEASE_USE_ITEM：代理正在使用 M67 时在 SparkStrength 的松手作用域内执行，否则（或接缝在松手执行前失败时）照旧直接
     * {@code stopUsingItem()}。
     */
    public static void releaseUse(MagicianPlaybackFakePlayer proxy) {
        Method scope = proxy.isUsingItem() && isM67(proxy.getActiveItem()) ? seam(false) : null;
        if (scope != null) {
            try {
                scope.invoke(null, proxy, (Runnable) proxy::stopUsingItem);
                return;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                disabled = true;
                if (!proxy.isUsingItem()) {
                    return;
                }
            }
        }
        proxy.stopUsingItem();
    }

    @Nullable
    private static Method seam(boolean refresh) {
        if (disabled || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return null;
        }
        return refresh ? Seam.REFRESH : Seam.SCOPE;
    }

    /**
     * Both methods resolved once, after the mod-loaded guard (class init is thread-safe); SparkStrength's classes never
     * change at runtime, so a failed lookup latches null for both, and a failed call latches {@link #disabled}.
     * 两个方法在模组加载守卫之后解析一次（类初始化线程安全）；SparkStrength 的类运行期不会改变，因此任一查找失败都锁定两者为 null，
     * 调用失败锁定 {@link #disabled}。
     */
    private static final class Seam {
        private static final Method REFRESH;
        private static final Method SCOPE;

        static {
            Method refresh;
            Method scope;
            try {
                Class<?> service = Class.forName(USE_SERVICE);
                refresh = service.getMethod(REFRESH_EQUIPMENT, ServerPlayerEntity.class);
                scope = service.getMethod(RELEASE_SCOPE, ServerPlayerEntity.class, Runnable.class);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                refresh = null;
                scope = null;
            }
            REFRESH = refresh;
            SCOPE = scope;
        }
    }
}
