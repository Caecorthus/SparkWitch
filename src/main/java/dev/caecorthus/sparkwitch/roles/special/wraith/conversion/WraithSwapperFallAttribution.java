package dev.caecorthus.sparkwitch.roles.special.wraith.conversion;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Resolves pushed-fall credit from NoellesRoles' confirmed Swapper replay record. */
public final class WraithSwapperFallAttribution {
    private static final Identifier SWAPPER_SKILL_ID = Identifier.of("noellesroles", "swapper");
    private static final int ATTRIBUTION_TICKS = 100;
    private static final int GROUND_CLEAR_DELAY_TICKS = 2;
    private static final Map<UUID, Attribution> ATTRIBUTIONS = new HashMap<>();
    private static boolean registered;

    private WraithSwapperFallAttribution() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(WraithSwapperFallAttribution::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    /** Observes a provider-owned record only after both teleports succeeded. / 仅观察双方传送成功后的提供方记录。 */
    public static void onRecordedSkillUse(
            ServerPlayerEntity actor,
            Identifier skillId,
            @Nullable ServerPlayerEntity target,
            @Nullable NbtCompound extra
    ) {
        if (!SWAPPER_SKILL_ID.equals(skillId)
                || extra == null
                || !"swap".equals(extra.getString("action"))
                || !extra.containsUuid("target1")
                || !extra.containsUuid("target2")) {
            return;
        }
        UUID first = extra.getUuid("target1");
        UUID second = extra.getUuid("target2");
        UUID actorUuid = actor.getUuid();
        if (first.equals(second)) {
            return;
        }
        long now = actor.getServer().getTicks();
        if (!actorUuid.equals(first)) {
            ATTRIBUTIONS.put(first, new Attribution(actorUuid, now));
        }
        if (!actorUuid.equals(second)) {
            ATTRIBUTIONS.put(second, new Attribution(actorUuid, now));
        }
    }

    public static @Nullable UUID peekResponsibleUuid(ServerPlayerEntity victim) {
        Attribution attribution = ATTRIBUTIONS.get(victim.getUuid());
        return attribution == null ? null : attribution.actorUuid();
    }

    public static void consumeResponsibleUuid(ServerPlayerEntity victim) {
        ATTRIBUTIONS.remove(victim.getUuid());
    }

    public static void clear() {
        ATTRIBUTIONS.clear();
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();
        Iterator<Map.Entry<UUID, Attribution>> iterator = ATTRIBUTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Attribution> entry = iterator.next();
            Attribution attribution = entry.getValue();
            long age = now - attribution.recordedAtTick();
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
            if (age >= ATTRIBUTION_TICKS
                    || age >= GROUND_CLEAR_DELAY_TICKS && (target == null || target.isOnGround())) {
                iterator.remove();
            }
        }
    }

    private record Attribution(UUID actorUuid, long recordedAtTick) {
    }
}
