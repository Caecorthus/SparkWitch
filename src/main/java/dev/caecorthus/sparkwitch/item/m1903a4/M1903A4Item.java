package dev.caecorthus.sparkwitch.item.m1903a4;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.index.WatheParticles;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * The M1903A4 keeps the bow pose while charged, and loads .30-06 rounds into a five-slot stack the way the Hunter's
 * double barrel loads shells: each right-click with the ammunition (or an inventory right-click on the rifle) pushes
 * one round, the last loaded is fired first. Loading is server-authoritative and never fires anything yet.
 * <p>
 * Cooldown contract: the rifle starts each round on a 60 second cooldown. Loading a round only refreshes the cooldown
 * when less than two seconds are left (or none is running), so a long cooldown is never shortened and a fresh one is
 * never stretched past two seconds.
 * M1903A4 保留蓄力时的拉弓姿势，并按猎人双管猎枪的方式把 .30-06 子弹压进五格弹栈：用弹药右键一次（或在背包中右键步枪）
 * 装入一发，后装先打。装填为服务端权威，目前不会开火。
 * <p>
 * 冷却契约：步枪每局开局进入 60 秒冷却。装填时只有剩余冷却不足两秒（或没有冷却）才会刷新为两秒，长冷却不会被缩短，
 * 新冷却也不会被拉长到两秒以上。
 */
public class M1903A4Item extends BowItem {
    public static final int MAX_ROUNDS = 5;
    public static final int RELOAD_COOLDOWN_TICKS = 40;
    public static final int ROUND_START_COOLDOWN_TICKS = 20 * 60;
    /** Muzzle point: this far along the look vector, this far below eye level. / 枪口：沿视线的距离与低于眼睛的高度。 */
    private static final double MUZZLE_DISTANCE = 0.95D;
    private static final double MUZZLE_DROP = 0.12D;

    private static final String LOADED_ROUNDS_KEY = "LoadedRounds";
    private static final String COOLDOWN_UNTIL_KEY = "CooldownUntil";

    public M1903A4Item(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // BowItem.use refuses without arrows in the inventory; the rifle charges on its own loaded rounds instead.
        // 原版 BowItem.use 会因为背包里没有箭而拒绝；步枪按自身弹栈判定蓄力。
        if (user.getItemCooldownManager().isCoolingDown(this) || getLoadedRounds(stack) <= 0) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    /**
     * Any release fires, no matter how long the charge was: one loaded round leaves the stack and Wathe's gunshot
     * particles appear at the muzzle. Switching the held item cancels the shot like a bow, because that path clears the
     * active stack without calling this method.
     * 无论蓄力多久，松开都会开火：弹栈减少一发，并在枪口生成 Wathe 的枪口焰。切换物品会像弓一样取消射击，因为那条路径
     * 只清除使用状态、不会调用本方法。
     */
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient || getLoadedRounds(stack) <= 0) {
            return;
        }
        popRound(stack);
        if (user instanceof ServerPlayerEntity shooter) {
            Vec3d muzzle = muzzle(shooter);
            shooter.getServerWorld().spawnParticles(
                    WatheParticles.GUNSHOT, muzzle.x, muzzle.y, muzzle.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            M1903A4Gunplay.onFired(shooter);
            long until = shooter.getServerWorld().getTime() + M1903A4Gunplay.SHOT_COOLDOWN_TICKS;
            setCooldownUntil(stack, until);
            shooter.getItemCooldownManager().set(this, M1903A4Gunplay.SHOT_COOLDOWN_TICKS);
            M1903A4Combat.fire(shooter);
        }
    }

    /** Muzzle point: eye level, a little below the look vector and a hair to the right/left of centre.
     *  枪口位置：眼睛高度、沿视线前方、略微偏低。 */
    public static Vec3d muzzle(ServerPlayerEntity shooter) {
        Vec3d look = shooter.getRotationVec(1.0F);
        return new Vec3d(
                shooter.getX() + look.x * MUZZLE_DISTANCE,
                shooter.getEyeY() - MUZZLE_DROP + look.y * MUZZLE_DISTANCE,
                shooter.getZ() + look.z * MUZZLE_DISTANCE
        );
    }

