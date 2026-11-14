package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkfactionapi.api.replay.SparkReplayApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 亡魂时刻: revives bodies the Ferryman already ferried (怜悯) as Lost Souls beside him, gives them an empty (spent)
 * derringer, a crowbar and a knife, and marks them for everyone with server-spawned soul-fire particles. The moment ends
 * when every Lost Soul is dead (the Ferryman dies with them), when the Ferryman is gone (the souls die with him), or
 * when nobody outside the moment is left alive (the Ferryman and the souls win, decided by {@link FerrymanWinService}).
 * Both "dies with them" kills are forced and use a SparkTraits-terminal death reason, so no protection or Last Stand
 * can veto them. Server only.
 * 亡魂时刻：把摆渡人已经摆渡（怜悯）的遗体作为迷失的灵魂在他身边复活，发给它们一把空膛（已用过的）德林加、撬棍与匕首，
 * 并由服务端在其周身生成灵魂火粒子让全场看见。所有迷失的灵魂死亡时时刻结束（摆渡人随之死亡）；摆渡人消失时灵魂随他
 * 而去；时刻之外再无存活者时，摆渡人与迷失的灵魂获胜（由 {@link FerrymanWinService} 判定）。两种“随之而去”的死亡都是
 * 强制击杀，且死因已登记为 SparkTraits 终结原因，任何保护或背水一战都无法否决。仅服务端。
 */
public final class FerrymanMomentService {
    private static final String START_KEY = "message.sparkwitch.ferryman.moment.start";
    private static final String SOUL_KEY = "message.sparkwitch.ferryman.moment.soul";
    private static final String DENIED_KEY = "message.sparkwitch.ferryman.moment.denied";
    private static final String LOST_KEY = "message.sparkwitch.ferryman.moment.lost";
    private static final String WARCRY_KEY = "title.sparkwitch.lost_soul.warcry";

    /**
     * SparkFactionAPI replay cause for the Lost Soul role write; the Charon is its source player.
     * Lost Soul 职业写入时使用的 SparkFactionAPI 回放原因；来源玩家为摆渡人。
     */
    public static final Identifier REVIVE_REPLAY_CAUSE = SparkWitch.id("ferryman_moment_revive");

    private static boolean registered;

    private FerrymanMomentService() {
    }

    /** Wathe's internal dead-player set; null when the field is gone. / Wathe 内部死者集合；字段消失时为 null。 */
    private static final @Nullable Field DEAD_PLAYERS = resolveDeadPlayersField();

    /**
     * Installed by the optional voice-chat plugin: drops a revived soul out of every voice group, so it can never
     * keep talking in the dead channel after coming back. No-op when Simple Voice Chat is absent.
     * 由可选的语音插件安装：把复活的灵魂移出所有语音分组，避免他复活后仍在死者频道说话。没有语音模组时为空操作。
     */
    private static volatile java.util.function.Consumer<ServerPlayerEntity> voiceGroupReset = player -> {
    };

