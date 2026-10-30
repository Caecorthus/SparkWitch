package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * The Knight's cross sword. Right-click raises it for at least one second; the server then performs the same target
 * ray Wathe's knife uses ({@code ProjectileUtil.getCollision}, nearest living player, two blocks). During a duel the
 * nearest player must be the selected opponent, otherwise the swing is invalid and nothing happens. The sword never
 * enters a cooldown. No client payload or extra sound is involved.
 * 骑士的十字剑。右键举起至少 1 秒后，服务端执行与 Wathe 小刀相同的目标射线判定
 * （{@code ProjectileUtil.getCollision}，2 格内最近存活玩家）。决斗中最近的玩家必须是选定对手，
 * 否则无效、什么都不发生。十字剑永不冷却。全程不依赖客户端数据包，也不额外播放声音。
 */
public final class CrossSwordItem extends Item {
    public static final int MAX_USE_TICKS = 100;
    public static final int MIN_HOLD_TICKS = 10;
    public static final double STAB_RANGE = 2.0D;

    public CrossSwordItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.setCurrentHand(hand);
        user.playSound(WatheSounds.ITEM_KNIFE_PREPARE, 1.0F, 1.0F);
        user.getItemCooldownManager().remove(this);
        return TypedActionResult.consume(stack);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return MAX_USE_TICKS;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof ServerPlayerEntity player) || player.isSpectator() || player.isCreative()) {
            return;
        }
        player.getItemCooldownManager().remove(this);
        if (remainingUseTicks > MAX_USE_TICKS - MIN_HOLD_TICKS) {
            return;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
        // The cross sword only exists for the Knight in an active duel. / 十字剑只在决斗中的骑士手里有效。
        if (!duel.active() || !duel.isKnight(player.getUuid())) {
            return;
        }
        HitResult hit = ProjectileUtil.getCollision(player,
                entity -> entity instanceof PlayerEntity candidate && GameFunctions.isPlayerAliveAndSurvival(candidate),
                STAB_RANGE);
        if (!(hit instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof ServerPlayerEntity victim)
                || victim == player) {
            return;
        }
        UUID targetId = duel.target();
        // Check that this player is the one being duelled before any kill. / 先确认这个人就是被决斗者，再判击杀。
        if (targetId == null || !targetId.equals(victim.getUuid())) {
            return;
        }
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), WatheSounds.ITEM_KNIFE_STAB,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        GameFunctions.killPlayer(victim, true, player, GameConstants.DeathReasons.KNIFE);
    }

    /** The cross sword never has a cooldown. / 十字剑永不进入冷却。 */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (entity instanceof PlayerEntity player) {
            player.getItemCooldownManager().remove(this);
        }
    }
}
