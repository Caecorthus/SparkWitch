package dev.caecorthus.sparkwitch.client.apprentice;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.net.UseApprenticePurifyC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * The Apprentice Witch's secondary key sends the empty Purify request (owner 2026-10-06 D3). Graduation, cooldown,
 * mana and aim are all checked by the server, which answers a locked or refused press on the action bar.
 * 预备魔女的副技能键发送空的净化请求（所有者 2026-10-06 D3）。出师、冷却、魔力与瞄准全部由服务端校验，未解锁或被拒绝时由
 * 服务端在动作栏回复。
 */
public final class ApprenticeClientModule {
    private static boolean registered;

    private ApprenticeClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        SecondaryAbilityRegistry.register(SparkWitchRoles.APPRENTICE_WITCH_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                if (client.player != null) {
                    ClientPlayNetworking.send(new UseApprenticePurifyC2SPayload());
                }
            }
        });
        registered = true;
    }
}
