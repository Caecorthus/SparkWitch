package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkfactionapi.api.compat.NoellesHiddenEquipment;
import dev.caecorthus.sparkwitch.command.ForceAbilityCommand;
import dev.caecorthus.sparkwitch.command.ForcePromotionCommand;
import dev.caecorthus.sparkwitch.command.GhostSettingsCommand;
import dev.caecorthus.sparkwitch.command.SetManaCommand;
import dev.caecorthus.sparkwitch.command.WatheGhostDividendCommand;
import dev.caecorthus.sparkwitch.skill.SparkWitchBuiltInSkills;
import dev.caecorthus.sparkwitch.impl.SparkWitchEvents;
import dev.caecorthus.sparkwitch.net.SparkWitchPackets;
import dev.caecorthus.sparkwitch.net.SparkWitchVersionHandshake;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistEffects;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelEffects;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterEffects;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterEntities;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsEffects;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserFeatureService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SparkWitch implements ModInitializer {
    public static final String MOD_ID = "sparkwitch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SparkWitchSounds.register();
        HunterEffects.register();
        OrthopedistEffects.register();
        GuardianAngelEffects.register();
        FocusedFootstepsEffects.register();
        HunterEntities.register();
        SparkWitchItems.register();
        SparkWitchEntities.register();
        // NoellesRoles remains the packet-filter owner; FactionAPI only extends its hidden-item predicate.
        // NoellesRoles 仍负责装备包过滤，FactionAPI 这里只扩展其隐藏物品判定。
        NoellesHiddenEquipment.register(SparkWitchItems.perfumeEssence());
        NoellesHiddenEquipment.register(SparkWitchItems.cologne());
        NoellesHiddenEquipment.register(SparkWitchItems.blackRavenLedger());
        SparkWitchRoles.register();
        SparkWitchBuiltInSkills.register();
        SparkWitchPackets.register();
        CurserFeatureService.register();
        SparkWitchVersionHandshake.registerServer();
        SparkWitchEvents.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SparkWitchRoles.refreshAssassinGuessRoleOrder());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SetManaCommand.register(dispatcher);
            ForceAbilityCommand.register(dispatcher);
            ForcePromotionCommand.register(dispatcher);
            GhostSettingsCommand.register(dispatcher);
            WatheGhostDividendCommand.register(dispatcher);
        });
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
