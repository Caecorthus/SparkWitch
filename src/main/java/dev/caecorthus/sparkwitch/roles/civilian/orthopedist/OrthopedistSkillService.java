package dev.caecorthus.sparkwitch.roles.civilian.orthopedist;

import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.UUID;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Server-authoritative Orthopedist lifecycle, validation, effect application, and replay recording.
 * 骨科大夫的服务端权威生命周期、校验、效果应用与回放记录入口。
 */
public final class OrthopedistSkillService {
    private static final String ACTION_BONE_SETTING = "bone_setting";
    private static final String ACTION_HEAL_FRACTURE = "heal_fracture";
    private static boolean replayRegistered;

    private OrthopedistSkillService() {
    }

    public static synchronized void registerReplayFormatter() {
        if (replayRegistered) {
            return;
        }
        replayRegistered = true;
        ReplayRegistry.registerSkillFormatter(OrthopedistRules.ROLE_ID, OrthopedistSkillService::formatReplay);
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        OrthopedistPlayerComponent.KEY.get(player).assignForRole(role);
        if (isOrthopedist(role)) {
            // A newly assigned viewer may already track buffed players, so refresh their public effect bits now.
            // 新分配的观察者可能早已追踪带正骨的玩家，因此此时补发公开效果标记。
            for (ServerPlayerEntity target : player.getServerWorld().getPlayers()) {
                OrthopedistPlayerComponent.KEY.sync(target);
            }
        }
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        OrthopedistPlayerComponent.KEY.get(player).clear();
    }

    public static void use(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        Role role = game.getRole(caster);
        OrthopedistPlayerComponent component = OrthopedistPlayerComponent.KEY.get(caster);
        if (!game.isRunning()
                || !isOrthopedist(role)
                || !GameFunctions.isPlayerPlayingAndAlive(caster)
                || component.getCooldownTicks() > 0) {
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(caster)) {
            return;
        }

        ServerPlayerEntity target = OrthopedistTargeting.findAimedPlayer(caster);
        if (target == null || !OrthopedistRules.canAttempt(
                true,
                true,
                false,
                component.getCooldownTicks(),
                GameFunctions.isPlayerPlayingAndAlive(target),
                caster.canSee(target),
                caster.squaredDistanceTo(target)
        )) {
            caster.sendMessage(Text.translatable("message.sparkwitch.orthopedist.no_target"), true);
            return;
        }

        HunterPlayerComponent injury = HunterPlayerComponent.KEY.get(target);
        OrthopedistRules.TargetAction action = OrthopedistRules.targetAction(
                injury.getFractureLayers(),
                target.hasStatusEffect(OrthopedistEffects.boneSetting())
        );
        if (action == OrthopedistRules.TargetAction.REJECT_ALREADY_ACTIVE) {
            caster.sendMessage(Text.translatable(
                    "message.sparkwitch.orthopedist.already_active",
                    target.getDisplayName()
            ), true);
            return;
        }

        String replayAction;
        if (action == OrthopedistRules.TargetAction.HEAL_FRACTURE && injury.healOneFractureLayer()) {
            replayAction = ACTION_HEAL_FRACTURE;
        } else {
            target.addStatusEffect(new StatusEffectInstance(
                    OrthopedistEffects.boneSetting(),
                    OrthopedistRules.BONE_SETTING_TICKS,
                    0,
                    false,
                    true,
                    true
            ));
            OrthopedistPlayerComponent.KEY.get(target).refreshBoneSettingState();
            replayAction = ACTION_BONE_SETTING;
        }
        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                OrthopedistRules.SPEED_TICKS,
                0,
                false,
                true,
                true
        ));
        component.setCooldownTicks(OrthopedistRules.POST_USE_COOLDOWN_TICKS);
        recordUse(caster, target, replayAction);
    }

    private static void recordUse(ServerPlayerEntity caster, ServerPlayerEntity target, String action) {
        NbtCompound extra = new NbtCompound();
        extra.putString("action", action);
        GameRecordManager.recordSkillUse(caster, OrthopedistRules.ROLE_ID, target, extra);
    }

    private static Text formatReplay(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        String action = data.getString("action");
        String translationKey = switch (action) {
            case ACTION_HEAL_FRACTURE -> "replay.skill.sparkwitch.orthopedist.heal_fracture";
            case ACTION_BONE_SETTING -> "replay.skill.sparkwitch.orthopedist.bone_setting";
            default -> null;
        };
        if (translationKey == null) {
            return null;
        }

        UUID actorUuid = data.getUuid("actor");
        UUID targetUuid = data.getUuid("target");
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(
                translationKey,
                ReplayGenerator.formatPlayerName(actorUuid, playerInfo),
                ReplayGenerator.formatPlayerName(targetUuid, playerInfo)
        );
    }

    private static boolean isOrthopedist(Role role) {
        return role != null && OrthopedistRules.ROLE_ID.equals(role.identifier());
    }
}
