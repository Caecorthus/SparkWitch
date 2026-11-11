package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** 汉娜的洋伞与手帕。 Hanna's umbrella and handkerchief. */
public final class HannaItems {

    private static final String DURABILITY = "sparkwitch:umbrella_left";

    private HannaItems() {
    }

    /** 洋伞: slow falling while held, one point of durability per second. 洋伞：手持缓降，每秒 1 点耐久。 */
    public static final class Umbrella extends Item {
        public Umbrella(Settings settings) {
            super(settings);
        }

        public static int left(ItemStack stack) {
            NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            return nbt.contains(DURABILITY) ? nbt.getInt(DURABILITY) : HannaRules.UMBRELLA_DURABILITY;
        }

        public static void setLeft(ItemStack stack, int value) {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt(DURABILITY, Math.max(0, value));
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }

        /** While held: slow falling, and one durability per second. 手持时：缓降，每秒扣 1 点耐久。 */
        @Override
        public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
            if (world.isClient || !selected || !(entity instanceof PlayerEntity player)) {
                return;
            }
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 40, 1, false, false, false));
            int left = left(stack);
            if (world.getTime() % 20L == 0L) {
                left--;
                setLeft(stack, left);
                if (left <= 0) {
                    stack.decrement(1);
                }
            }
        }

        /** Right-click opens the parasol: 10 s of slow falling, and it is recorded for the replay. */
        /** 右键撑开洋伞：10 秒缓降，并写入回放。 */
        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
                return TypedActionResult.success(stack);
            }
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 10 * 20, 1, false, false, false));
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(),
                    SoundCategory.PLAYERS, 0.7F, 1.2F);
            var replay = new NbtCompound();
            replay.putString("action", "umbrella");
            dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                    player, HannaRules.ROLE_ID, null, replay);
            return TypedActionResult.success(stack);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("item.sparkwitch.hanna_umbrella.tooltip.left", left(stack))
                    .formatted(Formatting.AQUA));
            tooltip.add(Text.translatable("item.sparkwitch.hanna_umbrella.tooltip.usage")
                    .formatted(Formatting.GRAY));
        }
    }

    /** 手帕: right-click for 5 s of sanity regen, clears the float penalty. 2 uses, 15 s between uses. */
    /** 手帕：右键 5 秒回理智并解除浮游惩罚。2 次耐久，每次间隔 15 秒。 */
    public static final class Handkerchief extends Item {

        private static final String USES_LEFT = "sparkwitch:handkerchief_left";

        public Handkerchief(Settings settings) {
            super(settings);
        }

        private static int usesLeft(ItemStack stack) {
            NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            return nbt.contains(USES_LEFT) ? nbt.getInt(USES_LEFT) : HannaRules.HANDKERCHIEF_USES;
        }

        private static void setUsesLeft(ItemStack stack, int value) {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt(USES_LEFT, Math.max(0, value));
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("item.sparkwitch.hanna_handkerchief.tooltip.left", usesLeft(stack))
                    .formatted(Formatting.AQUA));
        }

        /** 20 ticks of holding = 1 s; the effect lands when the hold completes. 按住 20 tick（1 秒）后生效。 */
        @Override
        public int getMaxUseTime(ItemStack stack, net.minecraft.entity.LivingEntity user) {
            return 20;
        }

        @Override
        public void usageTick(World world, net.minecraft.entity.LivingEntity user, ItemStack stack, int remainingUseTicks) {
            if (world.isClient || remainingUseTicks > 1 || !(user instanceof ServerPlayerEntity player)) {
                return;
            }
            HannaService.useHandkerchief(player);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20, 0, false, false, false));
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_BOTTLE_EMPTY,
                    SoundCategory.PLAYERS, 0.6F, 1.4F);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
                return TypedActionResult.success(stack);
            }
            // Fire immediately on right-click; the hold timer is gone because a single click must work.
            // 右键立即生效；取消长按计时，因为单击也必须能用。
            if (player.getItemCooldownManager().isCoolingDown(this)) {
                return TypedActionResult.fail(stack);
            }
            player.getItemCooldownManager().set(this, HannaRules.HANDKERCHIEF_COOLDOWN_TICKS);
            int left = usesLeft(stack) - 1;
            setUsesLeft(stack, left);
            if (left <= 0) {
                stack.decrement(1);
            }
            HannaService.useHandkerchief(player);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20, 0, false, false, false));
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_BOTTLE_EMPTY,
                    SoundCategory.PLAYERS, 0.6F, 1.4F);
            return TypedActionResult.success(stack);
        }

        private TypedActionResult<ItemStack> unusedUse(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
                return TypedActionResult.success(stack);
            }
            HannaService.useHandkerchief(player);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20, 0, false, false, false));
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_BOTTLE_EMPTY,
                    SoundCategory.PLAYERS, 0.6F, 1.4F);
            return TypedActionResult.success(stack);
        }
    }
}
