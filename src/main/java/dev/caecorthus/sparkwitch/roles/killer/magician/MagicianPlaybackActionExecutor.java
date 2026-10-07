package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheParticles;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

/**
 * 播放动作执行层。第一层只实现 Wathe 原版匕首、左轮和普通交互；
 * 第二层通过 {@link MagicianPlaybackActionAdapters} 让扩展职业接入自己的武器。
 */
public final class MagicianPlaybackActionExecutor {
    private MagicianPlaybackActionExecutor() {}
    public static void execute(ServerPlayerEntity owner, MagicianPlaybackFakePlayer proxy, MagicianPlaybackEntity visible, MagicianRecordedAction action) {
        ItemStack stack = proxy.getMainHandStack();
        // 皮套回放是录制好的独立时间线，不应继承真实玩家/上一动作留下的物品冷却。
        // 每条动作执行前清掉代理冷却，真实玩家和其他实体的冷却完全不受影响。
        if (!stack.isEmpty()) proxy.getItemCooldownManager().remove(stack.getItem());
        MagicianPlaybackActionAdapter adapter = MagicianPlaybackActionAdapters.find(stack);
        if (adapter != null && action.type() != MagicianRecordedAction.Type.SELECT_SLOT) {
            adapter.execute(owner, proxy, visible, stack.copy());
            return;
        }
        switch (action.type()) {
            case ATTACK -> attack(owner, proxy, visible);
            case USE_MAIN_HAND -> use(proxy, visible, Hand.MAIN_HAND, action.blockHit());
            case USE_OFF_HAND -> use(proxy, visible, Hand.OFF_HAND, action.blockHit());
            case RELEASE_USE_ITEM -> release(owner, proxy, visible);
            case SWING_MAIN_HAND -> visible.playReplaySwing(Hand.MAIN_HAND);
            case SWING_OFF_HAND -> visible.playReplaySwing(Hand.OFF_HAND);
            case SELECT_SLOT -> proxy.getInventory().selectedSlot = Math.max(0, Math.min(8, action.intValue()));
            case GUN_SHOOT -> shoot(owner, proxy, visible);
            case KNIFE_STAB -> knife(owner, proxy, visible);
        }
    }
    private static void attack(ServerPlayerEntity owner, MagicianPlaybackFakePlayer proxy, MagicianPlaybackEntity visible) {
        EntityHitResult hit = target(proxy, MagicianConstants.KNIFE_RANGE);
        if (hit == null || !(hit.getEntity() instanceof ServerPlayerEntity target) || !GameFunctions.isPlayerAliveAndSurvival(target)) return;
        if (proxy.getMainHandStack().isOf(WatheItems.KNIFE)) { knife(owner, proxy, visible); return; }
        if (proxy.getMainHandStack().isOf(WatheItems.BAT)) {
            // FakePlayer 不一定会完整经过 Wathe 的 PlayerEntity.attack mixin，球棒必须在回放层显式复刻。
            GameFunctions.killPlayer(target, true, owner, GameConstants.DeathReasons.BAT);
            proxy.getWorld().playSound(null, target.getX(), target.getEyeY(), target.getZ(),
                    WatheSounds.ITEM_BAT_HIT, SoundCategory.PLAYERS, 3.0F, 1.0F);
            proxy.swingHand(Hand.MAIN_HAND);
            visible.playReplaySwing(Hand.MAIN_HAND);
            return;
        }
        // 普通左键必须继续走 Minecraft 原版伤害链，不能把空手挥击错误变成瞬杀。
        proxy.attack(target);
        proxy.swingHand(Hand.MAIN_HAND); visible.playReplaySwing(Hand.MAIN_HAND);
    }
    private static void use(MagicianPlaybackFakePlayer proxy, MagicianPlaybackEntity visible, Hand hand, @Nullable BlockHitResult recorded) {
        ItemStack stack = proxy.getStackInHand(hand);
        if (recorded != null) {
            ActionResult result = proxy.interactionManager.interactBlock(proxy, proxy.getWorld(), stack, hand, recorded);
            if (result.isAccepted()) { visible.playReplaySwing(hand); return; }
        }
        if (stack.isOf(WatheItems.REVOLVER)) return;
        ActionResult result = proxy.interactionManager.interactItem(proxy, proxy.getWorld(), stack, hand);
        if (result.isAccepted() && !proxy.isUsingItem()) visible.playReplaySwing(hand);
    }
    private static void release(ServerPlayerEntity owner, MagicianPlaybackFakePlayer proxy, MagicianPlaybackEntity visible) {
        ItemStack stack = proxy.getActiveItem().copy();
        int used = stack.isEmpty() ? 0 : stack.getMaxUseTime(proxy) - proxy.getItemUseTimeLeft();
        proxy.stopUsingItem(); visible.setReplayUseState(false, null); visible.setReplayItemUseTimeLeft(0);
        if (stack.isOf(WatheItems.KNIFE) && used >= 10) knife(owner, proxy, visible);
    }
    private static void shoot(ServerPlayerEntity owner, MagicianPlaybackFakePlayer proxy, MagicianPlaybackEntity visible) {
        ItemStack stack = proxy.getMainHandStack();
        if (!stack.isOf(WatheItems.REVOLVER)) return;
        // 真实左轮右键会先发机械上膛声，再发射击声；回放代理没有客户端物品 use 链，需在服务端补齐。
        proxy.getWorld().playSound(null, proxy.getX(), proxy.getEyeY(), proxy.getZ(),
                WatheSounds.ITEM_REVOLVER_CLICK, SoundCategory.PLAYERS, 0.5F, 1.0F);
        EntityHitResult hit = target(proxy, MagicianConstants.REVOLVER_RANGE);
        if (hit != null && hit.getEntity() instanceof ServerPlayerEntity target && GameFunctions.isPlayerAliveAndSurvival(target)) {
            GameRecordManager.recordItemUse(owner, net.minecraft.registry.Registries.ITEM.getId(WatheItems.REVOLVER), target instanceof ServerPlayerEntity sp ? sp : null, null);
            GameFunctions.killPlayer(target, true, owner, GameConstants.DeathReasons.GUN);
        } else {
            GameRecordManager.recordItemUse(owner, net.minecraft.registry.Registries.ITEM.getId(WatheItems.REVOLVER), null, null);
        }
        proxy.getWorld().playSound(null, proxy.getX(), proxy.getEyeY(), proxy.getZ(), WatheSounds.ITEM_REVOLVER_SHOOT, SoundCategory.PLAYERS, 5f, 1f);
        // Wathe 原版火花是客户端手部特效；回放代理没有真实客户端手部，因此补发服务端粒子让所有客户端可见。
        if (proxy.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(WatheParticles.GUNSHOT, proxy.getX(), proxy.getEyeY() - 0.15D, proxy.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        proxy.getItemCooldownManager().set(WatheItems.REVOLVER, GameConstants.ITEM_COOLDOWNS.getOrDefault(WatheItems.REVOLVER, 0));
        visible.playReplaySwing(Hand.MAIN_HAND);
    }
    private static void knife(ServerPlayerEntity owner, MagicianPlaybackFakePlayer proxy, MagicianPlaybackEntity visible) {
        EntityHitResult hit = target(proxy, MagicianConstants.KNIFE_RANGE);
        if (hit == null || !(hit.getEntity() instanceof ServerPlayerEntity target) || !GameFunctions.isPlayerAliveAndSurvival(target)) return;
        GameRecordManager.recordItemUse(owner, net.minecraft.registry.Registries.ITEM.getId(WatheItems.KNIFE), target instanceof ServerPlayerEntity sp ? sp : null, null);
        GameFunctions.killPlayer(target, true, owner, GameConstants.DeathReasons.KNIFE);
        target.playSound(WatheSounds.ITEM_KNIFE_STAB, 1f, 1f); proxy.swingHand(Hand.MAIN_HAND); visible.playReplaySwing(Hand.MAIN_HAND);
        proxy.getItemCooldownManager().set(WatheItems.KNIFE, GameConstants.ITEM_COOLDOWNS.getOrDefault(WatheItems.KNIFE, 0));
    }
    private static @Nullable EntityHitResult target(PlayerEntity player, double range) {
        Box box = player.getBoundingBox().stretch(player.getRotationVec(1f).multiply(range)).expand(.5);
        return ProjectileUtil.raycast(player, player.getEyePos(), player.getEyePos().add(player.getRotationVec(1f).multiply(range)), box,
                e -> e != player && e instanceof PlayerEntity p && !p.isSpectator() && GameFunctions.isPlayerAliveAndSurvival(p), range * range);
    }
}
