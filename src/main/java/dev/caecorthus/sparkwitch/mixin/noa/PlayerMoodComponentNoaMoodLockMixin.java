package dev.caecorthus.sparkwitch.mixin.noa;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaMoodLock;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Noa's sanity lock ({@link NoaMoodLock}): zeroes only Wathe's OWN operands inside its mood loop, so no {@code setMood}
 * call is added, removed or replaced. Why this hook is safe:
 * <ul>
 *   <li>Pinned Wathe 1.5.6 bytecode: in serverTick and clientTick the FIRST {@code Map.size()} is the drain operand
 *   ({@code setMood(mood - tasks.size() * MOOD_DRAIN)}); serverTick's second {@code Map.size()} (next-task timer) is
 *   outside {@code ordinal = 0}. serverTick has exactly one {@code 0.5F} constant, the task-completion
 *   {@code setMood(mood + 0.5F)} (clientTick's psychosis {@code 0.5F} is outside the target). {@code require}/{@code allow}
 *   fail loudly if Wathe drifts.</li>
 *   <li>Only {@code @ModifyExpressionValue}, never {@code @Redirect}: MixinExtras chains the {@code Map.size()} handler
 *   with the Abyss Listener zone handler on the same read in either order (only the real task count is subtracted, so
 *   the zone's +1 pseudo task survives), and nothing here touches SparkTraits' {@code getMoodType}/{@code clamp}/
 *   {@code killPlayer} redirects, the Bell Echo / Perfumer {@code MOOD_DRAIN} scalers, or the {@code isEmpty()} gate.</li>
 *   <li>The SparkTraits and SparkStrength {@code setMood} argument modifiers (ordinal 0 stays the drain, ordinal 1 the
 *   gain) receive {@code proposed == mood} for a Noa; all of them are delta-based, so they add nothing. Every other
 *   {@code setMood} caller is outside these two operands and still lands; Wathe's REAL gate and its {@code mood <= -1}
 *   breakdown kill are untouched, and the Wraith HEAD cancel still skips the whole loop.</li>
 *   <li>Non-Noa players get the original operand back unchanged. Every target uses only JDK types or a constant, so
 *   {@code remap = false} holds in dev and production.</li>
 * </ul>
 * Both sides read Wathe's synced role map, so the client drain prediction matches the locked server value.
 * 诺亚理智锁定（{@link NoaMoodLock}）：只把 Wathe 情绪循环内它自身的操作数清零，不增删或替换任何 {@code setMood} 调用。
 * 此挂点安全的原因：
 * <ul>
 *   <li>锁定 Wathe 1.5.6 字节码：serverTick 与 clientTick 中第一个 {@code Map.size()} 是下降操作数
 *   （{@code setMood(mood - tasks.size() * MOOD_DRAIN)}）；serverTick 的第二个 {@code Map.size()}（下一任务计时）不在
 *   {@code ordinal = 0} 内。serverTick 恰有一个 {@code 0.5F} 常量，即完成任务的 {@code setMood(mood + 0.5F)}
 *   （clientTick 中幻觉用的 {@code 0.5F} 不在目标内）。Wathe 变动时 {@code require}/{@code allow} 会直接报错。</li>
 *   <li>只用 {@code @ModifyExpressionValue}，从不 {@code @Redirect}：MixinExtras 会把 {@code Map.size()} 处理器与同一读取上的
 *   聆渊者领域处理器按任意顺序串联（只减去真实任务数，领域的 +1 临时任务得以保留）；不触碰 SparkTraits 的
 *   {@code getMoodType}/{@code clamp}/{@code killPlayer} 重定向、敲钟人回响与调香师的 {@code MOOD_DRAIN} 缩放，以及
 *   {@code isEmpty()} 判定。</li>
 *   <li>SparkTraits 与 SparkStrength 对 {@code setMood} 参数的修改（ordinal 0 仍是下降，ordinal 1 仍是回复）对诺亚收到
 *   {@code proposed == mood}；它们都只按差值计算，因此不会额外改变。其他所有 {@code setMood} 调用方都不经过这两个操作数，
 *   照常生效；Wathe 的真实理智判定与 {@code mood <= -1} 精神崩溃击杀不变，冤魂的 HEAD 取消仍跳过整个循环。</li>
 *   <li>非诺亚玩家原样拿回操作数。所有目标只含 JDK 类型或常量，因此 {@code remap = false} 在开发与生产环境均成立。</li>
 * </ul>
 * 双端都读取 Wathe 已同步的职业表，因此客户端的下降预测与被锁定的服务端数值一致。
 */
@Mixin(value = PlayerMoodComponent.class, remap = false)
public abstract class PlayerMoodComponentNoaMoodLockMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    /**
     * Both sides: Noa's own pending tasks never drain her. / 双端：诺亚自己的待办任务从不使她理智下降。
     */
    @ModifyExpressionValue(
            method = {"serverTick", "clientTick"},
            at = @At(value = "INVOKE", target = "Ljava/util/Map;size()I", ordinal = 0),
            require = 2,
            allow = 2
    )
    private int sparkwitch$noaTasksNeverDrain(int drainingTasks) {
        return NoaMoodLock.drainingTaskCount(
                drainingTasks,
                ((PlayerMoodComponent) (Object) this).tasks.size(),
                NoaMoodLock.locks(player)
        );
    }

    /**
     * Server authority: a completed task never restores Noa's sanity; the task still completes and pays.
     * 服务端权威：完成任务从不回复诺亚的理智；任务照常完成并发放金币。
     */
    @ModifyExpressionValue(
            method = "serverTick",
            at = @At(value = "CONSTANT", args = "floatValue=0.5"),
            require = 1,
            allow = 1
    )
    private float sparkwitch$noaTasksNeverRestore(float gain) {
        return NoaMoodLock.taskCompletionGain(gain, NoaMoodLock.locks(player));
    }
}
