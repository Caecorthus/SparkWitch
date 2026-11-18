package dev.caecorthus.sparkwitch.item.m1903a4;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.index.WatheParticles;
import dev.doctor4t.wathe.record.GameRecordManager;
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
 * one round, the last loaded is fired first. Loading and firing are server-authoritative; any-length charge fires on
 * release, from the main hand only, through the {@link M1903A4Combat#decide} gate asked at charge start and again at
 * release (a refused release spends nothing).
 * <p>
 * Cooldown contract: every write goes through {@link M1903A4Cooldowns} ("max + exact" on the holder's cooldown
 * manager, never shortening a longer lock): 60 s at round start, 10 s after a shot, and a 2 s floor after a load.
 * M1903A4 保留蓄力时的拉弓姿势，并按猎人双管猎枪的方式把 .30-06 子弹压进五格弹栈：用弹药右键一次（或在背包中右键步枪）
 * 装入一发，后装先打。装填与开火均为服务端权威；任意时长的蓄力在松开时开火，只限主手，并经 {@link M1903A4Combat#decide}
 * 门槛在开始蓄力与松开时各判定一次（被拒绝的松手不消耗任何东西）。
 * <p>
 * 冷却契约：所有写入都经过 {@link M1903A4Cooldowns}（在持有者的冷却管理器上“取大 + 精确”，绝不缩短更长的锁定）：开局 60 秒、
 * 开火后 10 秒、装填后 2 秒下限。
 */
public class M1903A4Item extends BowItem {
    public static final int MAX_ROUNDS = 5;
    /** Muzzle point: this far along the look vector, this far below eye level. / 枪口：沿视线的距离与低于眼睛的高度。 */
    private static final double MUZZLE_DISTANCE = 0.95D;
    private static final double MUZZLE_DROP = 0.12D;

    private static final String LOADED_ROUNDS_KEY = "LoadedRounds";

    public M1903A4Item(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    /**
     * Charge start. Main hand only: an off-hand rifle passes, so a failed main-hand ammo load never falls through into
     * charging the off-hand rifle. BowItem.use refuses without arrows in the inventory; the rifle charges on its own
     * loaded rounds instead.
     * 开始蓄力。只限主手：副手步枪返回 PASS，因此主手弹药装填失败时不会转而让副手步枪蓄力。原版 BowItem.use 会因为背包里没有箭而
     * 拒绝；步枪按自身弹栈判定蓄力。
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND) {
            return TypedActionResult.pass(stack);
        }
        if (M1903A4Combat.decide(user, stack, hand) != M1903A4Rules.Decision.FIRE) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    /**
     * Any release fires, no matter how long the charge was, but only when the server gate passes again now: a forced
     * release (Kidnapper control, stun), a sealed or cooling rifle, or a shooter who died mid-charge (Wathe keeps the
     * rifle and its active use) spends nothing. An accepted shot spends the round and writes the cooldown first, so
     * nothing later (a kill, a retaliation) can undo or repeat it. Switching the held item cancels the shot like a bow,
     * because that path clears the active stack without calling this method.
     * 无论蓄力多久，松开都会开火，但只有此刻服务端门槛再次通过时才开火：被强制松手（绑匪操控、眩晕）、被封印或冷却中的步枪、
     * 蓄力中死亡的射手（Wathe 会保留步枪及其使用状态）都不消耗任何东西。被接受的射击先耗弹并写入冷却，之后的任何事（击杀、反噬）
     * 都无法撤销或重复它。切换物品会像弓一样取消射击，因为那条路径只清除使用状态、不会调用本方法。
     */
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient || !(user instanceof ServerPlayerEntity shooter)
                || M1903A4Combat.decide(shooter, stack, shooter.getActiveHand()) != M1903A4Rules.Decision.FIRE) {
            return;
        }
        popRound(stack);
        M1903A4Cooldowns.shot(shooter);
        Vec3d muzzle = muzzle(shooter);
        shooter.getServerWorld().spawnParticles(
                WatheParticles.GUNSHOT, muzzle.x, muzzle.y, muzzle.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        M1903A4Gunplay.onFired(shooter);
        M1903A4Combat.fire(shooter, stack);
    }

    /**
     * Fabric seam: loading and firing only rewrite CUSTOM_DATA, so the held rifle never replays the equip animation.
     * Fabric 接缝：装填与开火只改写 CUSTOM_DATA，因此手持步枪不会重播换手动画。
     */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack,
                                                  ItemStack newStack) {
        return false;
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
        if (!(rifle.getItem() instanceof M1903A4Item) || !ammo.isOf(SparkWitchItems.ammo3006())) {
            return false;
        }
        if (getLoadedRounds(rifle) >= MAX_ROUNDS) {
            return false;
        }
        if (player.getWorld().isClient) {
            return true;
        }
        pushRound(rifle);
        if (!player.isCreative()) {
            ammo.decrement(1);
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            M1903A4Cooldowns.reload(serverPlayer);
            M1903A4Gunplay.onLoaded(serverPlayer);
            NbtCompound extra = new NbtCompound();
            extra.putString(M1903A4Rules.RECORD_ACTION, M1903A4Rules.ACTION_RELOAD);
            extra.putInt(M1903A4Rules.RECORD_LOADED_ROUNDS, getLoadedRounds(rifle));
            GameRecordManager.recordItemUse(serverPlayer, SparkWitchItems.M1903A4_ID, null, extra);
        }
        player.currentScreenHandler.sendContentUpdates();
        return true;
    }

    public static int getLoadedRounds(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return 0;
        }
        return Math.max(0, Math.min(customData.copyNbt().getInt(LOADED_ROUNDS_KEY), MAX_ROUNDS));
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
