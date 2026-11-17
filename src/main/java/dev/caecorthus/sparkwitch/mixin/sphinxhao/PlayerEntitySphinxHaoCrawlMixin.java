package dev.caecorthus.sparkwitch.mixin.sphinxhao;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoCrawlPose;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 豪气冲天's forced crawl (陆泳, rules in {@link SphinxHaoCrawlPose}), common config so it runs on the server, the
 * crawler's own client and every observer's client alike (vanilla target, remap = true).
 * <ul>
 *   <li>{@code updatePose} HEAD: vanilla recomputes every player's pose each tick on every side
 *   ({@code PlayerEntity.tick}); a forced crawler gets {@link EntityPose#SWIMMING} out of water, which is vanilla's own
 *   crawl: a 0.6-high hitbox, and {@code ClientPlayerEntity.shouldSlowDown} ({@code isCrawling}) scales the input to
 *   sneak speed, so the local crawler moves at crawl speed and {@code canStartSprinting} (forward input >= 0.8) cannot
 *   start a sprint. The rest of the method is skipped only while forced, so the pose returns to vanilla's choice on
 *   the first tick after the window.</li>
 *   <li>{@code tickMovement} HEAD: a sprint already running when the window opens would keep going (vanilla does not
 *   stop a sprint for crawling), so it is cleared on both sides, like {@code SeekerSprintLockMixin}.</li>
 *   <li>{@code jump} HEAD: no hopping while land-swimming (owner brief: no sprint and no jump while crawling).</li>
 * </ul>
 * Movement is client-authoritative for players, so the crawler's own client is what enforces speed and jumps; the
 * server's copy keeps the synced pose and hitbox. Players outside a window never take a branch.
 * 豪气冲天的强制陆泳（规则见 {@link SphinxHaoCrawlPose}），位于通用配置，因此服务端、陆泳者本人的客户端与所有观察者的客户端都会
 * 运行（原版目标，remap = true）。
 * <ul>
 *   <li>{@code updatePose} HEAD：原版在各端每刻为每名玩家重新计算姿势（{@code PlayerEntity.tick}）；被强制的陆泳者在水外得到
 *   {@link EntityPose#SWIMMING}，即原版自身的爬行：碰撞箱高 0.6，{@code ClientPlayerEntity.shouldSlowDown}
 *   （{@code isCrawling}）把输入缩放到潜行速度，因此本地陆泳者以爬行速度移动，{@code canStartSprinting}（前进输入 >= 0.8）
 *   无法开始疾跑。只有被强制时才跳过方法其余部分，窗口结束后的第一刻即恢复原版姿势选择。</li>
 *   <li>{@code tickMovement} HEAD：窗口开启时已在进行的疾跑会持续（原版不会因爬行停止疾跑），因此两端都清除，同
 *   {@code SeekerSprintLockMixin}。</li>
 *   <li>{@code jump} HEAD：陆泳时不能跳（协调者要求：爬行时不能疾跑也不能跳）。</li>
 * </ul>
 * 玩家移动由客户端权威，因此由陆泳者本人的客户端负责速度与跳跃；服务端副本保持同步的姿势与碰撞箱。窗口之外的玩家不会进入任何
 * 分支。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntitySphinxHaoCrawlMixin {
    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$forceSphinxHaoCrawl(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (SphinxHaoCrawlPose.forcesCrawl(self)) {
            self.setPose(EntityPose.SWIMMING);
            ci.cancel();
        }
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void sparkwitch$clearSphinxHaoCrawlSprint(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.isSprinting() && SphinxHaoCrawlPose.forcesCrawl(self)) {
            self.setSprinting(false);
        }
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockSphinxHaoCrawlJump(CallbackInfo ci) {
        if (SphinxHaoCrawlPose.forcesCrawl((PlayerEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
