package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.caecorthus.sparkwitch.record.AchievementRecords;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheParticles;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
            case BAT_HIT -> bat(owner, proxy, visible);
        }
    }
    private static void attack(ServerPlayerEntity owner, MagicianPlaybackFakePlayer proxy, MagicianPlaybackEntity visible) {
        // Players only: vanilla attack() has no reach check, so a crow stand-in would punch the hidden body from afar.
        // 只选玩家：原版 attack() 不检查距离，乌鸦替身会让拳头隔空打到隐藏的本体。
        ServerPlayerEntity target = pickPlayer(owner, proxy, MagicianConstants.KNIFE_RANGE);
        if (target == null) return;
        // 普通左键必须继续走 Minecraft 原版伤害链，不能把空手挥击错误变成瞬杀。A recorded ATTACK is a left-click
        // that reached the attack chain (a punch, also with a knife in hand, as in Wathe); a full-charge bat kill is
        // recorded separately as BAT_HIT. / 录制的 ATTACK 是进入攻击链的左键（与 Wathe 一致，手持刀也只是普通一拳）；
        // 满蓄力球棒击杀另行录制为 BAT_HIT。
        proxy.attack(target);
        proxy.swingHand(Hand.MAIN_HAND); visible.playReplaySwing(Hand.MAIN_HAND);
    }
    /**
     * Replays a full-charge bat kill the Magician landed while recording (recorded at Wathe's kill call, so a weak
     * swing never replays as a kill). The bat must be in the proxy's main hand at this frame.
     * 回放录制期间魔术师打出的满蓄力球棒击杀（在 Wathe 击杀调用处录制，弱挥击永远不会回放成击杀）。该帧代理主手必须持球棒。
     */
    private static void bat(ServerPlayerEntity owner, MagicianPlaybackFakePlayer proxy, MagicianPlaybackEntity visible) {
        if (!proxy.getMainHandStack().isOf(WatheItems.BAT)) return;
        ServerPlayerEntity target = target(owner, proxy, MagicianConstants.KNIFE_RANGE);
        if (target == null) return;
        puppetKill(owner, visible, target, GameConstants.DeathReasons.BAT, WatheItems.BAT);
        proxy.getWorld().playSound(null, target.getX(), target.getEyeY(), target.getZ(),
                WatheSounds.ITEM_BAT_HIT, SoundCategory.PLAYERS, 3.0F, 1.0F);
        proxy.swingHand(Hand.MAIN_HAND);
        visible.playReplaySwing(Hand.MAIN_HAND);
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
        // A knife release never stabs by itself: only the KNIFE_STAB recorded at Wathe's accepted-stab anchor does,
        // so a released knife that Wathe or SparkTraits refused never replays as a kill.
        // 松开刀本身从不刺击：只有在 Wathe 已接受刺击锚点录制的 KNIFE_STAB 才会，因此被 Wathe 或 SparkTraits 拒绝的松手
        // 不会回放成击杀。
        proxy.stopUsingItem(); visible.setReplayUseState(false, null); visible.setReplayItemUseTimeLeft(0);
    }
    private static void shoot(ServerPlayerEntity owner, MagicianPlaybackFakePlayer proxy, MagicianPlaybackEntity visible) {
        ItemStack stack = proxy.getMainHandStack();
        if (!stack.isOf(WatheItems.REVOLVER)) return;
        // 真实左轮右键会先发机械上膛声，再发射击声；回放代理没有客户端物品 use 链，需在服务端补齐。
        proxy.getWorld().playSound(null, proxy.getX(), proxy.getEyeY(), proxy.getZ(),
                WatheSounds.ITEM_REVOLVER_CLICK, SoundCategory.PLAYERS, 0.5F, 1.0F);
        ServerPlayerEntity target = target(owner, proxy, MagicianConstants.REVOLVER_RANGE);
        if (target != null) {
            GameRecordManager.recordItemUse(owner, net.minecraft.registry.Registries.ITEM.getId(WatheItems.REVOLVER), target, null);
            puppetKill(owner, visible, target, GameConstants.DeathReasons.GUN, WatheItems.REVOLVER);
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
        // The recorded stab passed Wathe's receiver with a knife in hand; the replay needs the knife in hand too.
        // 录制的刺击是手持刀通过 Wathe 接收器的；回放同样要求手中有刀。
        if (!proxy.getMainHandStack().isOf(WatheItems.KNIFE) && !proxy.getOffHandStack().isOf(WatheItems.KNIFE)) return;
        ServerPlayerEntity target = target(owner, proxy, MagicianConstants.KNIFE_RANGE);
        if (target == null) return;
        GameRecordManager.recordItemUse(owner, net.minecraft.registry.Registries.ITEM.getId(WatheItems.KNIFE), target, null);
        puppetKill(owner, visible, target, GameConstants.DeathReasons.KNIFE, WatheItems.KNIFE);
        target.playSound(WatheSounds.ITEM_KNIFE_STAB, 1f, 1f); proxy.swingHand(Hand.MAIN_HAND); visible.playReplaySwing(Hand.MAIN_HAND);
        proxy.getItemCooldownManager().set(WatheItems.KNIFE, GameConstants.ITEM_COOLDOWNS.getOrDefault(WatheItems.KNIFE, 0));
    }
    /**
     * The puppet's kill, unchanged ({@code killPlayer} with the owner as killer), followed by achievement record W17
     * only when it took: the target was alive before and is dead after (a parry, shield or protection leaves them
     * alive). The disguise's death state is read before the kill, so a puppet disguised as its own victim reads false.
     * 皮套的击杀，行为不变（以主人为击杀者调用 {@code killPlayer}）；仅当击杀生效时（目标此前存活、此后死亡；格挡、护盾或保护会让其
     * 存活）才写入成就记录 W17。伪装对象的死亡状态在击杀前读取，因此伪装成受害者本人的皮套记为未死亡。
     */
    private static void puppetKill(ServerPlayerEntity owner, MagicianPlaybackEntity visible, ServerPlayerEntity target,
                                   Identifier deathReason, Item weapon) {
        GameWorldComponent game = GameWorldComponent.KEY.get(target.getWorld());
        UUID disguise = visible.disguise();
        boolean disguiseDead = disguise != null && game.isPlayerDead(disguise);
        boolean wasDead = game.isPlayerDead(target.getUuid());
        GameFunctions.killPlayer(target, true, owner, deathReason);
        if (killTook(wasDead, game.isPlayerDead(target.getUuid()))) {
            AchievementRecords.magicianPuppetKill(owner, target, Registries.ITEM.getId(weapon), disguise, disguiseDead);
        }
    }
    /** W17: a puppet kill took only when it turned a living target dead. / W17：仅当把存活目标变为死亡时皮套击杀才算生效。 */
    static boolean killTook(boolean deadBefore, boolean deadAfter) {
        return !deadBefore && deadAfter;
    }
    /**
     * The player the puppet's weapon hits, picked like Wathe's own knife/revolver targeting
     * ({@code ProjectileUtil.getCollision}: the nearest entity along the look ray, cut by blocks, so nothing is hit
     * through walls or closed doors). Never the Magician who owns the puppet (the proxy carries the owner's UUID, so
     * the UUID check excludes both), never a spectator or a dead player.
     * 皮套武器命中的玩家，与 Wathe 自身刀/左轮选目标方式一致（{@code ProjectileUtil.getCollision}：沿视线的最近实体，
     * 被方块截断，因此不会隔墙或隔着关闭的门命中）。永远不会是皮套主人魔术师（代理使用主人的 UUID，UUID 检查同时排除二者），
     * 也不会是旁观者或已死亡玩家。
     */
    private static @Nullable ServerPlayerEntity target(ServerPlayerEntity owner, PlayerEntity proxy, double range) {
        ServerPlayerEntity picked = pickPlayer(owner, proxy, range);
        // Raven Form seam (the lethal gun/knife/bat replays only): a crow strictly nearer on the puppet's look ray stands
        // in for its owner. / 化鸦接缝（仅致命的枪/刀/球棒回放）：皮套视线上严格更近的乌鸦以其主人代入。
        ServerPlayerEntity crowOwner = proxy instanceof ServerPlayerEntity attacker
                ? BlackRavenCrowHits.nearerOnLook(attacker, picked, range) : null;
        return crowOwner != null ? crowOwner : picked;
    }
    /**
     * The players-only half of {@link #target}: the nearest eligible player on the puppet's block-clipped look ray,
     * never a Raven Form crow's owner. / {@link #target} 只选玩家的一半：皮套按方块截断的视线上最近的合格玩家，绝不是化鸦乌鸦的主人。
     */
    private static @Nullable ServerPlayerEntity pickPlayer(ServerPlayerEntity owner, PlayerEntity proxy, double range) {
        HitResult hit = ProjectileUtil.getCollision(proxy,
                e -> e instanceof ServerPlayerEntity p && !p.getUuid().equals(owner.getUuid())
                        && !p.isSpectator() && GameFunctions.isPlayerAliveAndSurvival(p),
                range);
        return hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof ServerPlayerEntity target ? target : null;
    }
}
