package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Control Expert Disruptor: every other affectable participant within 6 blocks loses keyed instinct for 10 s.
 * Server-authoritative: the client only predicts a consumed use and sends nothing of its own. Every affected player
 * gets the same private notice and sound, and nothing that depends on who or how many were affected reaches the
 * world or the user, so the item can never work as a killer or witch detector.
 * 控场专家的干扰器：使 6 格内其他所有可被影响的参与者失去按键本能 10 秒。由服务端权威决定：客户端只预测
 * 一次已消耗的使用，不发送任何自有数据。每名受影响者获得相同的私密提示与音效，任何取决于受影响者身份或
 * 数量的信息都不会传到世界或使用者，因此该道具永远无法充当杀手或魔女探测器。
 */
public final class DisruptorItem extends Item {
    public static final String DISRUPTED_MESSAGE_KEY = "message.sparkwitch.control_expert.disrupted";
    private static final int TOOLTIP_LINES = 3;
    private static final SoundEvent USE_SOUND = SoundEvents.BLOCK_BEACON_POWER_SELECT;
    private static final float USE_VOLUME = 1.0F;
    private static final float USE_PITCH = 1.4F;
    private static final SoundEvent DISRUPTED_SOUND = SoundEvents.BLOCK_BEACON_DEACTIVATE;
    private static final float DISRUPTED_VOLUME = 1.0F;
    private static final float DISRUPTED_PITCH = 1.2F;

    public DisruptorItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // No client prediction of targets: vanilla's use-item packet is the whole request.
        // 客户端不预测目标：原版的使用物品数据包就是全部请求。
        if (world.isClient()) {
            return TypedActionResult.success(stack, false);
        }
        if (!(user instanceof ServerPlayerEntity ce) || !(world instanceof ServerWorld serverWorld)
                || !ControlExpertTargeting.canUse(ce)) {
            return TypedActionResult.fail(stack);
        }
        disruptNearby(ce, serverWorld);
        // Plain vanilla cooldown so SparkTraits Fast Hands may shorten it. / 普通原版冷却，SparkTraits 快手可缩短。
        ce.getItemCooldownManager().set(this, ControlExpertRules.DISRUPTOR_COOLDOWN);
        GameRecordManager.recordItemUse(ce, ControlExpertRules.DISRUPTOR_ID, null, null);
        // Fixed and heard only by the user: identical whether nobody or many were affected.
        // 固定且只有使用者本人能听到：无论无人还是多人受影响都完全相同。
        ce.playSoundToPlayer(USE_SOUND, SoundCategory.PLAYERS, USE_VOLUME, USE_PITCH);
        return TypedActionResult.consume(stack);
    }

    /**
     * Uniform per-target effect with no role, faction or instinct branch. The owner-only HUD countdown is the primary
     * notice; the action bar line is best effort because blackout re-sends the action bar every tick. Chat is no
     * fallback: Wathe hides the chat HUD from playing-and-alive, non-creative players, and SparkWitch reopens it only
     * for active Wraiths, who are never targets.
     * 对每个目标施加完全相同的效果，不按职业、阵营或本能能力分支。仅拥有者可见的 HUD 倒计时是主要提示；
     * 动作栏提示只是尽力而为，因为停电期间动作栏每刻都会被覆盖。聊天栏不能作为后备：Wathe 会对存活且非创造
     * 模式的参赛玩家隐藏聊天栏，SparkWitch 仅为激活的冤魂重新开放，而冤魂从不是目标。
     */
    private static void disruptNearby(ServerPlayerEntity ce, ServerWorld world) {
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (!ControlExpertRules.inDisruptRange(target.squaredDistanceTo(ce))
                    || !ControlExpertTargeting.canAffect(ce, target, ControlExpertRules.DISRUPTOR_ACTION)) {
                continue;
            }
            ControlExpertStatusComponent.KEY.get(target).disrupt(ControlExpertRules.DISRUPT_TICKS);
            target.sendMessage(Text.translatable(DISRUPTED_MESSAGE_KEY).formatted(Formatting.RED), true);
            target.playSoundToPlayer(DISRUPTED_SOUND, SoundCategory.PLAYERS, DISRUPTED_VOLUME, DISRUPTED_PITCH);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.disruptor.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
