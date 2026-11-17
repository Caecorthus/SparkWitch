package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 「“手”枪」 ({@code sparkwitch:sphinx_hao_hand_gun}), the Sphinx Hao's spring-fist toy pistol. It holds one fist
 * ({@link SparkWitchDataComponents#SPHINX_HAO_LOADED}). Use is vanilla's use-item packet, like the Taser, so there is no
 * C2S payload of its own: empty with a fist in the inventory loads it, empty without one dry-clicks, loaded fires
 * ({@link SphinxHaoGunService}). Only a living Sphinx Hao can use it; anyone else's use does nothing. It is deliberately
 * outside {@code wathe:guns}: no Wathe gun packet, innocent-shot punishment, gun pickup or gun drop applies.
 * 「“手”枪」（{@code sparkwitch:sphinx_hao_hand_gun}），狮身豪的弹簧拳头玩具手枪，可装一发拳头
 * （{@link SparkWitchDataComponents#SPHINX_HAO_LOADED}）。使用走原版使用物品数据包（与电击枪相同），因此没有自有 C2S 载荷：
 * 空膛且背包有拳头时装填、空膛无拳头时空响、已装填时发射（{@link SphinxHaoGunService}）。只有存活的狮身豪能使用；其他人使用
 * 无任何效果。刻意不加入 {@code wathe:guns}：Wathe 的开枪数据包、误杀惩罚、拾枪与掉枪规则都不适用。
 */
public final class SphinxHaoHandGunItem extends Item {
    static final String TOOLTIP_LOADED_KEY = "item.sparkwitch.sphinx_hao_hand_gun.tooltip.loaded";
    static final String TOOLTIP_EMPTY_KEY = "item.sparkwitch.sphinx_hao_hand_gun.tooltip.empty";
    static final String TOOLTIP_USAGE_KEY = "item.sparkwitch.sphinx_hao_hand_gun.tooltip.usage";
    /** Local-only recoil after vanilla captured the aim for the use packet. / 仅本地后坐，在原版记录瞄准之后施加。 */
    private static final float RECOIL_PITCH = 3.0F;
    private static final ClientFire NO_CLIENT_FIRE = player -> {
    };
    private static volatile ClientFire clientFire = NO_CLIENT_FIRE;

    /**
     * Client-only presentation hook: the local client used a loaded gun that passed the client-side gates (the held
     * model then shows the body only until the fist arrives). Never mutates the stack. / 仅客户端的表现钩子：本地客户端使用
     * 了通过客户端门槛的已装填手枪（手持模型随即只显示枪身，直到拳头到达）。从不修改物品堆。
     */
    @FunctionalInterface
    public interface ClientFire {
        void onFire(PlayerEntity player);
    }

    public SphinxHaoHandGunItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    /** Installed once by the client module. / 由客户端模块安装一次。 */
    public static void installClientFire(ClientFire hook) {
        clientFire = Objects.requireNonNull(hook, "hook");
    }

    /** Both sides: whether this gun holds a fist. / 双端：这把枪是否装有拳头。 */
    public static boolean isLoaded(@Nullable ItemStack stack) {
        return stack != null && Boolean.TRUE.equals(stack.get(SparkWitchDataComponents.SPHINX_HAO_LOADED));
    }

    /** Server only: an empty gun carries no component at all. / 仅服务端：空膛的枪不带该组件。 */
    static void setLoaded(ItemStack stack, boolean loaded) {
        if (loaded) {
            stack.set(SparkWitchDataComponents.SPHINX_HAO_LOADED, Boolean.TRUE);
        } else {
            stack.remove(SparkWitchDataComponents.SPHINX_HAO_LOADED);
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            // Presentation only; the server decides everything. Anyone else's use stays silent (PASS).
            // 仅表现；一切由服务端决定。其他人的使用保持无声（PASS）。
            if (!SphinxHaoGunService.mayUse(user)) {
                return TypedActionResult.pass(stack);
            }
            if (isLoaded(stack)) {
                user.setPitch(user.getPitch() - RECOIL_PITCH);
                clientFire.onFire(user);
            }
            return TypedActionResult.consume(stack);
        }
        if (!(user instanceof ServerPlayerEntity shooter) || !(world instanceof ServerWorld serverWorld)) {
            return TypedActionResult.pass(stack);
        }
        return SphinxHaoGunService.use(shooter, serverWorld, stack)
                ? TypedActionResult.consume(stack) : TypedActionResult.pass(stack);
    }

    /**
     * Fabric seam: loading and firing only flip the loaded component, so the held gun never replays the equip animation.
     * Fabric 接缝：装填与发射只切换装填组件，因此手持的枪不会重播换手动画。
     */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack,
                                                  ItemStack newStack) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(isLoaded(stack) ? TOOLTIP_LOADED_KEY : TOOLTIP_EMPTY_KEY)
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(TOOLTIP_USAGE_KEY).formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
