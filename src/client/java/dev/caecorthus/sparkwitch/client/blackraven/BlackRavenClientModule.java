package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.net.OpenBlackRavenDisguiseS2CPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import net.minecraft.client.MinecraftClient;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/** Registers only Black Raven's client-owned handlers, disguise views, and visual events. / 只注册黑羽鸦自有的客户端处理、伪装视图与视觉事件。 */
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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BlackRavenPerceptionScreenEffects.close();
            BlackRavenDisguiseClientState.clearClient();
        });
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
        BlackRavenDisguiseInstinctClientHooks.register();
        BlackRavenDisguiseClientState.register();
        // Registering this receiver is also what makes the server's canSend check pass for the mask.
        // 注册该接收器同时让服务端对面具的 canSend 检查得以通过。
        ClientPlayNetworking.registerGlobalReceiver(OpenBlackRavenDisguiseS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (SparkWitchServerConnection.isConfirmedServer()) {
                        BlackRavenLedgerBookScreen.open(
                                context.client(), BlackRavenLedgerBookScreen.Tab.ABSENT, payload.session());
                    }
                }));
    }
}