    /** One round leaves the top of the stack. / 弹栈弹出栈顶一发。 */
    private static void popRound(ItemStack stack) {
        NbtCompound nbt = customData(stack);
        nbt.putInt(LOADED_ROUNDS_KEY, Math.max(0, getLoadedRounds(stack) - 1));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    @Override
    public boolean onClicked(ItemStack rifle, ItemStack ammo, Slot slot, ClickType clickType,
                             PlayerEntity player, StackReference cursorStackReference) {
        if (clickType != ClickType.RIGHT || !tryReload(player, rifle, ammo)) {
            return false;
        }
        if (!player.getWorld().isClient) {
            cursorStackReference.set(ammo);
        }
        return true;
    }

    /** Loads one round; the single path shared by the ammunition item and the inventory click. / 装一发，弹药使用与背包点击共用。 */
    public static boolean tryReload(PlayerEntity player, ItemStack rifle, ItemStack ammo) {
        if (!(rifle.getItem() instanceof M1903A4Item rifleItem) || !ammo.isOf(SparkWitchItems.ammo3006())) {
            return false;
        }
        if (getLoadedRounds(rifle) >= MAX_ROUNDS) {
            return false;
        }
        if (player.getWorld().isClient) {
            return true;
        }
        pushRound(rifle);
        long now = player.getWorld().getTime();
        long remaining = getCooldownUntil(rifle) - now;
        if (remaining < RELOAD_COOLDOWN_TICKS) {
            setCooldownUntil(rifle, now + RELOAD_COOLDOWN_TICKS);
            player.getItemCooldownManager().set(rifleItem, RELOAD_COOLDOWN_TICKS);
        }
        if (!player.isCreative()) {
            ammo.decrement(1);
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            M1903A4Gunplay.onLoaded(serverPlayer);
        }
        player.currentScreenHandler.sendContentUpdates();
        return true;
    }

    /** Round-start cooldown written onto the rifle itself (world tick based) and onto the item cooldown manager. */
    public static void startRoundCooldown(PlayerEntity player, ItemStack rifle) {
        long now = player.getWorld().getTime();
        setCooldownUntil(rifle, now + ROUND_START_COOLDOWN_TICKS);
        player.getItemCooldownManager().set(SparkWitchItems.m1903a4(), ROUND_START_COOLDOWN_TICKS);
    }

    public static int getLoadedRounds(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return 0;
        }
        return Math.max(0, Math.min(customData.copyNbt().getInt(LOADED_ROUNDS_KEY), MAX_ROUNDS));
    }

    private static long getCooldownUntil(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData == null ? 0L : customData.copyNbt().getLong(COOLDOWN_UNTIL_KEY);
    }

    private static void setCooldownUntil(ItemStack stack, long worldTime) {
        NbtCompound nbt = customData(stack);
        nbt.putLong(COOLDOWN_UNTIL_KEY, worldTime);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static void pushRound(ItemStack stack) {
        NbtCompound nbt = customData(stack);
        nbt.putInt(LOADED_ROUNDS_KEY, Math.min(getLoadedRounds(stack) + 1, MAX_ROUNDS));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static NbtCompound customData(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData == null ? new NbtCompound() : customData.copyNbt();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int loaded = getLoadedRounds(stack);
        tooltip.add(Text.translatable("item.sparkwitch.m1903a4.tooltip.line1", loaded, MAX_ROUNDS)
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.sparkwitch.m1903a4.tooltip.line2").formatted(Formatting.GRAY));
        // One line per slot, top of the stack first (fires next), empty slots named as such.
        // 每个弹位一行，栈顶在前（下一发），空位显示为空。
        for (int slot = 0; slot < MAX_ROUNDS; slot++) {
            tooltip.add(slot < loaded
                    ? Text.translatable(SparkWitchItems.ammo3006().getTranslationKey()).formatted(Formatting.GRAY)
                    : Text.translatable("item.sparkwitch.m1903a4.tooltip.empty").formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
