package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Owns Kidnapper drag cleanup wiring. / 只负责绑架者拖尸运行态清理接线。 */
public final class KidnapperDragLifecycle {
    private static boolean registered;

    private KidnapperDragLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        ServerTickEvents.END_WORLD_TICK.register(world ->
                world.getPlayers().forEach(KidnapperDragService::reconcile));
        UseItemCallback.EVENT.register(KidnapperDragLifecycle::tryThrowDraggedBodyWithItem);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                tryThrowDraggedBody(player, world, hand));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                tryThrowDraggedBody(player, world, hand));
        KillPlayer.AFTER.register((victim, killer, deathReason) -> KidnapperDragService.release(victim));
        ResetPlayer.EVENT.register(KidnapperDragService::release);
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                KidnapperDragService.release(serverPlayer);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                KidnapperDragService.release(handler.getPlayer()));
        GameEvents.ON_WIN_DETERMINED.register((world, component, status, neutralWinner) -> clearWorld(world));
        GameEvents.ON_FINISH_FINALIZE.register((world, component) -> clearWorld(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                server.getWorlds().forEach(KidnapperDragLifecycle::clearWorld));
    }

    private static void clearWorld(net.minecraft.world.World world) {
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.getPlayers().forEach(KidnapperDragService::release);
        }
    }

    /** Consumes only the exact Kidnapper carry gesture on both prediction and server authority. / 仅在客户端预测与服务端权威两端消费绑架者的精确搬运手势。 */
    private static TypedActionResult<ItemStack> tryThrowDraggedBodyWithItem(
            PlayerEntity player,
            World world,
            Hand hand
    ) {
        ItemStack stack = player.getStackInHand(hand);
        return tryThrowDraggedBody(player, world, hand) == ActionResult.PASS
                ? TypedActionResult.pass(stack)
                : TypedActionResult.success(stack, world.isClient);
    }

    private static ActionResult tryThrowDraggedBody(PlayerEntity player, World world, Hand hand) {
        if (hand != Hand.MAIN_HAND
                || !player.isSneaking()
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !KidnapperRules.isKidnapper(GameWorldComponent.KEY.get(world).getRole(player))
                || KidnapperDragService.findDraggedBody(player) == null) {
            return ActionResult.PASS;
        }
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            KidnapperDragService.throwDraggedBody(serverPlayer);
        }
        return ActionResult.SUCCESS;
    }
}
