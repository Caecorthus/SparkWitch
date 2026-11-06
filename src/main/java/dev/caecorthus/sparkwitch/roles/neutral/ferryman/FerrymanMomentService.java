package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 亡魂时刻: revives bodies the Ferryman already ferried (怜悯) as Lost Souls beside him, gives them a derringer with
 * no rounds, a crowbar and a knife, and makes them glow for everyone. The moment ends when every Lost Soul is dead
 * (the Ferryman dies with them) or when nobody outside the moment is left alive (the Ferryman and the souls win).
 * 亡魂时刻：把摆渡人已经摆渡（怜悯）的遗体作为迷失的灵魂在他身边复活，发给它们一把空德林加、撬棍与匕首，并让全场都能
 * 看见它们。所有迷失的灵魂死亡时时刻结束（摆渡人随之死亡）；时刻之外再无存活者时，摆渡人与迷失的灵魂获胜。
 */
public final class FerrymanMomentService {
    private static final String START_KEY = "message.sparkwitch.ferryman.moment.start";
    private static final String SOUL_KEY = "message.sparkwitch.ferryman.moment.soul";
    private static final String DENIED_KEY = "message.sparkwitch.ferryman.moment.denied";
    private static final String LOST_KEY = "message.sparkwitch.ferryman.moment.lost";

    private FerrymanMomentService() {
    }

