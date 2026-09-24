package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchTargeting;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Shared factor authority, invoked before role-clearing death callbacks.
 * 共通因子的服务端权威；死亡结算早于身份清理。 */
public final class WitchFactorService {
    public static final Identifier SKILL_ID = SparkWitch.id("witch_factor");
    public static final Identifier EMMA_ROLE_ID = SparkWitch.id("emma");
    public static final Identifier VOODOO_ROLE_ID = Identifier.of("noellesroles", "voodoo");
    public static final int COOLDOWN_TICKS = 400;
    public static final int MANA_COST = 50;

    private WitchFactorService() { }

    public static void beginRound(ServerWorld world, int participants) {
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
        component.state().begin(participants, component.settings().limit(participants));
        component.sync();
    }

    public static void clearRound(ServerWorld world) {
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
        component.state().clear();
        component.sync();
    }

    public static boolean isEmma(Role role) {
        return role != null && EMMA_ROLE_ID.equals(role.identifier());
    }

    public static boolean isEligibleCarrier(PlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && role != SparkWitchRoles.grandWitch() && role != SparkWitchRoles.accomplice()
                && role != SparkWitchRoles.witchMaiden() && !isEmma(role)
                && !VOODOO_ROLE_ID.equals(role.identifier());
    }

    public static void onRoleChanged(ServerPlayerEntity player) {
        for (ServerWorld world : player.getServer().getWorlds()) {
            WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
            if (!isEligibleCarrier(player)) component.state().recover(player.getUuid());
            component.sync();
        }
    }

    public static void onRecruited(ServerPlayerEntity player) {
        for (ServerWorld world : player.getServer().getWorlds()) {
            WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
            component.state().recover(player.getUuid());
            component.sync();
        }
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        ServerPlayerEntity source = context.player();
        if (context.role() != SparkWitchRoles.grandWitch()
                || WitchPlayerComponent.KEY.get(source).getMana() < MANA_COST) {
            return WitchSkillUseResult.fail(null);
        }
        ServerPlayerEntity target = GrandWitchTargeting.findTarget(source,
                context.target() == null ? null : context.target().getUuid());
        if (target == null || !trySpread(source, target)) return WitchSkillUseResult.fail(null);
        WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(source);
        mana.setMana(mana.getMana() - MANA_COST);
        return WitchSkillUseResult.success(COOLDOWN_TICKS, "message.sparkwitch.skill.witch_factor.spread");
    }

    /** The caller owns costs and targeting; successful insertion alone spends the common quota.
     * 调用方负责消耗与选人；只有成功插入因子才扣除共用额度。 */
    public static boolean trySpread(ServerPlayerEntity source, ServerPlayerEntity target) {
        ServerWorld world = source.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Role role = game.getRole(source);
        if (!game.isRunning() || (role != SparkWitchRoles.grandWitch() && !isEmma(role))
                || target == source || target.getServerWorld() != world || !alive(source) || !alive(target)
                || !isEligibleCarrier(target) || isFactorHolder(target)
                || !dev.caecorthus.sparkfactionapi.api.SparkFactionApi.canAffectPlayer(source, target, SKILL_ID, game)) return false;
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
        if (!component.state().spread(source.getUuid(), target.getUuid())) return false;
        // Dormant identities never leave the server, even to their own recipient.
        // 潜伏因子的身份不离开服务端，包括其持有者本人。
        component.sync();
        return true;
    }

