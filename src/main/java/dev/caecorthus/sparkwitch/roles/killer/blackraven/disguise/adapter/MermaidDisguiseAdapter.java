package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.mermaid.MermaidPlayerComponent;

/**
 * Disguise parity surface of the Mermaid: a native passive. MermaidPlayerComponent.serverTick reads isRole, so the
 * acting overlay drives the water effects and MaxAir; leaving the identity must drop them immediately.
 * 美人鱼的伪装对等面：原生被动。MermaidPlayerComponent.serverTick 读取 isRole，扮演覆盖层即可驱动水中效果与
 * MaxAir；离开该身份时必须立即清除。
 */
public final class MermaidDisguiseAdapter implements BlackRavenDisguiseAdapter {
    public static final Identifier ROLE_ID = Identifier.of("noellesroles", "mermaid");

    @Override
    public Identifier roleId() {
        return ROLE_ID;
    }

    /** Resets the NoellesRoles Mermaid state on every exit reason. / 所有退出原因都重置 NoellesRoles 美人鱼状态。 */
    @Override
    public void onExit(ServerPlayerEntity player, DisguiseExitReason reason) {
        MermaidPlayerComponent.KEY.get(player).reset();
    }
}
