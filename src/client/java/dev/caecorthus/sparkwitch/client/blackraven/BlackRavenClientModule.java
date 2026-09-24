package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import net.minecraft.client.MinecraftClient;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/** Registers only Black Raven's client-owned handler and visual event. / 只注册黑羽鸦自有的客户端按键处理与视觉事件。 */
public final class BlackRavenClientModule {
    private static boolean registered;

    private BlackRavenClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        dev.caecorthus.sparkwitch.api.SparkWitchApi.installLastEscapeVisionRenderer(
                BlackRavenPerceptionScreenEffects::renderLastEscape);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BlackRavenPerceptionScreenEffects.close());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> BlackRavenPerceptionScreenEffects.close());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return SparkWitch.id("last_escape_compositor");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        MinecraftClient.getInstance().execute(BlackRavenPerceptionScreenEffects::close);
                    }
                });
        SecondaryAbilityRegistry.register(BlackRavenRules.ROLE_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                if (client.player != null) {
                    BlackRavenClientState.cycle(client.player);
                }
            }

            @Override
            public void tick(MinecraftClient client) {
                BlackRavenClientState.tick(client);
            }

            @Override
            public void reset() {
                BlackRavenClientState.reset();
                BlackRavenPerceptionScreenEffects.close();
            }
        });
        BlackRavenInstinctClientHooks.register();
    }
}
