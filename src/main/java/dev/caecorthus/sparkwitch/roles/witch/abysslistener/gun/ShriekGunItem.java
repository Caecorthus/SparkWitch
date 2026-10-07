package dev.caecorthus.sparkwitch.roles.witch.abysslistener.gun;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import java.util.List;
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

/**
 * Abyss Listener Shriek Gun (Taser template): a non-lethal, server-authoritative hitscan that any holder may fire
 * ({@link OffMatchUse}); the binding applies only to match participants. It never joins Wathe's gun tag or gun packet,
 * so no forged client packet can turn it into a lethal shot; vanilla's use-item packet (which carries the aim) is the
 * whole request.
 * 聆渊者的啸音铳（以电击枪为模板）：非致命、由服务端权威判定的直射道具，任何持有者都可开火（{@link OffMatchUse}）；
 * 绑定只约束对局参与者。它永不加入 Wathe 枪械标签或枪械数据包，因此伪造的客户端数据包无法把它变成致命射击；
 * 原版使用物品数据包（已携带瞄准方向）就是全部请求。
 */
public final class ShriekGunItem extends Item {
    private static final int TOOLTIP_LINES = 3;
    /** Local-only recoil, applied after vanilla captured the aim for the use packet. / 仅本地的后坐，在原版为使用数据包记录瞄准方向之后施加。 */
    private static final float RECOIL_PITCH = 3.0F;

    public ShriekGunItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            // Presentation only (swing + recoil); the server decides everything. / 仅表现（挥手与后坐）；一切由服务端决定。
            user.setPitch(user.getPitch() - RECOIL_PITCH);
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity shooter) || !(world instanceof ServerWorld serverWorld)) {
            return TypedActionResult.fail(stack);
        }
        OffMatchUse.Mode mode = ShriekGunService.fireMode(shooter, stack);
        if (mode == OffMatchUse.Mode.REFUSED) {
            return TypedActionResult.fail(stack);
        }
        if (mode == OffMatchUse.Mode.MATCH) {
            ShriekGunService.fire(shooter, serverWorld, this);
        } else {
            // Not a match participant (or a STARTING/STOPPING transition): sound and particles only, no one is hit.
            // 非对局参与者（或 STARTING/STOPPING 过渡阶段）：仅声音与粒子，不命中任何人。
            ShriekGunService.firePresentation(shooter, serverWorld);
        }
        // A miss costs the same as a hit, in every mode. Plain vanilla cooldown so SparkTraits Fast Hands applies.
        // 每种模式下未命中与命中代价相同。普通原版冷却，SparkTraits 快手可缩短。
        shooter.getItemCooldownManager().set(this, AbyssListenerRules.GUN_COOLDOWN_TICKS);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.shriek_gun.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
