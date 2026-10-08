package dev.caecorthus.sparkwitch.client.mixin.potiongunner;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionScopeClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Two-handed launcher pose for every rendered player (plan: carried in both hands, the art is fitted to it): the
 * hand whose shown stack is the launcher answers {@code CROSSBOW_HOLD}, which the biped model applies to both arms.
 * <p>
 * The launcher's use action is NONE, so every vanilla {@code getArmPose} call for it (hip or scoped) ends at the
 * final {@code return ITEM}. Wathe ({@code wathe$customArmPose}, a bat → CROSSBOW_CHARGE, priority 1000) and
 * NoellesRoles ({@code noellesroles$jesterMomentArmPose}, every main hand → CROSSBOW_CHARGE while the viewer's Jester
 * Moment runs, priority 1500) both put a cancellable {@code @Inject} at that TAIL. A
 * {@code @ModifyReturnValue(RETURN)} is not woven at that return (USEC rifle precedent, exported class 2026-10-08), so
 * the old hook never fired for the launcher. Hence:
 * <ul>
 * <li>{@code @WrapMethod} at mixin priority 1600. MixinExtras nests method wrappers by priority (higher = outer):
 * {@code BlindGatePlayerEntityRendererMixin} (2000, Blind viewers get EMPTY, still wins) → this wrapper → the
 * method body with Wathe's and NoellesRoles' TAIL injects.</li>
 * <li>Only vanilla's plain {@code ITEM} answer becomes {@code CROSSBOW_HOLD}; an inner pose is kept (owner,
 * 2026-10-08: the Jester Moment wins, because NoellesRoles then draws a bat in that hand, so the gunner looks like
 * everyone else).</li>
 * <li>The shown stack is still the one Wathe renders (main hand of a visible player: a note shows empty, a psychotic
 * viewer sees the psychosis item). A {@code @ModifyVariable} at the STORE of vanilla's only {@code ItemStack} local
 * reads the value after every expression handler on the single {@code getStackInHand} call, Wathe's substitution
 * included, whatever the mixin priorities; an expression hook would sit inside Wathe's. So a viewer shown a fake
 * item never sees the launcher pose, and a fake launcher gets the pose its picture needs. A {@code @Share} flag
 * carries the verdict out to the wrapper.</li>
 * </ul>
 * <p>
 * 所有被渲染玩家的双手持炮姿势（计划：双手扛炮，贴图按此姿势制作）：显示物品为炮筒的那只手返回 {@code CROSSBOW_HOLD}，
 * 由双足模型应用到双臂。
 * <p>
 * 炮筒的使用动作为 NONE，因此原版 {@code getArmPose} 对它的每次调用（腰射或开镜）都走到最后的 {@code return ITEM}。
 * Wathe（{@code wathe$customArmPose}，球棒 → CROSSBOW_CHARGE，优先级 1000）与 NoellesRoles
 * （{@code noellesroles$jesterMomentArmPose}，观察者的小丑时刻期间所有主手 → CROSSBOW_CHARGE，优先级 1500）都在该
 * TAIL 处放了可取消的 {@code @Inject}。{@code @ModifyReturnValue(RETURN)} 织不进这一处返回（参照 USEC 步枪，
 * 2026-10-08 导出的类），所以旧钩子对炮筒从未生效。因此：
 * <ul>
 * <li>改用混入优先级 1600 的 {@code @WrapMethod}。MixinExtras 按优先级嵌套方法包装（越高越外层）：
 * {@code BlindGatePlayerEntityRendererMixin}（2000，盲人观察者得到 EMPTY，仍然优先）→ 本包装 → 带 Wathe 与
 * NoellesRoles TAIL 注入的方法体。</li>
 * <li>只有原版的普通 {@code ITEM} 结果改为 {@code CROSSBOW_HOLD}；内层给出的姿势保持不变（所有者 2026-10-08：
 * 小丑时刻优先，因为此时 NoellesRoles 在该手绘制球棒，药炮手看起来与其他人一样）。</li>
 * <li>显示物品仍是 Wathe 渲染的那一个（可见玩家的主手：便签显示为空，精神错乱的观察者看到幻觉物品）。在原版唯一的
 * {@code ItemStack} 局部变量 STORE 处的 {@code @ModifyVariable} 读取的是唯一一次 {@code getStackInHand} 调用上所有
 * 表达式处理器（含 Wathe 的替换）之后的值，与混入优先级无关；表达式钩子会落在 Wathe 的里层。因此看到假物品的观察者
 * 永远看不到持炮姿势，而幻觉中的炮筒也会得到与画面一致的姿势。一个 {@code @Share} 标记把判断结果带给外层包装。</li>
 * </ul>
 */
@Mixin(value = PlayerEntityRenderer.class, priority = 1600)
public abstract class PotionLauncherArmPoseMixin {
    @ModifyVariable(method = "getArmPose", at = @At("STORE"))
    private static ItemStack sparkwitch$noteShownLauncher(ItemStack shown,
                                                         @Share("launcherShown") LocalBooleanRef launcherShown) {
        launcherShown.set(PotionScopeClient.isLauncher(shown));
        return shown;
    }

    @WrapMethod(method = "getArmPose(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/client/render/entity/model/BipedEntityModel$ArmPose;")
    private static BipedEntityModel.ArmPose sparkwitch$holdLauncherWithBothHands(
            AbstractClientPlayerEntity player, Hand hand, Operation<BipedEntityModel.ArmPose> original,
            @Share("launcherShown") LocalBooleanRef launcherShown) {
        BipedEntityModel.ArmPose pose = original.call(player, hand);
        return launcherShown.get() && pose == BipedEntityModel.ArmPose.ITEM
                ? BipedEntityModel.ArmPose.CROSSBOW_HOLD : pose;
    }
}
