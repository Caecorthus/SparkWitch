package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.Objects;

/**
 * The Knight's cross sword, a knife-style stab that never cools down. Right-click raises it (Wathe's knife prepare
 * sound). Releasing after at least {@link #MIN_HOLD_TICKS} (11 ticks, 0.55 s, the same strict threshold as Wathe's
 * knife) makes the Knight's client pick the target from its own view, the way Wathe's knife does: a
 * {@link #STAB_RANGE}-block {@code ProjectileUtil.getCollision} ray whose nearest living player must be the duel
 * target, otherwise nothing is sent. The client then sends {@code sparkwitch:cross_sword_stab}, and
 * {@link CrossSwordStabService} revalidates the stab against the server's own release record and positions. Picking on
 * the client keeps moving targets hittable under lag.
 * 骑士的十字剑，刀式刺击，永不冷却。右键举起（Wathe 小刀的蓄力音效）。至少举起 {@link #MIN_HOLD_TICKS}
 * （11 tick，0.55 秒，与 Wathe 小刀同样严格）后松手，由骑士客户端按自身视角选靶，与 Wathe 小刀相同：
 * {@link #STAB_RANGE} 格的 {@code ProjectileUtil.getCollision} 射线上最近的存活玩家必须是决斗目标，否则不发送。
 * 随后客户端发送 {@code sparkwitch:cross_sword_stab}，由 {@link CrossSwordStabService} 依据服务端自己的松手记录与
 * 位置重新校验。在客户端选靶，延迟下移动中的目标依然能被刺中。
 */
public final class CrossSwordItem extends Item {
    public static final int MAX_USE_TICKS = 100;
    /** Same strict threshold as Wathe's knife: remaining < max - 10, i.e. at least 11 held ticks. / 与 Wathe 小刀相同的严格门槛：剩余 < 上限 - 10，即至少举 11 tick。 */
    public static final int MIN_HOLD_TICKS = 11;
    /** Wathe's knife stab distance. / Wathe 小刀的刺击距离。 */
    public static final double STAB_RANGE = 3.0D;

    private static final ClientRelease NO_CLIENT_RELEASE = (player, stack, heldTicks) -> {
    };
    private static volatile ClientRelease clientRelease = NO_CLIENT_RELEASE;

    public CrossSwordItem(Settings settings) {
        super(settings);
    }

    /**
     * Client-side release seam: common code must never reference client classes, so the client initializer
     * ({@code KnightSwordClient.register}) installs the stab sender once. On a dedicated server it stays a no-op.
     * 客户端松手接缝：通用代码不得引用客户端类，因此由客户端初始化（{@code KnightSwordClient.register}）安装一次刺击发送器。
     * 专用服务器上保持空操作。
     */
    @FunctionalInterface
    public interface ClientRelease {
        void onRelease(PlayerEntity player, ItemStack stack, int heldTicks);
    }

    /** Client init only. / 仅客户端初始化调用。 */
    public static void installClientRelease(ClientRelease hook) {
        clientRelease = Objects.requireNonNull(hook, "hook");
    }

    /** The client hold gate, shared by the release sender and the crosshair. / 客户端举剑门槛，松手发送与准星共用。 */
    public static boolean qualifiedHold(int heldTicks) {
        return heldTicks >= MIN_HOLD_TICKS && heldTicks <= MAX_USE_TICKS;
    }

    // No cooldown handling here (spec 无cd): no SparkWitch code sets one, the duel whitelists the sword, and the
    // forced-cooldown writers (Fiend aura, Abyss suppression, AC shell) skip it through the SparkFactionAPI item
    // exemption in SparkWitchForcedCooldowns. Removing a cooldown every tick would send a cooldown packet per tick.
    // 这里不处理冷却（规格“无cd”）：SparkWitch 中没有代码为它设置冷却，决斗白名单包含十字剑，强制冷却写入方（魔人光环、
    // 聆渊压制、AC 弹）经 SparkWitchForcedCooldowns 中的 SparkFactionAPI 物品豁免跳过它。每 tick 移除冷却会每 tick 发送冷却包。
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.setCurrentHand(hand);
        user.playSound(WatheSounds.ITEM_KNIFE_PREPARE, 1.0F, 1.0F);
        return TypedActionResult.consume(stack);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return MAX_USE_TICKS;
    }

    /**
     * Both sides: vanilla sends the release action before this runs on the client, so the server records its own
     * release first and the client's stab payload is checked against it.
     * 双端：原版在客户端执行此处之前先发送松手动作，因此服务端先记录自己的松手，再用它校验客户端的刺击包。
     */
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) {
            return;
        }
        int heldTicks = getMaxUseTime(stack, user) - remainingUseTicks;
        if (world.isClient) {
            clientRelease.onRelease(player, stack, heldTicks);
        } else if (player instanceof ServerPlayerEntity serverPlayer) {
            CrossSwordStabService.recordRelease(serverPlayer, stack, heldTicks);
        }
    }
}
