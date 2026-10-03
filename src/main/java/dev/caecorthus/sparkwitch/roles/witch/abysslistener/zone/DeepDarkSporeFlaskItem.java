package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Abyss Listener Deep Dark Spore Flask: a consumable throwable bought from the role's own shop. The server throws a
 * role-owned {@link DeepDarkSporeFlaskEntity}; vanilla's use-item packet (which carries the aim) is the whole request,
 * so no forged packet can open a zone anywhere. Any participant may throw a flask they hold; it has no cooldown.
 * 聆渊者的深暗孢瓶：从本职业商店购买的消耗型投掷物。服务端投出本职业自有的 {@link DeepDarkSporeFlaskEntity}；
 * 原版使用物品数据包（已携带瞄准方向）就是全部请求，因此伪造的数据包无法在任意位置展开领域。任何参与者都可以投出
 * 手中的孢瓶；它没有冷却。
 */
public final class DeepDarkSporeFlaskItem extends Item {
    private static final int TOOLTIP_LINES = 3;
    /** Same flight as the Shock Device. / 与电击装置相同的飞行参数。 */
    private static final float THROW_SPEED = 0.5F;
    private static final float THROW_DIVERGENCE = 1.0F;
    private static final float THROW_VOLUME = 0.5F;
    private static final float THROW_PITCH = 0.5F;

    public DeepDarkSporeFlaskItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(16);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity thrower) || !(world instanceof ServerWorld serverWorld)
                || !canThrow(thrower)
                || SparkTraitsKillerBridge.blocksWeaponAction(thrower, stack)) {
            return TypedActionResult.fail(stack);
        }
        DeepDarkSporeFlaskEntity flask = new DeepDarkSporeFlaskEntity(serverWorld, thrower);
        flask.setItem(stack.copyWithCount(1));
        flask.setVelocity(thrower, thrower.getPitch(), thrower.getYaw(), 0.0F, THROW_SPEED, THROW_DIVERGENCE);
        if (!serverWorld.spawnEntity(flask)) {
            return TypedActionResult.fail(stack);
        }
        GameRecordManager.recordItemUse(thrower, AbyssListenerRules.FLASK_ITEM_ID, null, null);
        stack.decrementUnlessCreative(1, thrower);
        serverWorld.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(),
                SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS, THROW_VOLUME, THROW_PITCH);
        return TypedActionResult.consume(stack);
    }

    /**
     * Server gate: an ACTIVE round (a throw during the end fade would be wasted) and a living, playing, non-spectator,
     * non-creative participant who is not an active Wraith.
     * 服务端门槛：对局处于 ACTIVE（结束淡出期间投掷只会白白浪费），且投掷者是存活、参与中、非旁观、非创造、非激活冤魂的参与者。
     */
    private static boolean canThrow(ServerPlayerEntity thrower) {
        return GameWorldComponent.KEY.get(thrower.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(thrower)
                && GameFunctions.isPlayerAliveAndSurvival(thrower)
                && !WraithStateService.isActive(thrower);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.deep_dark_spore_flask.tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
