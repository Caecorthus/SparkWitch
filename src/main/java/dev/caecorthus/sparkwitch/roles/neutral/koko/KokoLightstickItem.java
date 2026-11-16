package dev.caecorthus.sparkwitch.roles.neutral.koko;

import java.util.List;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 应援棒 ({@code sparkwitch:koko_lightstick}), sold only in Koko's shop for 100 traffic. Right-click: Speed II for
 * 10 s and, for a living Koko, doubled stream traffic for the same 10 s; then a 30 s vanilla item cooldown. It is
 * never consumed (owner 2026-11-13: reusable, 30 s apart) and works for whoever holds it, like the other shop items:
 * another holder only gets the speed.
 * <p>Client/server authority: both sides refuse a cooling item or a player outside an ACTIVE round from synced state,
 * so the client never swings for a use the server refuses; the effect, the doubling window, the cooldown and the replay
 * line are server-only.
 * 应援棒（{@code sparkwitch:koko_lightstick}），只在可可的商店出售，售价 100 流量。右键：10 秒速度 II，存活的可可同时获得这
 * 10 秒内的直播流量翻倍；随后进入 30 秒原版物品冷却。使用后不消耗（所有者 2026-11-13：可重复使用，间隔 30 秒），与其他商店
 * 物品一样谁拿到都能用：其他持有者只获得速度。
 * <p>客户端/服务端权威：两端都依据已同步状态拒绝冷却中的物品或不在 ACTIVE 回合中的玩家，因此客户端不会为服务端会拒绝的使用
 * 挥手；效果、翻倍窗口、冷却与回放都只在服务端执行。
 */
public final class KokoLightstickItem extends Item {
    public KokoLightstickItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this) || !KokoStreamService.inActiveRound(user)) {
            return TypedActionResult.fail(stack);
        }
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            cheer(player);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    private void cheer(ServerPlayerEntity player) {
        long now = player.getServerWorld().getTime();
        player.getItemCooldownManager().set(this, KokoRules.LIGHTSTICK_COOLDOWN_TICKS);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, KokoRules.LIGHTSTICK_BUFF_TICKS,
                KokoRules.LIGHTSTICK_SPEED_AMPLIFIER, false, false, true));
        if (KokoParticipation.isKoko(player)) {
            KokoComponent.KEY.get(player).startCheer(now);
        }
        KokoStreamService.cue(player, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.8F, 1.6F);
        KokoReplay.recordLightstick(player);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // Stack data only: the creative search index builds tooltips off-thread with no player.
        // 只读物品堆数据：创造模式搜索索引会在后台线程、无玩家时构建提示文本。
        tooltip.add(Text.translatable("item.sparkwitch.koko_lightstick.tooltip.usage",
                KokoRules.LIGHTSTICK_BUFF_TICKS / 20).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.sparkwitch.koko_lightstick.tooltip.cooldown",
                KokoRules.LIGHTSTICK_COOLDOWN_TICKS / 20).formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
