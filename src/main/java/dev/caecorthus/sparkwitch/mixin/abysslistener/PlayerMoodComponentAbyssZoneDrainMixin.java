package dev.caecorthus.sparkwitch.mixin.abysslistener;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone.DeepDarkZoneStandingDrain;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Deep Dark Zone drain (owner D7): while the player is exposed, Wathe's per-tick drain
 * {@code if (!tasks.isEmpty()) setMood(mood - tasks.size() * MOOD_DRAIN)} becomes
 * {@code (real tasks + 1) × MOOD_DRAIN × 15}, even with no real task. Why this hook is safe:
 * <ul>
 *   <li>Pinned Wathe 1.5.6 bytecode: in both serverTick and clientTick the FIRST {@code Map.isEmpty()} and the FIRST
 *   {@code Map.size()} are the drain operands, and {@code MOOD_DRAIN} is read once. serverTick's second
 *   {@code Map.size()} (next-task timer) is outside {@code ordinal = 0}; clientTick's {@code HashMap.isEmpty()} has
 *   another owner. {@code require = 2, allow = 2} fail loudly if Wathe drifts.</li>
 *   <li>Only {@code @ModifyExpressionValue}, never {@code @Redirect}: MixinExtras chains it with the Bell Ringer Echo
 *   handler on the same {@code MOOD_DRAIN} read (×15 × 1.5, multiplicative, Bell Ringer mixin untouched) and with
 *   SparkTraits' redirects in this method. No {@code setMood} call is added or removed, so SparkTraits and
 *   SparkStrength still adjust the drain through their {@code setMood} argument modifiers (ordinal 0 stays the drain).</li>
 *   <li>Wathe's REAL-mood gate (serverTick) and {@code setMood} pinning (both sides) still decide who drains; the
 *   Wraith HEAD cancel still skips the whole loop. The task map, task generation and {@code TaskComplete} are never
 *   touched: the pseudo task exists only as these operands and as a client display line.</li>
 *   <li>Every target uses only JDK/Wathe types, so {@code remap = false} holds in dev and production.</li>
 * </ul>
 * Both sides read the owner-synced exposure, so the client prediction matches the server.
 * 深暗领域理智下降（所有者 D7）：玩家处于暴露状态时，Wathe 每刻下降
 * {@code if (!tasks.isEmpty()) setMood(mood - tasks.size() * MOOD_DRAIN)} 变为 {@code (真实任务数 + 1) × MOOD_DRAIN × 15}，
 * 没有真实任务时也会下降。此挂点安全的原因：
 * <ul>
 *   <li>锁定 Wathe 1.5.6 字节码：serverTick 与 clientTick 中第一个 {@code Map.isEmpty()} 与第一个 {@code Map.size()} 都是
 *   下降的操作数，{@code MOOD_DRAIN} 各读取一次。serverTick 的第二个 {@code Map.size()}（下一任务计时）不在 {@code ordinal = 0}
 *   内；clientTick 的 {@code HashMap.isEmpty()} 属于其他类。Wathe 变动时 {@code require = 2, allow = 2} 会直接报错。</li>
 *   <li>只用 {@code @ModifyExpressionValue}，从不 {@code @Redirect}：MixinExtras 会把它与同一 {@code MOOD_DRAIN} 读取上的
 *   敲钟人回响处理器串联（×15 × 1.5 相乘，不改敲钟人 mixin），并与 SparkTraits 在此方法中的重定向共存。没有增删任何
 *   {@code setMood} 调用，因此 SparkTraits 与 SparkStrength 仍通过修改 {@code setMood} 参数调整下降（ordinal 0 仍是下降）。</li>
 *   <li>谁会下降仍由 Wathe 的真实理智判定（serverTick）与 {@code setMood} 固定（双端）决定；冤魂的 HEAD 取消仍跳过整个循环。
 *   从不触碰任务表、任务生成与 {@code TaskComplete}：临时任务只以这些操作数和客户端显示行的形式存在。</li>
 *   <li>所有目标只含 JDK/Wathe 类型，因此 {@code remap = false} 在开发与生产环境均成立。</li>
 * </ul>
 * 双端都读取仅同步给本人的暴露标记，因此客户端预测与服务端一致。
 */
@Mixin(value = PlayerMoodComponent.class, remap = false)
public abstract class PlayerMoodComponentAbyssZoneDrainMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    /** The pseudo task keeps the drain running with no real task. / 没有真实任务时，临时任务也让下降继续。 */
    @ModifyExpressionValue(
            method = {"serverTick", "clientTick"},
            at = @At(value = "INVOKE", target = "Ljava/util/Map;isEmpty()Z", ordinal = 0),
            require = 2,
            allow = 2
    )
    private boolean sparkwitch$abyssZoneKeepsDraining(boolean tasksEmpty) {
        return DeepDarkZoneStandingDrain.noDrainingTasks(tasksEmpty, DeepDarkZoneStandingDrain.isExposed(player));
    }

    /** The pseudo task counts as one more task. / 临时任务计为多一个任务。 */
    @ModifyExpressionValue(
            method = {"serverTick", "clientTick"},
            at = @At(value = "INVOKE", target = "Ljava/util/Map;size()I", ordinal = 0),
            require = 2,
            allow = 2
    )
    private int sparkwitch$abyssZonePseudoTask(int realTasks) {
        return DeepDarkZoneStandingDrain.drainingTaskCount(realTasks, DeepDarkZoneStandingDrain.isExposed(player));
    }

    /** ×15 on the per-task rate, chained after any earlier scaling. / 单任务速率 ×15，串联在其他放大之后。 */
    @ModifyExpressionValue(
            method = {"serverTick", "clientTick"},
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/game/GameConstants;MOOD_DRAIN:F",
                    opcode = Opcodes.GETSTATIC
            ),
            require = 2,
            allow = 2
    )
    private float sparkwitch$abyssZoneDrainRate(float drain) {
        return DeepDarkZoneStandingDrain.drainPerTask(drain, DeepDarkZoneStandingDrain.isExposed(player));
    }
}
