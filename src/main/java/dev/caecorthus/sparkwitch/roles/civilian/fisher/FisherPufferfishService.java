package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PufferfishEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracked pufferfish at the tray: sanity sting, lifetime, sweeps. / 托盘河豚：扣理智、寿命、清扫。 */
public final class FisherPufferfishService {
    static final String TAG = "sparkwitch_fisher_pufferfish";
    private static final Identifier STING_ACTION = SparkWitch.id("fisher_pufferfish_sting");
    private static final Map<UUID, TrackedFish> FISH = new HashMap<>();
    private static final Map<UUID, Long> LAST_STING = new HashMap<>();
    private static boolean registered;

    private FisherPufferfishService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(FisherPufferfishService::tick);
        // A saved/unloaded orphan has no valid round owner; discard on load even outside a round sweep.
        // 存档或卸载后遗留的河豚没有有效对局归属；加载时立即移除，补足回合清扫无法遍历未加载区块的情况。
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (isTagged(entity) && !FISH.containsKey(entity.getUuid())) {
                entity.discard();
            }
        });
    }

    public static void spawnAt(ServerWorld world, BlockPos tray, ServerPlayerEntity fisher) {
        PufferfishEntity fish = EntityType.PUFFERFISH.create(world);
        if (fish == null) {
            return;
        }
        // Keep the vanilla entity/AI, but never let an environmental death produce edible loot.
        // 保留原版实体与 AI，但环境死亡也不能产生可食用掉落；这是原版 MobEntity 的空掉落表配置。
        NbtCompound data = new NbtCompound();
        fish.writeCustomDataToNbt(data);
        data.putString("DeathLootTable", "minecraft:empty");
        fish.readCustomDataFromNbt(data);
        fish.setInvulnerable(true);
        fish.setPersistent();
        fish.setSilent(true);
        fish.addCommandTag(TAG);
        fish.refreshPositionAndAngles(tray.getX() + 0.5, tray.getY() + 1.0, tray.getZ() + 0.5, 0f, 0f);
        FISH.put(fish.getUuid(), new TrackedFish(fish, fisher.getUuid(), now(world.getServer())));
        if (!world.spawnEntity(fish)) {
            FISH.remove(fish.getUuid());
            fish.discard();
        }
    }

    private static void tick(MinecraftServer server) {
        long now = now(server);
        LAST_STING.entrySet().removeIf(entry -> stingReady(now, entry.getValue()));
        for (TrackedFish tracked : new ArrayList<>(FISH.values())) {
            PufferfishEntity fish = tracked.fish();
            GameWorldComponent game = GameWorldComponent.KEY.get(fish.getWorld());
            if (fish.isRemoved() || !fish.isAlive() || !game.isRunning() || expired(now, tracked.spawnTick())) {
                fish.discard();
                FISH.remove(fish.getUuid());
                continue;
            }
            if (fish.getPuffState() <= 0) {
                continue;
            }
            ServerPlayerEntity fisher = server.getPlayerManager().getPlayer(tracked.fisher());
            for (ServerPlayerEntity target : ((ServerWorld) fish.getWorld()).getPlayers()) {
                Role role = game.getRole(target);
                if (!FisherParticipants.isLivingParticipant(target) || role == null || role.getMoodType() != Role.MoodType.REAL
                        || !fish.getBoundingBox().intersects(target.getBoundingBox())
                        || !stingReady(now, LAST_STING.get(target.getUuid()))) {
                    continue;
                }
                if (!target.getUuid().equals(tracked.fisher())
                        && (fisher == null || !SparkFactionApi.canAffectPlayer(fisher, target, STING_ACTION, game))) {
                    continue;
                }
                // One shared per-player cooldown prevents several fish from stacking stings within two seconds.
                // 所有河豚共享每名玩家的两秒冷却，防止多只河豚同时叠加扣理智。
                LAST_STING.put(target.getUuid(), now);
                PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(target);
                mood.setMood(mood.getMood() - FisherRules.PUFFERFISH_MOOD_DRAIN);
                target.playSoundToPlayer(SoundEvents.ENTITY_PUFFER_FISH_STING, SoundCategory.PLAYERS, 1f, 1f);
                target.sendMessage(Text.translatable("message.sparkwitch.fisher.pufferfish_sting"), true);
            }
        }
    }

    static boolean expired(long now, long spawnTick) {
        return now - spawnTick >= FisherRules.PUFFERFISH_LIFETIME_TICKS;
    }

    static boolean stingReady(long now, Long lastSting) {
        return lastSting == null || now - lastSting >= FisherRules.PUFFERFISH_STING_COOLDOWN_TICKS;
    }

    static void clearPlayer(UUID player) {
        for (TrackedFish tracked : new ArrayList<>(FISH.values())) {
            if (tracked.fisher().equals(player)) {
                tracked.fish().discard();
                FISH.remove(tracked.fish().getUuid());
            }
        }
        LAST_STING.remove(player);
    }

    static void sweepAll(MinecraftServer server) {
        for (TrackedFish tracked : FISH.values()) {
            tracked.fish().discard();
        }
        clearTracking();
        for (ServerWorld world : server.getWorlds()) {
            var tagged = new ArrayList<Entity>();
            for (Entity entity : world.iterateEntities()) {
                if (isTagged(entity)) {
                    tagged.add(entity);
                }
            }
            tagged.forEach(Entity::discard);
        }
    }

    /** After SERVER_STOPPED, release references without touching closed worlds. / 服务端停止后只释放引用，不访问已关闭世界。 */
    static void clearTracking() {
        FISH.clear();
        LAST_STING.clear();
    }

    private static boolean isTagged(Entity entity) {
        return entity instanceof PufferfishEntity && entity.getCommandTags().contains(TAG);
    }

    private static long now(MinecraftServer server) {
        return server.getOverworld().getTime();
    }

    private record TrackedFish(PufferfishEntity fish, UUID fisher, long spawnTick) {
    }
}
