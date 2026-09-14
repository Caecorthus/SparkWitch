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
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Authoritative factor lifecycle. Call death BEFORE role-clearing/Wraith conversion.
 * 权威因子生命周期：死亡回调必须先于身份清理和怨灵转换。 */
public final class WitchFactorService {
    public static final Identifier SKILL_ID = SparkWitch.id("witch_factor");
    public static final int COOLDOWN_TICKS = 400;

    private WitchFactorService() { }

    public static void beginRound(ServerWorld world, int openingParticipants) {
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
        component.state().begin(openingParticipants);
        component.sync();
    }

    public static void clearRound(ServerWorld world) {
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
        component.state().clear();
        component.sync();
    }

    /** Call after any role conversion, including the owner's, to revoke private client views immediately.
     * 任何身份转换（包括拥有者）之后调用，立即撤销客户端私密视图。 */
    public static void onRoleChanged(ServerPlayerEntity player) {
        for (ServerWorld world : player.getServer().getWorlds()) {
            WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
            removeInvalidOwners(world, component.state());
            component.sync();
        }
    }

    public static void onRecruited(ServerPlayerEntity player) {
        for (ServerWorld world : player.getServer().getWorlds()) {
            WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
            // Recruitment may also remove an owning Grand Witch's role.
            // 招募也可能撤销因子拥有者的大魔女身份。
            component.state().recoverForRecruitment(player.getUuid());
            component.sync();
        }
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        ServerPlayerEntity owner = context.player();
        ServerWorld world = owner.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!game.isRunning() || game.getRole(owner) != SparkWitchRoles.grandWitch() || !alive(owner)) {
            return WitchSkillUseResult.fail(null);
        }
        ServerPlayerEntity target = GrandWitchTargeting.findTarget(owner,
                context.target() == null ? null : context.target().getUuid());
        if (target == null || target == owner || target.getServerWorld() != world || !alive(target)) {
            return WitchSkillUseResult.fail(null);
        }
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
        removeInvalidOwners(world, component.state());
        // Search all dimensions: changing worlds must never permit a second factor on one player.
        // 检查所有维度，禁止换世界后在同一玩家身上重复施放。
        for (ServerWorld other : owner.getServer().getWorlds()) {
            if (WitchFactorWorldComponent.KEY.get(other).isFactorHolder(target.getUuid())) {
                return WitchSkillUseResult.fail(null);
            }
        }
        if (!component.state().spread(owner.getUuid(), target.getUuid())) {
            return WitchSkillUseResult.fail(null);
        }
        component.sync();
        return WitchSkillUseResult.success(COOLDOWN_TICKS, "message.sparkwitch.skill.witch_factor.spread");
    }

    /** Confirmed Wathe AFTER only; canceled lethal attempts must never call this method.
     * 仅由 Wathe 确认死亡的 AFTER 调用；被取消的致死尝试不得调用。 */
    public static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                 Identifier deathReason) {
        for (ServerWorld world : victim.getServer().getWorlds()) {
            WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
            WitchFactorState state = component.state();
            if (!state.active()) {
                continue;
            }
            boolean valid = killer != null && killer.getServerWorld() == world && alive(killer)
                    && GameWorldComponent.KEY.get(world).isRunning();
            if (valid) {
                for (ServerWorld other : victim.getServer().getWorlds()) {
                    if (other != world && WitchFactorWorldComponent.KEY.get(other).isFactorHolder(killer.getUuid())) {
                        valid = false;
                        break;
                    }
                }
            }
            Role killerRole = killer == null ? null
                    : GameWorldComponent.KEY.get(killer.getServerWorld()).getRole(killer);
            boolean recovered = killerRole == SparkWitchRoles.accomplice()
                    || killerRole == SparkWitchRoles.grandWitch();
            boolean changed = state.afterDeath(victim.getUuid(), killer == null ? null : killer.getUuid(), valid, recovered);
            changed |= removeInvalidOwners(world, state);
            if (changed) {
                component.sync();
            }
        }
    }

    /** On clients this reads only the recipient's private, sanitized CCA view.
     * 客户端仅读取当前接收者经过过滤的私密 CCA 视图。 */
    public static boolean isFactorHolder(PlayerEntity player) {
        if (player.getWorld().isClient) {
            return WitchFactorWorldComponent.KEY.get(player.getWorld()).isFactorHolder(player.getUuid());
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            for (ServerWorld world : serverPlayer.getServer().getWorlds()) {
                if (WitchFactorWorldComponent.KEY.get(world).isFactorHolder(player.getUuid())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isVisibleTo(PlayerEntity viewer, PlayerEntity target) {
        if (viewer.getWorld() != target.getWorld()) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        Role role = game.getRole(viewer);
        if (!game.isRunning() || (role != SparkWitchRoles.grandWitch() && role != SparkWitchRoles.accomplice())
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || GameFunctions.isPlayerSpectatingOrCreative(viewer)) {
            return false;
        }
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(viewer.getWorld());
        if (viewer.getWorld().isClient) {
            return component.isFactorHolder(target.getUuid());
        }
        WitchFactorState.Factor factor = component.state().factors().get(target.getUuid());
        return factor != null && (role == SparkWitchRoles.accomplice() || factor.owner().equals(viewer.getUuid()));
    }

    static void tick(ServerWorld world, WitchFactorWorldComponent component) {
        WitchFactorState state = component.state();
        if (!state.active()) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!game.isRunning()) {
            clearRound(world);
            return;
        }
        boolean changed = removeInvalidOwners(world, state);
        Iterator<Map.Entry<UUID, WitchFactorState.Factor>> iterator = state.factors().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, WitchFactorState.Factor> entry = iterator.next();
            UUID holderId = entry.getKey();
            WitchFactorState.Factor factor = entry.getValue();
            if (game.isPlayerDead(holderId) || !game.hasAnyRole(holderId)) {
                iterator.remove();
                changed = true;
                continue;
            }
            ServerPlayerEntity holder = world.getServer().getPlayerManager().getPlayer(holderId);
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(factor.owner());
            // Pause while either endpoint is offline/off-world. Never catch up missed rewards.
            // 任意一端离线或离开原世界时暂停，不追补离线收益。
            if (holder == null || owner == null || holder.getServerWorld() != world
                    || owner.getServerWorld() != world || !alive(holder) || !alive(owner)) {
                continue;
            }
            WitchFactorState.Factor advanced = factor.advance();
            entry.setValue(advanced);
            Role role = game.getRole(holder);
            boolean apprentice = role == SparkWitchRoles.apprenticeWitch();
            if (advanced.manaTicks() == 0) {
                WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(owner);
                mana.addMana(WitchFactorState.manaReward(apprentice, role == SparkWitchRoles.murderousWitch()));
            }
            if (advanced.moodTicks() == 0 && role != null && role.getMoodType() == Role.MoodType.REAL) {
                PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(holder);
                mood.setMood(mood.getMood() - WitchFactorState.moodLoss(apprentice));
            }
        }
        if (changed) {
            component.sync();
        }
    }

    private static boolean removeInvalidOwners(ServerWorld world, WitchFactorState state) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        return state.factors().entrySet().removeIf(e -> game.isPlayerDead(e.getValue().owner())
                || game.getRole(e.getValue().owner()) != SparkWitchRoles.grandWitch());
    }

    private static boolean alive(ServerPlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player);
    }
}
