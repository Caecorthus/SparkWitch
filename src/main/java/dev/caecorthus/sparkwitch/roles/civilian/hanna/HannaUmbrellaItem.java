package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 洋伞 Parasol ({@code sparkwitch:hanna_umbrella}), bought from Hanna's shop and usable by whoever holds it (owner
 * 2026-11-11). Server authority:
 * <ul>
 *   <li>Held in either hand by a living, non-spectator player: Slow Falling II is kept on (a 2 s effect refreshed
 *   only when it is missing or about to run out, never once per tick) and 1 durability is spent per second of world
 *   time; at 0 the parasol is gone. Creative holders spend nothing.</li>
 *   <li>Right-click: 10 s of Slow Falling II that needs no holding, with an item cooldown of the same length.</li>
 * </ul>
 * Uses left are vanilla durability (the bar shows them), the Spare Key model; nothing is stored in custom data. No
 * death drop is registered. Its cues are private sounds, so a floating Hanna stays silent for everyone else.
 * 洋伞（{@code sparkwitch:hanna_umbrella}），在汉娜的商店购买，谁拿到都能用（所有者 2026-11-11）。全部由服务端决定：
 * 存活且非旁观的玩家把它握在任一只手时保持缓降 II（2 秒的效果，仅在缺失或即将结束时刷新，绝不每刻一次），并按世界时间每秒
 * 消耗 1 点耐久；归零即消失。创造模式不消耗。右键：10 秒无需手持的缓降 II，并进入同样长度的物品冷却。
 * 剩余次数即原版耐久（耐久条显示），沿用备用钥匙的做法；不在自定义数据里存任何东西。未注册死亡掉落。提示音都是私有声音，
 * 因此浮游中的汉娜对其他人依然无声。
 */
public final class HannaUmbrellaItem extends Item {
    /** Held upkeep: a 2 s effect, refreshed in its last second. / 手持维持：2 秒的效果，在最后一秒内刷新。 */
    static final int HELD_EFFECT_TICKS = 40;
    static final int HELD_REFRESH_BELOW_TICKS = 20;

    public HannaUmbrellaItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings()
                .maxCount(1)
                .maxDamage(HannaRules.UMBRELLA_DURABILITY);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        // Compared by identity: vanilla passes "selected" for the off-hand and armour lists by index too.
        // 按对象身份比较：原版对副手与护甲列表同样按下标传入 selected。
        if (world.isClient() || !(entity instanceof ServerPlayerEntity holder)
                || (holder.getMainHandStack() != stack && holder.getOffHandStack() != stack)
                || !holder.isAlive() || holder.isSpectator()) {
            return;
        }
        if (needsSlowFalling(holder.getStatusEffect(StatusEffects.SLOW_FALLING))) {
            holder.addStatusEffect(slowFalling(HELD_EFFECT_TICKS));
        }
        if (world.getTime() % 20L == 0L && !holder.isCreative()) {
            stack.setDamage(stack.getDamage() + 1);
            if (stack.getDamage() >= stack.getMaxDamage()) {
                // Zero durability removes the parasol instead of leaving a broken one. / 耐久归零直接移除，不留损坏的洋伞。
                stack.decrement(1);
                holder.playSoundToPlayer(SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.5F, 1.2F);
            }
        }
    }

    /**
     * Missing, weaker, or ours about to run out. A longer or infinite Slow Falling of the same or a higher level is
     * left alone (a weaker one stays underneath as vanilla's hidden effect and comes back afterwards).
     * 缺失、更弱，或我们的效果即将结束。等级相同或更高、且更长或无限的缓降保持不动（更弱的效果由原版保留为隐藏效果，之后恢复）。
     */
    static boolean needsSlowFalling(@Nullable StatusEffectInstance current) {
        if (current == null || current.getAmplifier() < HannaRules.SLOW_FALLING_AMPLIFIER) {
            return true;
        }
        return current.getAmplifier() == HannaRules.SLOW_FALLING_AMPLIFIER && !current.isInfinite()
                && current.getDuration() < HELD_REFRESH_BELOW_TICKS;
    }

    private static StatusEffectInstance slowFalling(int ticks) {
        return new StatusEffectInstance(StatusEffects.SLOW_FALLING, ticks, HannaRules.SLOW_FALLING_AMPLIFIER,
                false, false, true);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user instanceof ServerPlayerEntity player) {
            if (!player.isAlive() || player.isSpectator()) {
                return TypedActionResult.pass(stack);
            }
            player.addStatusEffect(slowFalling(HannaRules.UMBRELLA_OPEN_TICKS));
            // Vanilla refuses a cooling item on both sides before use() runs: no replay or sound spam.
            // 原版在两端都会在 use() 之前拒绝冷却中的物品：不会刷回放，也不会刷声音。
            player.getItemCooldownManager().set(this, HannaRules.UMBRELLA_OPEN_TICKS);
            player.playSoundToPlayer(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.7F, 1.3F);
            HannaReplay.recordUmbrella(player);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    /**
     * Fabric seam: the per-second durability change must not replay the equip animation of the held parasol.
     * Fabric 接缝：每秒一次的耐久变化不得让手持的洋伞重播换手动画。
     */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack,
                                                  ItemStack newStack) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // Stack data only: the creative search index builds tooltips off-thread with no player.
        // 只读物品堆数据：创造模式搜索索引会在后台线程、无玩家时构建提示文本。
        tooltip.add(Text.translatable("item.sparkwitch.hanna_umbrella.tooltip.usage",
                HannaRules.UMBRELLA_OPEN_TICKS / 20).formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
