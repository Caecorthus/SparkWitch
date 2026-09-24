package dev.caecorthus.sparkwitch.client.emma;

import dev.doctor4t.wathe.client.WatheClient;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.noellesroles.client.jester.JesterMomentClient;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;

/** Resolve displayed identities without recursively following another player's disguise.
 * 解析可见身份，不递归追踪被模仿玩家当前的伪装。 */
public final class EmmaVisibleName {
    private static boolean lookedUp;
    private static Method strengthName;
    private EmmaVisibleName() { }

    public static Text of(PlayerEntity target) {
        if (target.hasStatusEffect(StatusEffects.INVISIBILITY) || target.isInvisible() || JesterMomentClient.isActive()) {
            return hidden();
        }
        MorphlingPlayerComponent morph = MorphlingPlayerComponent.KEY.get(target);
        if (morph.corpseMode) return hidden();
        if (morph.getMorphTicks() > 0) return morph.disguise == null ? hidden() : profileName(morph.disguise);
        if (FabricLoader.getInstance().isModLoaded("sparkstrength")) {
            if (!lookedUp) {
                lookedUp = true;
                try {
                    strengthName = Class.forName("annina.sparkstrength.client.role.morphling.MorphlingAppearanceClientHelper")
                            .getMethod("resolveActiveDisplayName", PlayerEntity.class);
                } catch (ReflectiveOperationException | LinkageError ignored) { }
            }
            if (strengthName == null) return hidden();
            try {
                Object name = strengthName.invoke(null, target);
                if (name instanceof Text text) return text.copy();
            } catch (ReflectiveOperationException | LinkageError ignored) { return hidden(); }
        }
        return Text.literal(target.getGameProfile().getName());
    }

    private static Text profileName(UUID identity) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerListEntry entry = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(identity);
        if (entry == null && WatheClient.PLAYER_ENTRIES_CACHE != null) entry = WatheClient.PLAYER_ENTRIES_CACHE.get(identity);
        if (entry != null) return Text.literal(entry.getProfile().getName());
        PlayerEntity player = client.world == null ? null : client.world.getPlayerByUuid(identity);
        return player == null ? hidden() : Text.literal(player.getGameProfile().getName());
    }
    private static Text hidden() { return Text.literal("????????").formatted(Formatting.OBFUSCATED); }
}
