package dev.caecorthus.sparkwitch.roles.civilian.savior;

import java.util.List;
import java.util.Objects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The Savior's pistol. It copies Wathe's revolver by identity rather than instantiating it: the client picks the aimed
 * player (30 blocks, sleeping players included), sends {@code sparkwitch:savior_pistol_shoot}, applies the -4° recoil
 * and the local muzzle flash; the server revalidates and resolves the whole shot in {@link SaviorPistolService}.
 * Common code stays free of client classes through the {@link ClientShot} seam installed by the client initializer.
 * 救世主手枪。按“复制 Wathe 左轮”而不是实例化它：客户端选靶（30 格，含床上玩家），发送
 * {@code sparkwitch:savior_pistol_shoot}，施加 -4° 后坐与本地枪口火焰；服务端在 {@link SaviorPistolService} 中
 * 重新校验并完成整次射击。通用代码通过客户端初始化安装的 {@link ClientShot} 接缝保持不引用客户端类。
 */
public final class SaviorPistolItem extends Item {
    private static final ClientShot NO_CLIENT_SHOT = (player, stack) -> {
    };
    private static volatile ClientShot clientShot = NO_CLIENT_SHOT;

    public SaviorPistolItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    /** Client-only pick/send/recoil/flash seam; a dedicated server keeps the no-op. / 客户端选靶/发送/后坐/闪光的接缝；专用服务器保持空操作。 */
    @FunctionalInterface
    public interface ClientShot {
        void onShot(PlayerEntity player, ItemStack stack);
    }

    public static void installClientShot(ClientShot hook) {
        clientShot = Objects.requireNonNull(hook, "hook");
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(stack.getItem())) {
            return TypedActionResult.fail(stack);
        }
        if (world.isClient()) {
            // Presentation only: aim, recoil and flash happen here; the server decides everything.
            // 仅表现：瞄准、后坐与闪光在这里发生；一切判定由服务端决定。
            clientShot.onShot(user, stack);
        }
        // Wathe's revolver consumes rather than succeeds: CONSUME does not swing the hand. / 与 Wathe 左轮一致：CONSUME 不会触发挥手动作。
        return TypedActionResult.consume(stack);
    }

    /** Two-line role hint, matching the other SparkWitch weapon tooltips. / 两行职业提示，与其他 SparkWitch 武器提示一致。 */
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.savior_pistol.tooltip.line1")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.sparkwitch.savior_pistol.tooltip.line2")
                .formatted(Formatting.GRAY));
    }
}
