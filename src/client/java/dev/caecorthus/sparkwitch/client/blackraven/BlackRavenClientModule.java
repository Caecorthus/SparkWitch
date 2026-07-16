package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import net.minecraft.client.MinecraftClient;

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
