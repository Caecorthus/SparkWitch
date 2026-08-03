package dev.caecorthus.sparkwitch.item.tofana;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

public final class TofanaProtectionService {
    private static final TofanaRetaliationQueue<Participant> RETALIATIONS = new TofanaRetaliationQueue<>();
    private static boolean registered;

    private TofanaProtectionService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> drainRetaliations());
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> clear());
        ServerWorldEvents.UNLOAD.register((server, world) -> clear());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    public static boolean protect(
            ServerPlayerEntity holder,
            @Nullable ServerPlayerEntity attacker,
            boolean forced
    ) {
        boolean distinctPlayerKiller = attacker != null && !holder.getUuid().equals(attacker.getUuid());
        boolean killerPlayingAndAlive = attacker != null && GameFunctions.isPlayerPlayingAndAlive(attacker);
        if (!TofanaRules.canProtect(forced, distinctPlayerKiller, killerPlayingAndAlive)
                || !consumeOne(holder)) {
            return false;
        }

        RETALIATIONS.enqueue(
                new Participant(holder, holder.getServerWorld()),
                new Participant(attacker, attacker.getServerWorld())
        );
        return true;
    }

    static void clear() {
        RETALIATIONS.clear();
    }

    private static void drainRetaliations() {
        RETALIATIONS.tick(attempt -> {
            Participant holder = attempt.holder();
            Participant attacker = attempt.attacker();
            ServerPlayerEntity currentHolder = attacker.world().getServer()
                    .getPlayerManager()
                    .getPlayer(holder.player().getUuid());
            ServerPlayerEntity currentAttacker = attacker.world().getServer()
                    .getPlayerManager()
                    .getPlayer(attacker.player().getUuid());
            if ((currentHolder != null && currentHolder.getServerWorld() != holder.world())
                    || currentAttacker == null
                    || currentAttacker.getServerWorld() != attacker.world()
                    || !GameFunctions.isPlayerPlayingAndAlive(currentAttacker)) {
                return;
            }
            GameFunctions.killPlayer(
                    currentAttacker,
                    true,
                    holder.player(),
                    TofanaRules.DEATH_REASON_ID
            );
        });
    }

    private static boolean consumeOne(ServerPlayerEntity holder) {
        for (ItemStack stack : holder.getInventory().offHand) {
            if (consume(stack, holder)) {
                return true;
            }
        }
        for (ItemStack stack : holder.getInventory().main) {
            if (consume(stack, holder)) {
                return true;
            }
        }
        return false;
    }

    private static boolean consume(ItemStack stack, ServerPlayerEntity holder) {
        if (stack.isEmpty() || !stack.isOf(SparkWitchItems.tofanaElixir())) {
            return false;
        }
        stack.decrement(1);
        holder.getInventory().markDirty();
        return true;
    }

    private record Participant(ServerPlayerEntity player, ServerWorld world) {
    }
}
