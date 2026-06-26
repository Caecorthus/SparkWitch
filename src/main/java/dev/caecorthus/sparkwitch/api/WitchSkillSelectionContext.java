package dev.caecorthus.sparkwitch.api;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public record WitchSkillSelectionContext(
        ServerWorld world,
        GameWorldComponent gameComponent,
        ServerPlayerEntity player,
        Role role
) {
}