    public static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer, Identifier reason) {
        if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) return;
        boolean killerMarked = killer != null && isFactorHolder(killer);
        for (ServerWorld world : victim.getServer().getWorlds()) {
            WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
            boolean valid = killer != null && !killerMarked && killer.getServerWorld() == world
                    && alive(killer) && isEligibleCarrier(killer) && GameWorldComponent.KEY.get(world).isRunning();
            if (component.state().afterDeath(victim.getUuid(), killer == null ? null : killer.getUuid(), valid)) {
                component.sync();
            }
        }
    }

    /** Client queries read only this recipient's mature, sanitized view. / 客户端只读取当前接收者获准的成熟因子。 */
    public static boolean isFactorHolder(PlayerEntity player) {
        if (player.getWorld().isClient) {
            return WitchFactorWorldComponent.KEY.get(player.getWorld()).isFactorHolder(player.getUuid());
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            for (ServerWorld world : serverPlayer.getServer().getWorlds()) {
                if (WitchFactorWorldComponent.KEY.get(world).isFactorHolder(player.getUuid())) return true;
            }
        }
        return false;
    }

    public static boolean isNetworkViewer(PlayerEntity viewer) {
        if (!alive(viewer) || !GameWorldComponent.KEY.get(viewer.getWorld()).isRunning()) return false;
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(viewer.getWorld());
        if (viewer.getWorld().isClient) return component.hasNetworkView();
        Role role = GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        return component.state().active() && (role == SparkWitchRoles.grandWitch()
                || role == SparkWitchRoles.accomplice() || isEmma(role) || component.state().mature(viewer.getUuid()));
    }

    public static boolean isVisibleTo(PlayerEntity viewer, PlayerEntity target) {
        if (viewer.getWorld() != target.getWorld() || !alive(target) || !isNetworkViewer(viewer)) return false;
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(viewer.getWorld());
        return viewer.getWorld().isClient ? component.isFactorHolder(target.getUuid())
                : component.state().mature(target.getUuid());
    }

    public static int getRemaining(ServerWorld world) { return WitchFactorWorldComponent.KEY.get(world).state().remaining(); }
    public static int getLimit(ServerWorld world) { return WitchFactorWorldComponent.KEY.get(world).state().limit(); }
    public static boolean hasReachedSpeedThreshold(ServerWorld world) {
        return WitchFactorWorldComponent.KEY.get(world).state().speedUnlocked();
    }

    static void tick(ServerWorld world, WitchFactorWorldComponent component) {
        WitchFactorState state = component.state();
        if (!state.active()) return;
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!game.isRunning()) { clearRound(world); return; }
        Iterator<Map.Entry<UUID, WitchFactorState.Factor>> iterator = state.factors().entrySet().iterator();
        int matureCount = 0;
        while (iterator.hasNext()) {
            Map.Entry<UUID, WitchFactorState.Factor> entry = iterator.next();
            ServerPlayerEntity holder = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (holder != null && WitchFactorTraitsBridge.isDeathIntercepted(holder)) continue;
            if (game.isPlayerDead(entry.getKey()) || !game.hasAnyRole(entry.getKey())
                    || (holder != null && !isEligibleCarrier(holder))) {
                iterator.remove();
                continue;
            }
            // Offline/off-world holders pause; source presence is irrelevant.
            // 持有者离线或离开原世界时暂停，与来源是否存活无关。
            if (holder == null || holder.getServerWorld() != world || !alive(holder)) continue;
            WitchFactorState.Factor previous = entry.getValue();
            WitchFactorState.Factor factor = previous.advance();
            entry.setValue(factor);
            if (!previous.mature() && factor.mature()) {
                holder.sendMessage(Text.translatable("message.sparkwitch.factor.awakened"), false);
            }
            if (factor.mature()) matureCount++;
            Role role = game.getRole(holder);
            boolean apprentice = role == SparkWitchRoles.apprenticeWitch();
            if (factor.manaTicks() == 0) {
                int reward = WitchFactorState.manaReward(apprentice, role == SparkWitchRoles.murderousWitch());
                for (ServerPlayerEntity recipient : world.getPlayers()) {
                    if (alive(recipient) && game.getRole(recipient) == SparkWitchRoles.grandWitch()) {
                        WitchPlayerComponent.KEY.get(recipient).addMana(reward);
                    }
                }
            }
            if (factor.moodTicks() == 0 && role != null && role.getMoodType() == Role.MoodType.REAL) {
                PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(holder);
                mood.setMood(mood.getMood() - WitchFactorState.moodLoss(apprentice));
            }
        }
        state.observeMatureCarriers(matureCount);
    }

    static boolean alive(PlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player) && !GameFunctions.isPlayerSpectatingOrCreative(player);
    }
}
