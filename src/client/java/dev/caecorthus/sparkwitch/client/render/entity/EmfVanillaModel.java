package dev.caecorthus.sparkwitch.client.render.entity;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Entity render protocol, part 4 (2026-10-25): an optional bridge to Entity Model Features'
 * {@code EMFAnimationApi#registerVanillaModelCondition}. While a condition registered here holds for an entity, EMF
 * draws it with its vanilla model and vanilla pose (no pack geometry, no pack animation), so a pose an effect sets
 * through vanilla inputs is what gets drawn. Animation packs read some state straight from the entity, whatever the
 * vanilla pose: Fresh Animations raises the arm of anyone whose hand holds an item, which showed through the Blind's
 * empty arm pose in the line art (harness, 2026-10-25). EMF asks the conditions when it mounts an entity's render state,
 * on the render thread. Reflective, with no compile dependency: without EMF, or when its API cannot be found, nothing
 * is registered and rendering is unchanged. A condition that throws counts as true (the vanilla model never leaks a
 * pack pose), and no exception reaches EMF, whose loop would then skip other mods' conditions.
 * 实体渲染协议第 4 部分（2026-10-25）：到 Entity Model Features {@code EMFAnimationApi#registerVanillaModelCondition} 的可选
 * 桥接。在此注册的条件对某实体成立期间，EMF 以原版模型与原版姿势绘制它（无资源包几何、无资源包动画），效果通过原版输入设置
 * 的姿势即为最终画面。动画包会直接从实体读取部分状态而无视原版姿势：Fresh Animations 会抬起任何手持物品者的手臂，这在
 * 盲人线稿中穿透了空手姿势（测试工具，2026-10-25）。EMF 在挂载实体渲染状态时于渲染线程询问这些条件。反射实现，无编译依赖：
 * 未安装 EMF 或找不到其 API 时不注册任何内容，渲染不变。抛出异常的条件按成立处理（原版模型从不泄露资源包姿势），且异常
 * 不会传到 EMF，否则其循环会跳过其他模组的条件。
 */
public final class EmfVanillaModel {
    static final String EMF_MOD_ID = "entity_model_features";
    static final String EMF_API = "traben.entity_model_features.EMFAnimationApi";
    private static final List<Predicate<Entity>> CONDITIONS = new CopyOnWriteArrayList<>();
    private static boolean installed;

    private EmfVanillaModel() {
    }

    /**
     * Draws an entity with EMF's vanilla model while {@code condition} holds for it. Client init only.
     * 在 {@code condition} 对实体成立期间，以 EMF 的原版模型绘制它。仅在客户端初始化时调用。
     */
    public static synchronized void when(Predicate<Entity> condition) {
        CONDITIONS.add(Objects.requireNonNull(condition, "condition"));
        if (!installed) {
            installed = true;
            if (FabricLoader.getInstance().isModLoaded(EMF_MOD_ID)) {
                install();
            }
        }
    }

    /** EMF's callback; {@code emfEntity} is the entity being mounted. / EMF 的回调；参数为正在挂载的实体。 */
    static boolean forcesVanilla(Object emfEntity) {
        return emfEntity instanceof Entity entity && anyHolds(CONDITIONS, entity);
    }

    /**
     * Pure rule: true when a condition holds or throws.
     * 纯规则：任一条件成立或抛出异常时为真。
     */
    static <T> boolean anyHolds(List<? extends Predicate<? super T>> conditions, T value) {
        for (Predicate<? super T> condition : conditions) {
            try {
                if (condition.test(value)) {
                    return true;
                }
            } catch (RuntimeException exception) {
                return true;
            }
        }
        return false;
    }

    private static void install() {
        try {
            Method register = Class.forName(EMF_API, true, EmfVanillaModel.class.getClassLoader())
                    .getMethod("registerVanillaModelCondition", Function.class);
            Function<Object, Boolean> condition = EmfVanillaModel::forcesVanilla;
            register.invoke(null, condition);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            SparkWitch.LOGGER.warn("Entity Model Features is loaded but its vanilla-model API is unavailable ({}); "
                    + "animation packs keep posing entities that SparkWitch draws with vanilla poses", exception.toString());
        }
    }
}
