package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/**
 * 手帕 Handkerchief ({@code sparkwitch:hanna_handkerchief}), bought from Hanna's shop and usable by whoever holds it
 * (owner 2026-11-11). Hold right-click for {@link HannaRules#HANDKERCHIEF_HOLD_TICKS}; releasing early does nothing
 * and costs nothing. A finished use (server authority) spends 1 of its {@link HannaRules#HANDKERCHIEF_USES} uses,
 * starts the {@link HannaRules#HANDKERCHIEF_COOLDOWN_TICKS} item cooldown, starts
 * {@link HannaRules#HANDKERCHIEF_TICKS} of sanity regeneration in {@link HannaComponent} (pulsed by
 * {@link HannaFloatService}) and ends a running float penalty. Only a living participant of an ACTIVE round can use
 * it. Uses left are vanilla durability (the Spare Key model); nothing is stored in custom data. No death drop is
 * registered.
 * 手帕（{@code sparkwitch:hanna_handkerchief}），在汉娜的商店购买，谁拿到都能用（所有者 2026-11-11）。按住右键
 * {@link HannaRules#HANDKERCHIEF_HOLD_TICKS}；提前松开不生效，也不消耗。完成一次使用（由服务端决定）会消耗
 * {@link HannaRules#HANDKERCHIEF_USES} 次中的 1 次、进入 {@link HannaRules#HANDKERCHIEF_COOLDOWN_TICKS} 的物品冷却、在
 * {@link HannaComponent} 中开始 {@link HannaRules#HANDKERCHIEF_TICKS} 的理智回复（由 {@link HannaFloatService} 逐秒结算），
 * 并解除进行中的浮游惩罚。只有 ACTIVE 回合中存活的参与者可以使用。剩余次数即原版耐久（沿用备用钥匙的做法）；不在自定义数据
 * 里存任何东西。未注册死亡掉落。
 */
public final class HannaHandkerchiefItem extends Item {

    public HannaHandkerchiefItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings()
                .maxCount(1)
                .maxDamage(HannaRules.HANDKERCHIEF_USES);
    }

    /**
     * A living, non-spectator participant of an ACTIVE round. Reads only synced state, so both sides agree and the
     * client never starts a hold the server will refuse.
     * ACTIVE 回合中存活且非旁观的参与者。只读取已同步的状态，因此两端结论一致，客户端不会开始一次服务端会拒绝的按住。
     */
    static boolean inActiveRound(PlayerEntity user) {
        return GameWorldComponent.KEY.get(user.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(user) && !user.isSpectator();
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this) || !inActiveRound(user)) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    /**
     * NONE on purpose: EAT and DRINK play consumption sounds and particles to everyone nearby (and would break a
     * float's silence), BLOCK makes vanilla treat the raised item as a shield ({@code LivingEntity#isBlocking}), and
     * the bow, spyglass and horn poses need item-model overrides this flat sprite does not have.
     * 特意使用 NONE：EAT 与 DRINK 会向附近所有人播放进食音效与粒子（也会破坏浮游的静音），BLOCK 会让原版把举起的物品当作盾牌
     * （{@code LivingEntity#isBlocking}），而弓、望远镜与号角的姿势需要这张平面贴图没有的物品模型覆盖。
     */
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return HannaRules.HANDKERCHIEF_HOLD_TICKS;
    }

    /**
     * Reached only when the full hold elapsed; vanilla puts the returned stack back in the hand. The gate is checked
     * again because the round may have ended or the user died during the hold.
     * 只有按满时间才会到达这里；原版会把返回的物品堆放回手中。再次检查门槛，因为按住期间对局可能已结束或使用者已死亡。
     */
    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity player) || !inActiveRound(player)) {
            return stack;
        }
        HannaComponent state = HannaComponent.KEY.get(player);
        state.startSoothe(world.getTime());
        boolean clearedPenalty = state.clearPenalty();
        player.getItemCooldownManager().set(this, HannaRules.HANDKERCHIEF_COOLDOWN_TICKS);
        // Private cue: a floating Hanna stays silent for everyone else. / 私有提示音：浮游中的汉娜对其他人依然无声。
        player.playSoundToPlayer(SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 0.8F, 1.4F);
        HannaReplay.recordHandkerchief(player, clearedPenalty);
        if (player.isCreative()) {
            return stack;
        }
        stack.setDamage(stack.getDamage() + 1);
        // The last use removes the handkerchief instead of leaving a broken one. / 最后一次使用直接移除，不留损坏的手帕。
        return stack.getDamage() >= stack.getMaxDamage() ? ItemStack.EMPTY : stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // Stack data only: the creative search index builds tooltips off-thread with no player.
        // 只读物品堆数据：创造模式搜索索引会在后台线程、无玩家时构建提示文本。
        tooltip.add(Text.translatable("item.sparkwitch.hanna_handkerchief.tooltip.usage",
                HannaRules.HANDKERCHIEF_HOLD_TICKS / 20, HannaRules.HANDKERCHIEF_TICKS / 20,
                Math.round(HannaRules.HANDKERCHIEF_SANITY_PER_SECOND * 100F)).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.sparkwitch.hanna_handkerchief.tooltip.cooldown",
                HannaRules.HANDKERCHIEF_COOLDOWN_TICKS / 20).formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