    /** Wathe's internal dead-player set; null when the field is gone. / Wathe 内部死者集合；字段消失时为 null。 */
    private static final @Nullable Field DEAD_PLAYERS = resolveDeadPlayersField();

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FerrymanMomentService::tick);
    }

    /** Shop predicate: true means the purchase went through. / 商店谓词：返回 true 表示购买完成。 */
    static boolean tryStart(net.minecraft.entity.player.PlayerEntity buyer) {
        if (!(buyer instanceof ServerPlayerEntity ferryman) || !FerrymanParticipation.isActiveFerryman(ferryman)) {
            return false;
        }
        ServerWorld world = ferryman.getServerWorld();
        FerrymanMomentWorldComponent moment = FerrymanMomentWorldComponent.get(world);
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(ferryman);
        List<ServerPlayerEntity> eligible = eligibleSouls(world, component);
        if (!FerrymanMomentRules.canStart(moment.isActive(), eligible.size())) {
            ferryman.sendMessage(Text.translatable(DENIED_KEY), true);
            return false;
        }
        Collections.shuffle(eligible);
        int wanted = Math.min(FerrymanRules.soulCount(world.getPlayers().size()), eligible.size());
        Set<UUID> revived = new java.util.LinkedHashSet<>();
        for (ServerPlayerEntity soul : eligible.subList(0, wanted)) {
            reviveAsSoul(soul, ferryman);
            revived.add(soul.getUuid());
        }
        moment.start(ferryman.getUuid(), revived);
        broadcast(world, Text.translatable(START_KEY, revived.size()));
        return true;
    }

    /** Bodies the Ferryman ferried whose owners are dead and still online. / 摆渡人摆渡过、其主人已死亡且仍在线的遗体。 */
    private static List<ServerPlayerEntity> eligibleSouls(ServerWorld world, FerrymanPlayerComponent component) {
        List<ServerPlayerEntity> eligible = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (GameFunctions.isPlayerPlayingAndAlive(player) || !component.hasFerried(player.getUuid())) {
                continue;
            }
            if (FerrymanParticipation.isFerryman(player)) {
                continue;
            }
            eligible.add(player);
        }
        return eligible;
    }

    private static void reviveAsSoul(ServerPlayerEntity soul, ServerPlayerEntity ferryman) {
        ServerWorld world = soul.getServerWorld();
        // Wathe's killPlayer both switches the player to spectator and records the uuid in the world component's dead
        // set, and that set has no public removal. Leaving it there keeps the player dead for every Wathe check and
        // for the reset pass that puts a dead player back into spectator, so it must be cleared first.
        // Wathe 的 killPlayer 既把玩家切成旁观，又把 uuid 记进世界组件的死者集合，而该集合没有公开的移除方法。
        // 留在里面会让玩家对所有 Wathe 判定都算死人，并被重置流程重新切回旁观，因此必须先清掉。
        clearDeadFlag(soul);
        soul.changeGameMode(GameMode.SURVIVAL);
        soul.setCameraEntity(soul);
        soul.setHealth(soul.getMaxHealth());
        soul.getHungerManager().setFoodLevel(20);
        soul.clearStatusEffects();
        soul.teleport(world, ferryman.getX(), ferryman.getY(), ferryman.getZ(), Set.of(),
                ferryman.getYaw(), 0.0F);
        GameWorldComponent.KEY.get(world).addRole(soul, dev.caecorthus.sparkwitch.SparkWitchRoles.lostSoul());
        // A Lost Soul is announced by blue soul particles around it, not by a GLOWING outline every client renders.
        // 迷失的灵魂靠周身蓝色灵魂粒子被认出来，而不是所有人都渲染的发光描边。
        soul.getInventory().insertStack(new ItemStack(WatheItems.DERRINGER));
        soul.getInventory().insertStack(new ItemStack(WatheItems.CROWBAR));
        soul.getInventory().insertStack(new ItemStack(WatheItems.KNIFE));
        soul.currentScreenHandler.sendContentUpdates();
        discardBodies(world, soul.getUuid());
        soul.sendMessage(Text.translatable(SOUL_KEY), false);
    }

    /**
     * Removes the player from Wathe's per-round dead set through its field, once, failing soft: a renamed field only
     * logs and lets the revival report itself as failed instead of dying to a NoSuchFieldException every purchase.
     * 通过字段移除 Wathe 本局的死者记录，只解析一次、失败即降级：字段改名时只记日志，让复活按“失败”上报，
     * 而不是每次购买都抛 NoSuchFieldException。
     */
    private static void clearDeadFlag(ServerPlayerEntity soul) {
        try {
            if (DEAD_PLAYERS == null) {
                return;
            }
            Object store = DEAD_PLAYERS.get(GameWorldComponent.KEY.get(soul.getServerWorld()));
            if (store instanceof Set<?> dead) {
                dead.remove(soul.getUuid());
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            SparkWitch.LOGGER.warn("Charon moment: could not clear Wathe's dead flag for {}",
                    soul.getGameProfile().getName(), exception);
        }
    }

    private static @Nullable Field resolveDeadPlayersField() {
        try {
            Field field = GameWorldComponent.class.getDeclaredField("deadPlayers");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            SparkWitch.LOGGER.warn("Charon moment: Wathe's dead-player set is not reachable; revivals will fail",
                    exception);
            return null;
        }
    }

    private static void discardBodies(ServerWorld world, UUID owner) {
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof PlayerBodyEntity body && owner.equals(body.getPlayerUuid())) {
                body.discard();
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        for (ServerWorld world : server.getWorlds()) {
            FerrymanMomentWorldComponent moment = FerrymanMomentWorldComponent.get(world);
            if (!moment.isActive()) {
                continue;
            }
            ServerPlayerEntity owner = player(server, moment.owner());
            if (owner == null || !FerrymanParticipation.isActiveFerryman(owner)) {
                end(world, moment, false);
                continue;
            }
            // Grace: Wathe's death state can outlive the game-mode write for a tick or two, so a freshly revived soul
            // must never be mistaken for a dead one. During the grace the revival is re-asserted instead of judged.
            // 宽限：Wathe 的死亡状态可能比游戏模式写入晚一两 tick 才消失，因此刚复活的灵魂绝不能被当成死人。
            // 宽限期内只重新施加复活，不做死亡判定。
            if (moment.graceTicks() > 0) {
                moment.setGraceTicks(moment.graceTicks() - 1);
                for (UUID soulId : moment.souls()) {
                    ServerPlayerEntity soul = player(server, soulId);
                    if (soul != null && soul.isSpectator()) {
                        soul.changeGameMode(GameMode.SURVIVAL);
                        soul.setHealth(soul.getMaxHealth());
                    } else if (isSoulAlive(soul)) {
                        moment.markSoulAlive();
                    }
                }
                continue;
            }
            if (!moment.soulsEverAlive()) {
                // No soul ever came back: that is a revival failure, not a loss, so the Charon is not killed for it.
                // 没有任何灵魂复活成功：这是复活失败而不是战败，因此不能因此处死摆渡人。
                SparkWitch.LOGGER.warn("Ferryman moment: no Lost Soul came back; ending the moment without a death");
                end(world, moment, false);
                continue;
            }
            int soulsAlive = 0;
            for (UUID soulId : moment.souls()) {
                ServerPlayerEntity soul = player(server, soulId);
                if (isSoulAlive(soul)) {
                    soulsAlive++;
                } else {
                    SparkWitch.LOGGER.warn("Ferryman moment: lost soul {} did not survive its revival", soulId);
                    moment.removeSoul(soulId);
                }
            }
            spawnSoulParticles(world, server, moment, now);
            if (soulsAlive == 0) {
                // Every Lost Soul is down: the Ferryman follows them.
                // 迷失的灵魂全部倒下：摆渡人随之而去。
                broadcast(world, Text.translatable(LOST_KEY));
                GameFunctions.killPlayer(owner, false, null, SparkWitchDeathReasons.FERRYMAN_MOMENT_LOST);
                end(world, moment, false);
                continue;
            }
            if (FerrymanMomentRules.outsidersAlive(world, moment) == 0) {
                // The win itself is decided by FerrymanWinService on the win-condition phase.
                // 胜负本身由 FerrymanWinService 在胜负判定阶段决定。
                continue;
            }
        }
    }

    /** A Lost Soul counts as alive once it is out of spectator and has a body left. / 脱离旁观且有血量才算活着。 */
    private static boolean isSoulAlive(@Nullable ServerPlayerEntity soul) {
        return soul != null && !soul.isSpectator() && soul.isAlive() && soul.getHealth() > 0.0F;
    }

    /**
     * Blue soul flames rise around every living Lost Soul, every third tick: the lobby reads the threat from the
     * particles instead of a GLOWING outline painted for everyone. Server side, so every client sees the same thing.
     * 每三 tick 在每名存活的迷失灵魂周身升起蓝色灵魂火焰：全场从粒子读出威胁，而不是所有人共享的发光描边。
     * 由服务端发出，因此每个客户端看到的一致。
     */
    private static void spawnSoulParticles(ServerWorld world, MinecraftServer server, FerrymanMomentWorldComponent moment,
                                           long now) {
        if (now % 3L != 0L) {
            return;
        }
        for (UUID soulId : moment.souls()) {
            ServerPlayerEntity soul = player(server, soulId);
            if (!isSoulAlive(soul)) {
                continue;
            }
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    soul.getX(), soul.getY() + 1.1, soul.getZ(),
                    8, 0.45, 0.75, 0.45, 0.01);
        }
    }

    private static @Nullable ServerPlayerEntity player(MinecraftServer server, @Nullable UUID id) {
        return id == null ? null : server.getPlayerManager().getPlayer(id);
    }

    private static void end(ServerWorld world, FerrymanMomentWorldComponent moment, boolean keepSouls) {
        moment.clear();
    }

    public static void end(ServerWorld world) {
        end(world, FerrymanMomentWorldComponent.get(world), false);
    }

    private static void broadcast(ServerWorld world, Text message) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.sendMessage(message, false);
        }
    }
}
