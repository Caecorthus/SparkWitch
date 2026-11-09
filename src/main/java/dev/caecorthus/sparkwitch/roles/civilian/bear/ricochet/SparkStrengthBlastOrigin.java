package dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Optional SparkStrength seam (D3 step 2): the centre of the SparkStrength-scoped blast whose kills run on this thread
 * ({@code annina.sparkstrength.role.bodyguard.AttackOriginScope#currentBlast()}, which SparkStrength pushes around the
 * Wathe grenade, M67 and Bomber drone kill loops for its Democracy Shield). SparkWitch never compiles against
 * SparkStrength, so this is reached by reflection on the server thread. Absent, older or failing SparkStrength answers
 * null: the blast's origin is then unknown, which counts as "not front" (fail closed toward the 25% roll).
 * 可选的 SparkStrength 接缝（D3 第 2 步）：本线程正在结算其击杀的、由 SparkStrength 标注的爆炸中心
 * （{@code annina.sparkstrength.role.bodyguard.AttackOriginScope#currentBlast()}，SparkStrength 为民主盾牌把它包在 Wathe 手雷、
 * M67 与炸弹客无人机的击杀循环外）。SparkWitch 从不针对 SparkStrength 编译，因此在服务端线程经反射读取。SparkStrength 缺失、
 * 过旧或调用失败时返回 null：该爆炸的来源即为未知，视为“非正面”（失败时偏向 25% 掷骰）。
 */
final class SparkStrengthBlastOrigin {
    private static final String MOD_ID = "sparkstrength";
    /** Frozen SparkStrength seam. / 冻结的 SparkStrength 接缝。 */
    static final String ATTACK_ORIGIN_SCOPE = "annina.sparkstrength.role.bodyguard.AttackOriginScope";
    static final String CURRENT_BLAST = "currentBlast";

    private SparkStrengthBlastOrigin() {
    }

    static @Nullable Vec3d current() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return null;
        }
        Method currentBlast = Holder.CURRENT_BLAST_METHOD;
        if (currentBlast == null) {
            return null;
        }
        try {
            return currentBlast.invoke(null) instanceof Vec3d centre ? centre : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Fail closed: unknown origin. / 失败即关闭：来源未知。
            return null;
        }
    }

    /** Resolved once after the mod-loaded guard; a failed lookup latches null. / 在模组加载守卫后解析一次；失败锁定为 null。 */
    private static final class Holder {
        private static final Method CURRENT_BLAST_METHOD = resolve();

        private static Method resolve() {
            try {
                Method method = Class.forName(ATTACK_ORIGIN_SCOPE).getMethod(CURRENT_BLAST);
                return Vec3d.class.isAssignableFrom(method.getReturnType()) ? method : null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                return null;
            }
        }
    }
}
