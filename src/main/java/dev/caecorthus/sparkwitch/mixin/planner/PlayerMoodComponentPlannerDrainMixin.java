package dev.caecorthus.sparkwitch.mixin.planner;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaMoodLock;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRules;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A planner recommendation never drains sanity: Wathe's per-tick drain operand {@code tasks.size()} counts one task
 * fewer while the marked task ({@link PlannerComponent}) is in the map. Why this hook is safe:
 * <ul>
 *   <li>Pinned Wathe 1.5.6 bytecode: in serverTick and clientTick the FIRST {@code Map.size()} is the drain operand
 *   ({@code setMood(mood - tasks.size() * MOOD_DRAIN)}); serverTick's second {@code Map.size()} (next-task timer) is
 *   outside {@code ordinal = 0}. {@code require}/{@code allow} fail loudly if Wathe drifts.</li>
 *   <li>Only {@code @ModifyExpressionValue}, never {@code @Redirect}: MixinExtras chains it with Noa's lock and the
 *   Abyss Listener zone handler on the same read in any order. Only the marked task is subtracted, so the zone's +1
 *   pseudo task survives; for a Noa-locked player it does nothing, since her handler already removes every real task
 *   (subtracting again would cancel the zone's pseudo task). The result never goes below 0.</li>
 *   <li>No {@code setMood} call is added or removed: SparkTraits and SparkStrength still adjust the drain through their
 *   delta-based {@code setMood} argument modifiers, the Bell Echo / Sherry {@code MOOD_DRAIN} scalers still scale the
 *   per-task rate, and the task itself still ticks, completes and fires {@code TaskComplete} like any other task.</li>
 *   <li>Every target uses only JDK types, so {@code remap = false} holds in dev and production.</li>
 * </ul>
 * Both sides read the owner-synced marker against their own task map (the server also requires the exact task object
 * it inserted, so a same-type replacement such as a Bell Ringer Echo task drains normally), so the client prediction
 * matches the server; other players' components are never synced to a client, which then leaves the operand alone.
 * 规划大师的推荐从不消耗理智：被标记的任务（{@link PlannerComponent}）在任务表中时，Wathe 每刻下降的操作数
 * {@code tasks.size()} 少算一个任务。此挂点安全的原因：
 * <ul>
 *   <li>锁定 Wathe 1.5.6 字节码：serverTick 与 clientTick 中第一个 {@code Map.size()} 是下降操作数
 *   （{@code setMood(mood - tasks.size() * MOOD_DRAIN)}）；serverTick 的第二个 {@code Map.size()}（下一任务计时）不在
 *   {@code ordinal = 0} 内。Wathe 变动时 {@code require}/{@code allow} 会直接报错。</li>
 *   <li>只用 {@code @ModifyExpressionValue}，从不 {@code @Redirect}：MixinExtras 会把它与诺亚锁定、聆渊者领域在同一读取上的
 *   处理器按任意顺序串联。只减去被标记的任务，因此领域的 +1 临时任务得以保留；对诺亚锁定的玩家不做任何事，因为她的处理器已
 *   减去全部真实任务（再减一次会抵消领域的临时任务）。结果不会低于 0。</li>
 *   <li>不增删任何 {@code setMood} 调用：SparkTraits 与 SparkStrength 仍通过按差值计算的 {@code setMood} 参数修改调整下降，
 *   敲钟人回响与橘雪莉的 {@code MOOD_DRAIN} 缩放仍作用于单任务速率，该任务本身也照常计时、完成并像其他任务一样触发
 *   {@code TaskComplete}。</li>
 *   <li>所有目标只含 JDK 类型，因此 {@code remap = false} 在开发与生产环境均成立。</li>
 * </ul>
 * 双端都用仅同步给本人的标记对照自己的任务表（服务端还要求是其插入的那个任务对象，因此敲钟人回响任务这类同类型替换照常
 * 消耗理智），因此客户端预测与服务端一致；其他玩家的组件从不同步到客户端，此时操作数保持不变。
 */
@Mixin(value = PlayerMoodComponent.class, remap = false)
public abstract class PlayerMoodComponentPlannerDrainMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    /** Both sides: the recommended task never drains. / 双端：推荐的任务从不使理智下降。 */
    @ModifyExpressionValue(
            method = {"serverTick", "clientTick"},
            at = @At(value = "INVOKE", target = "Ljava/util/Map;size()I", ordinal = 0),
            require = 2,
            allow = 2
    )
    private int sparkwitch$plannerRecommendationNeverDrains(int drainingTasks) {
        if (!PlannerComponent.KEY.get(player).holdsRecommendation(((PlayerMoodComponent) (Object) this).tasks)) {
            return drainingTasks;
        }
        return PlannerRules.drainingTaskCount(drainingTasks, true, NoaMoodLock.locks(player));
    }
}
