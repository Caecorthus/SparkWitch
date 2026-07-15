package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class ProphetRuntime {
    private static boolean registered;

    private ProphetRuntime() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Wathe marks death before spawnEntity, so ENTITY_LOAD preserves same-tick activation order.
        // Wathe 会先标记死亡再 spawnEntity，因此 ENTITY_LOAD 能保留同 tick 内的激活先后。
        ServerEntityEvents.ENTITY_LOAD.register(ProphetRuntime::onEntityLoad);
        // Wathe fires AFTER only after corpse spawning; cancellation therefore cannot preempt collection.
        // Wathe 只在尸体生成后触发 AFTER，因此取消窗口不会抢在尸体收集之前。
        KillPlayer.AFTER.register(ProphetRuntime::afterKill);
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (component.isDeathOmenActive() && !ProphetRules.isProphet(role)) {
            component.cancelDeathOmenWindow();
        }
    }

    public static void tick(ServerPlayerEntity player, WitchPlayerComponent component) {
        if (!component.isDeathOmenActive()) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (!ProphetRules.isProphet(role) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            component.cancelDeathOmenWindow();
            return;
        }
        component.tickDeathOmenWindow();
    }

    private static void onEntityLoad(Entity entity, ServerWorld world) {
        if (!(entity instanceof PlayerBodyEntity body)) {
            return;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        if (!ProphetRules.shouldRecordLoadedBody(
                (int) world.getTime(),
                body.getDeathGameTime(),
                gameComponent.isPlayerDead(body.getPlayerUuid()))) {
            return;
        }
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            WitchPlayerComponent component = WitchPlayerComponent.KEY.get(viewer);
            if (component.isDeathOmenActive()
                    && ProphetRules.isProphet(gameComponent.getRole(viewer))
                    && GameFunctions.isPlayerPlayingAndAlive(viewer)) {
                component.recordDeathOmenBody(body.getUuid());
            }
        }
    }

    private static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(victim);
        if (component.isDeathOmenActive()) {
            component.cancelDeathOmenWindow();
        }
    }
}
