package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

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
        soul.changeGameMode(GameMode.SURVIVAL);
        soul.setCameraEntity(soul);
        soul.setHealth(soul.getMaxHealth());
        soul.getHungerManager().setFoodLevel(20);
        soul.clearStatusEffects();
        soul.teleport(world, ferryman.getX(), ferryman.getY(), ferryman.getZ(), Set.of(),
                ferryman.getYaw(), 0.0F);
        GameWorldComponent.KEY.get(world).addRole(soul, dev.caecorthus.sparkwitch.SparkWitchRoles.lostSoul());
        // Everyone sees a Lost Soul; the mark is the GLOWING outline the whole lobby already renders.
        // 全场都能看见迷失的灵魂：直接借原版发光描边。
        soul.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        soul.getInventory().insertStack(new ItemStack(WatheItems.DERRINGER));
        soul.getInventory().insertStack(new ItemStack(WatheItems.CROWBAR));
        soul.getInventory().insertStack(new ItemStack(WatheItems.KNIFE));
        soul.currentScreenHandler.sendContentUpdates();
        discardBodies(world, soul.getUuid());
        soul.sendMessage(Text.translatable(SOUL_KEY), false);
    }

    private static void discardBodies(ServerWorld world, UUID owner) {
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof PlayerBodyEntity body && owner.equals(body.getPlayerUuid())) {
                body.discard();
            }
        }
    }

    private static void tick(MinecraftServer server) {
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
            int soulsAlive = 0;
            for (UUID soulId : moment.souls()) {
                ServerPlayerEntity soul = player(server, soulId);
                if (soul != null && GameFunctions.isPlayerPlayingAndAlive(soul)) {
                    soulsAlive++;
                } else {
                    moment.removeSoul(soulId);
                }
            }
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

    private static @Nullable ServerPlayerEntity player(MinecraftServer server, @Nullable UUID id) {
        return id == null ? null : server.getPlayerManager().getPlayer(id);
    }

    private static void end(ServerWorld world, FerrymanMomentWorldComponent moment, boolean keepSouls) {
        for (UUID soulId : moment.souls()) {
            ServerPlayerEntity soul = world.getServer().getPlayerManager().getPlayer(soulId);
            if (soul != null) {
                soul.removeStatusEffect(StatusEffects.GLOWING);
            }
        }
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