    public static void installVoiceGroupReset(java.util.function.Consumer<ServerPlayerEntity> reset) {
        voiceGroupReset = reset;
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(FerrymanMomentService::tick);
        // Round boundaries end the moment silently: Wathe already reset every player, so nobody is killed here.
        // 回合边界静默结束时刻：Wathe 已重置所有玩家，这里不再击杀任何人。
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                end(serverWorld);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                end(serverWorld);
            }
        });
        // A stopping server drops the moment without killing anyone. / 服务器停止时丢弃时刻，不击杀任何人。
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                end(world);
            }
        });
        // SparkTraits reads terminal reasons at kill time, so a Last Stand never turns "dies with them" into a revive;
        // registering at server start is early enough and idempotent (Fiend devoured / Bell toll precedent).
        // SparkTraits 在击杀时读取终结原因，背水一战因此不会把“随之而去”变成复活；服务端启动时注册即足够早且幂等
        // （与魔人被吞食、钟声的做法相同）。
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                SparkTraitsKillerBridge.registerTerminalDeathReason(SparkWitchDeathReasons.FERRYMAN_MOMENT_LOST));
    }

    /** Shop predicate: true means the purchase went through. / 商店谓词：返回 true 表示购买完成。 */
    static boolean tryStart(net.minecraft.entity.player.PlayerEntity buyer) {
        if (!(buyer instanceof ServerPlayerEntity ferryman) || !FerrymanParticipation.isActiveFerryman(ferryman)) {
            return false;
        }
        ServerWorld world = ferryman.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        FerrymanMomentWorldComponent moment = FerrymanMomentWorldComponent.get(world);
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(ferryman);
        List<ServerPlayerEntity> eligible = eligibleSouls(world, game, component);
        if (!FerrymanMomentRules.canStart(moment.isActive(), eligible.size())) {
            ferryman.sendMessage(Text.translatable(DENIED_KEY), true);
            return false;
        }
        Collections.shuffle(eligible);
        int wanted = FerrymanMomentRules.soulTarget(game.getAllPlayers().size(), eligible.size());
        Set<UUID> revived = new LinkedHashSet<>();
        for (ServerPlayerEntity soul : eligible) {
            if (revived.size() >= wanted) {
                break;
            }
            if (reviveAsSoul(soul, ferryman, game, component)) {
                revived.add(soul.getUuid());
            }
        }
        if (revived.isEmpty()) {
            // Nothing came back (Wathe's dead set unreachable): refuse the purchase so nothing is charged, and never
            // start a moment that would kill the Charon for a revival failure.
            // 没有任何灵魂复活（Wathe 死者集合不可达）：拒绝购买、不扣费，也绝不开启一个会因复活失败而处死摆渡人的时刻。
            SparkWitch.LOGGER.warn("Charon moment: no Lost Soul came back; the purchase is refused");
            ferryman.sendMessage(Text.translatable(DENIED_KEY), true);
            return false;
        }
        moment.start(ferryman.getUuid(), revived);
        broadcast(world, Text.translatable(START_KEY, revived.size()));
        return true;
    }

    /**
     * Online players that {@link FerrymanMomentRules#isEligibleSoul} accepts: ferried by this Charon, a round
     * participant Wathe holds dead, not a Charon, no SparkTraits Last Stand holding the death, not an active Wraith.
     * {@link FerrymanMomentRules#isEligibleSoul} 接受的在线玩家：被本摆渡人摆渡、Wathe 记为死亡的本局参与者、不是摆渡人、
     * 死亡未被 SparkTraits 背水一战持有、不是活跃冤魂。
     */
    private static List<ServerPlayerEntity> eligibleSouls(ServerWorld world, GameWorldComponent game,
                                                          FerrymanPlayerComponent component) {
        List<ServerPlayerEntity> eligible = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            UUID id = player.getUuid();
            if (!component.hasFerried(id)) {
                continue;
            }
            if (FerrymanMomentRules.isEligibleSoul(
                    true,
                    game.hasAnyRole(id),
                    game.isPlayerDead(id),
                    FerrymanParticipation.isFerryman(player),
                    SparkTraitsKillerBridge.isLastStandDeathIntercepted(player),
                    WraithStateService.isActive(player))) {
                eligible.add(player);
            }
        }
        return eligible;
    }

    /**
     * Brings one ferried player back as a Lost Soul and returns whether Wathe now reports it alive. Nothing about the
     * player is touched until Wathe's dead flag is really gone, so a failed revival leaves the corpse exactly as it was.
     * 把一名已摆渡的玩家作为迷失的灵魂带回，并返回 Wathe 是否已报告其存活。在 Wathe 的死亡标记确实消失之前不改动该玩家
     * 的任何状态，因此复活失败时遗体保持原样。
     */
    private static boolean reviveAsSoul(ServerPlayerEntity soul, ServerPlayerEntity ferryman, GameWorldComponent game,
                                        FerrymanPlayerComponent component) {
        ServerWorld world = soul.getServerWorld();
        UUID soulId = soul.getUuid();
        // Wathe's killPlayer both switches the player to spectator and records the uuid in the world component's dead
        // set, and that set has no public removal. While it holds the uuid, Wathe treats the player as dead and its own
        // tick puts them back into spectator, so it must be cleared first; once it is gone (and the role is set) Wathe
        // treats the soul as alive at once, with no grace or re-assertion needed.
        // Wathe 的 killPlayer 既把玩家切成旁观，又把 uuid 记进世界组件的死者集合，而该集合没有公开的移除方法。只要集合里
        // 还有该 uuid，Wathe 就把玩家当作死人，并由自身 tick 切回旁观，因此必须先清掉；清除（并写入职业）之后 Wathe 立即
        // 视灵魂为存活，无需宽限或反复施加。
        if (!clearDeadFlag(game, soul)) {
            return false;
        }
        SparkReplayApi.withRoleChangeCause(REVIVE_REPLAY_CAUSE, ferryman,
                () -> game.addRole(soul, SparkWitchRoles.lostSoul()));
        game.sync();
        if (!GameFunctions.isPlayerPlayingAndAlive(soul)) {
            // Defensive: the round left its running state under us. Put the death back and report the failure.
            // 防御：对局在此期间离开了运行状态。恢复死亡标记并报告失败。
            game.markPlayerDead(soulId);
            game.sync();
            return false;
        }
        // A risen soul is no longer a ferried corpse: its next body is ferried, paid and marked afresh.
        // 复活的灵魂不再是已摆渡的尸体：它的下一具遗体重新计入摆渡、报酬与标记。
        component.removeFerried(soulId);

        // Same body restore as SparkTraits' Last Stand and SparkStrength's T-Virus revive: ADVENTURE (Wathe's round
        // mode, so souls cannot break the train), full health, air and food, no fall distance, own camera.
        // 与 SparkTraits 背水一战、SparkStrength T 病毒复活相同的身体复原：冒险模式（Wathe 的对局模式，灵魂因此不能破坏
        // 列车）、满血、满氧气与饱食度、清除摔落距离、视角回到自身。
        soul.setCameraEntity(soul);
        soul.changeGameMode(GameMode.ADVENTURE);
        soul.clearStatusEffects();
        soul.setHealth(soul.getMaxHealth());
        soul.setAir(soul.getMaxAir());
        soul.getHungerManager().setFoodLevel(20);
        soul.fallDistance = 0.0F;
        soul.teleport(world, ferryman.getX(), ferryman.getY(), ferryman.getZ(), Set.of(),
                ferryman.getYaw(), 0.0F);
        soul.setVelocity(Vec3d.ZERO);
        soul.velocityModified = true;
        // A Lost Soul keeps nothing from its old life: the whole inventory, the cursor stack and any dropped-loadout
        // leftovers go first, then the role's own kit is issued.
        // 迷失的灵魂不带走生前任何东西：先清空整个物品栏、光标上的物品以及残留，再发放本职业的套装。
        clearInventory(soul);
        soul.getInventory().insertStack(emptyDerringer());
        soul.getInventory().insertStack(new ItemStack(WatheItems.CROWBAR));
        soul.getInventory().insertStack(new ItemStack(WatheItems.KNIFE));
        soul.getInventory().markDirty();
        soul.currentScreenHandler.sendContentUpdates();
        discardBodies(world, soulId);
        // Out of every voice group: a revived soul must not keep the dead channel it was moved into on death.
        // 退出所有语音分组：复活的灵魂不能继续留在死亡时被挪进的死者频道。
        try {
            voiceGroupReset.accept(soul);
        } catch (RuntimeException exception) {
            SparkWitch.LOGGER.warn("Charon moment: could not drop {} from its voice group",
                    soul.getGameProfile().getName(), exception);
        }
        soul.sendMessage(Text.translatable(SOUL_KEY), false);
        // The order of the moment, in the largest red type the client draws: the Lost Souls are here to clear the board.
        // 时刻的命令，用客户端能画出的最大红字宣布：迷失的灵魂是来清场的。
        soul.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 70, 15));
        soul.networkHandler.sendPacket(new TitleS2CPacket(
                Text.translatable(WARCRY_KEY).formatted(Formatting.RED, Formatting.BOLD)));
        return true;
    }

    /**
     * The design's "derringer with no rounds": Wathe's derringer is loaded unless {@code USED} is true, so the soul's
     * copy is issued spent (Knight precedent). Wathe's own reload-on-kill rule is left untouched.
     * 设计中的“没有子弹的德林加”：Wathe 的德林加在 {@code USED} 不为 true 时是上膛的，因此灵魂拿到的是已用过的一把
     * （骑士先例）。Wathe 自身的击杀后装填规则保持不变。
     */
    static ItemStack emptyDerringer() {
        ItemStack derringer = new ItemStack(WatheItems.DERRINGER);
        derringer.set(WatheDataComponentTypes.USED, true);
        return derringer;
    }

    /**
     * Removes the player from Wathe's per-round dead set through its field, resolved once and failing soft: a renamed
     * field only logs and the revival reports itself as failed instead of throwing every purchase. Success means Wathe
     * itself no longer reports the player dead.
     * 通过字段移除 Wathe 本局的死者记录，只解析一次、失败即降级：字段改名时只记日志，让复活按“失败”上报，而不是每次购买
     * 都抛异常。只有 Wathe 自己不再报告该玩家死亡时才算成功。
     */
    private static boolean clearDeadFlag(GameWorldComponent game, ServerPlayerEntity soul) {
        if (DEAD_PLAYERS == null) {
            return false;
        }
        try {
            Object store = DEAD_PLAYERS.get(game);
            if (store instanceof Set<?> dead) {
                dead.remove(soul.getUuid());
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            SparkWitch.LOGGER.warn("Charon moment: could not clear Wathe's dead flag for {}",
                    soul.getGameProfile().getName(), exception);
            return false;
        }
        return !game.isPlayerDead(soul.getUuid());
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
        List<Entity> bodies = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof PlayerBodyEntity body && owner.equals(body.getPlayerUuid())) {
                bodies.add(body);
            }
        }
        bodies.forEach(Entity::discard);
    }

    /**
     * Empties every slot the player owns: main inventory, hotbar, armour, off hand and the cursor stack, as vanilla
     * {@code /clear} does, so nothing from the old life survives into the Lost Soul.
     * 清空玩家所有槽位：主背包、快捷栏、护甲、副手与光标，行为同原版 {@code /clear}，让生前物品一件都留不进迷失的灵魂。
     */
    private static void clearInventory(ServerPlayerEntity soul) {
        soul.getInventory().clear();
        soul.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
        soul.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
        soul.currentScreenHandler.sendContentUpdates();
        soul.playerScreenHandler.onContentChanged(soul.getInventory());
    }

    private static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        for (ServerWorld world : server.getWorlds()) {
            FerrymanMomentWorldComponent moment = FerrymanMomentWorldComponent.get(world);
            if (!moment.isActive()) {
                continue;
            }
            ServerPlayerEntity owner = player(server, moment.owner());
            boolean ownerActive = owner != null && FerrymanParticipation.isActiveFerryman(owner);
            int soulsAlive = ownerActive ? pruneFallenSouls(server, moment) : 0;
            switch (FerrymanMomentRules.tickAction(ownerActive, moment.soulsEverAlive(), soulsAlive)) {
                case END_WITH_SOULS ->
                        // The Charon is gone (dead, offline or re-roled): every Lost Soul goes with him (owner spec).
                        // 卡戎不在了（死亡、离线或换了职业）：所有迷失的灵魂随他一同消亡（所有者设定）。
                        endWithSouls(world, moment);
                case END_WITHOUT_DEATH -> {
                    // No soul was ever verified alive: a revival failure, not a loss, so the Charon is not killed.
                    // 没有任何灵魂被确认复活：这是复活失败而不是战败，因此不处死摆渡人。
                    SparkWitch.LOGGER.warn("Charon moment: no Lost Soul came back; ending the moment without a death");
                    end(world);
                }
                case CHARON_FOLLOWS -> {
                    // Every Lost Soul is down: the Charon follows them, forced so no protection can veto it.
                    // 迷失的灵魂全部倒下：摆渡人随之而去，强制执行，任何保护都无法否决。
                    broadcast(world, Text.translatable(LOST_KEY));
                    moment.clear();
                    GameFunctions.killPlayer(owner, false, null, SparkWitchDeathReasons.FERRYMAN_MOMENT_LOST, true);
                }
                case CONTINUE ->
                        // The win itself is decided by FerrymanWinService on the win-condition phase.
                        // 胜负本身由 FerrymanWinService 在胜负判定阶段决定。
                        spawnSoulParticles(world, server, moment, now);
            }
        }
    }

    /**
     * Drops every soul Wathe no longer reports alive (dead or offline) and returns how many remain. A swallowed soul or
     * a Rift Gate occupant is alive in Wathe and stays.
     * 移除 Wathe 不再报告存活（死亡或离线）的灵魂，并返回剩余数量。被吞下的灵魂或传送门占位者在 Wathe 中仍存活，因此保留。
     */
    private static int pruneFallenSouls(MinecraftServer server, FerrymanMomentWorldComponent moment) {
        int alive = 0;
        for (UUID soulId : moment.souls()) {
            if (FerrymanMomentRules.isSoulAlive(player(server, soulId))) {
                alive++;
            } else {
                moment.removeSoul(soulId);
            }
        }
        return alive;
    }

    /**
     * Blue soul flames rise around every living, visible Lost Soul, every third tick: the lobby reads the threat from
     * the particles. Server side, so every client sees the same thing. An alive spectator (swallowed, inside a Rift
     * Gate) gets none, so the flames never give its hidden position away.
     * 每三 tick 在每名存活且可见的迷失灵魂周身升起蓝色灵魂火焰：全场从粒子读出威胁。由服务端发出，因此每个客户端看到的
     * 一致。存活的旁观者（被吞下、在传送门内）不生成粒子，火焰绝不会暴露其隐藏位置。
     */
    private static void spawnSoulParticles(ServerWorld world, MinecraftServer server, FerrymanMomentWorldComponent moment,
                                           long now) {
        if (now % 3L != 0L) {
            return;
        }
        for (UUID soulId : moment.souls()) {
            ServerPlayerEntity soul = player(server, soulId);
            if (!FerrymanMomentRules.isSoulAlive(soul) || soul.isSpectator() || soul.getServerWorld() != world) {
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

    /** Ends the moment without killing anyone (round boundary, server stop). / 结束时刻且不击杀任何人（回合边界、停服）。 */
    public static void end(ServerWorld world) {
        FerrymanMomentWorldComponent.get(world).clear();
    }

    /**
     * The moment ends because the Charon is gone: every Lost Soul Wathe still reports alive (swallowed ones included)
     * is killed with a forced, SparkTraits-terminal death, then the state is cleared. The names are copied before the
     * state is cleared, and the state is cleared before the kills so a re-entrant win check sees no moment.
     * 时刻因卡戎消失而结束：Wathe 仍报告存活的每一名迷失灵魂（包括被吞下的）都以强制、SparkTraits 终结的死因被杀死，然后
     * 清空状态。名单在清空前复制；清空发生在击杀之前，因此重入的胜负判定看不到时刻。
     */
    public static void endWithSouls(ServerWorld world, FerrymanMomentWorldComponent moment) {
        Set<UUID> souls = moment.souls();
        moment.clear();
        MinecraftServer server = world.getServer();
        for (UUID soulId : souls) {
            ServerPlayerEntity soul = player(server, soulId);
            if (FerrymanMomentRules.isSoulAlive(soul)) {
                GameFunctions.killPlayer(soul, false, null, SparkWitchDeathReasons.FERRYMAN_MOMENT_LOST, true);
            }
        }
    }

    private static void broadcast(ServerWorld world, Text message) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.sendMessage(message, false);
        }
    }
}
