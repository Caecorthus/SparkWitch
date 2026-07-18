package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Applies one silent, non-refreshable delayed mark on right click. */
public final class FeatherBladeItem extends Item {
    public FeatherBladeItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity serverUser)) {
            return TypedActionResult.fail(stack);
        }
        ServerPlayerEntity target = BlackRavenTargeting.findAimedPlayer(serverUser);
        UUID matchId = BlackRavenMatch.currentId();
        BlackRavenMarkPlayerComponent mark = target == null ? null : BlackRavenMarkPlayerComponent.KEY.get(target);
        boolean allowed = BlackRavenRules.canMark(
                BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(world).getRole(serverUser)),
                GameFunctions.isPlayerPlayingAndAlive(serverUser),
                target != null && VendettaInteractionService.isOrdinaryAliveOrBoundKillerTarget(serverUser, target),
                target == serverUser,
                mark != null && mark.hasMark(),
                target != null && serverUser.canSee(target),
                target == null ? Double.POSITIVE_INFINITY : serverUser.squaredDistanceTo(target)
        );
        if (!allowed || matchId == null || mark == null
                || !mark.mark(serverUser.getUuid(), world.getTime() + BlackRavenRules.MARK_DURATION_TICKS, matchId)) {
            return TypedActionResult.fail(stack);
        }
        serverUser.getItemCooldownManager().set(this, BlackRavenRules.FEATHER_COOLDOWN_TICKS);
        return TypedActionResult.success(stack);
    }
}
