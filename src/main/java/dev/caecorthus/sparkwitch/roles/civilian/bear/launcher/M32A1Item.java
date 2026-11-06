package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Milkor M32A1 six-shot 40 mm launcher ({@code sparkwitch:m32a1}). Anyone holding it may use it (owner
 * 2026-11-05, {@code OffMatchUse}); never a Wathe gun, so Wathe gun punishment, gun pickup and gun packets never apply.
 * Every stack carries {@link SparkWitchDataComponents#M32_CYLINDER}, empty by default, so a fresh stack is the empty
 * launcher BEAR starts with. Holding use aims down the M2A1 sight (client presentation only; the server's
 * {@code isUsingItem} merely matches the client); loading happens in the inventory ({@link M32CursorLoading}); firing is
 * the separate {@code sparkwitch:fire_m32a1} intent handled by {@link M32FireService}.
 * Milkor M32A1 六发 40 mm 榴弹发射器（{@code sparkwitch:m32a1}）。任何持有者都能使用（所有者 2026-11-05，{@code OffMatchUse}）；
 * 从不是 Wathe 枪械，因此 Wathe 的误杀惩罚、拾枪与开枪数据包都不适用。每个物品堆都带有
 * {@link SparkWitchDataComponents#M32_CYLINDER}，默认为空，因此新物品堆就是 BEAR 开局持有的空发射器。按住使用键通过 M2A1 瞄具
 * 瞄准（仅客户端表现；服务端的 {@code isUsingItem} 只是与客户端一致）；装填在背包中完成（{@link M32CursorLoading}）；开火是独立的
 * {@code sparkwitch:fire_m32a1} 意图，由 {@link M32FireService} 处理。
 */
public class M32A1Item extends Item {
    /** Aim hold length: effectively endless, use ends on release. / 瞄准持续时间：近乎无限，松开即结束。 */
    public static final int MAX_USE_TICKS = 72_000;
    static final String ROUNDS_TOOLTIP_KEY = "item.sparkwitch.m32a1.tooltip.rounds";
    private static final int TOOLTIP_LINES = 2;

    public M32A1Item(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1).component(SparkWitchDataComponents.M32_CYLINDER, M32Cylinder.EMPTY);
    }

    /** The stack's cylinder, {@link M32Cylinder#EMPTY} when absent. / 物品堆的转轮；缺失时为 {@link M32Cylinder#EMPTY}。 */
    public static M32Cylinder cylinder(ItemStack stack) {
        return stack.getOrDefault(SparkWitchDataComponents.M32_CYLINDER, M32Cylinder.EMPTY);
    }

    /**
     * Holding use only starts aiming (the client reads {@code isUsingItem()} for the sight); nothing else happens on
     * either side. Main hand only, because only the main hand fires. The server's cooldown check before {@code use}
     * is skipped for the M32A1 ({@code mixin/usec/ServerPlayerInteractionManagerUsecScopeMixin}, client half
     * {@code UsecRifleUseCooldownMixin}), so it still aims during its 1 s cooldown.
     * 按住使用键只会开始瞄准（客户端读取 {@code isUsingItem()} 显示瞄具）；两端都不做其他事。仅主手，因为只有主手能开火。服务端在
     * {@code use} 之前的冷却检查对 M32A1 跳过（{@code mixin/usec/ServerPlayerInteractionManagerUsecScopeMixin}，客户端一半为
     * {@code UsecRifleUseCooldownMixin}），因此在 1 秒冷却期间仍可瞄准。
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND) {
            return TypedActionResult.pass(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    /** No bow-pull or eating animation while aiming. / 瞄准时没有拉弓或进食动画。 */
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return MAX_USE_TICKS;
    }

    /**
     * Fabric seam: loading, unloading and firing only rewrite the cylinder component, so the held launcher never
     * replays the equip animation. / Fabric 接缝：装填、退弹与开火只改写转轮组件，因此手持发射器不会重播换手动画。
     */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack,
                                                  ItemStack newStack) {
        return false;
    }

    /** Left-click fires; the held launcher never breaks blocks. / 左键用于开火；手持发射器永不破坏方块。 */
    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        return false;
    }

    /** Cursor loading and unloading ({@link M32CursorLoading}). / 光标装填与退弹（{@link M32CursorLoading}）。 */
    @Override
    public boolean onClicked(ItemStack stack, ItemStack otherStack, Slot slot, ClickType clickType,
                             PlayerEntity player, StackReference cursorStackReference) {
        return M32CursorLoading.onLauncherClicked(stack, otherStack, slot, clickType, player, cursorStackReference);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(ROUNDS_TOOLTIP_KEY, cylinder(stack).loadedCount(), BearRules.M32_CHAMBERS)
                .formatted(Formatting.GRAY));
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.m32a1.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
