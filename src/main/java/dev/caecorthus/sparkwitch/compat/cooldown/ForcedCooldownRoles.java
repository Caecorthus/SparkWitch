package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Self-role lookup for the NoellesRoles stores: the raw role, or the acting role of a disguised Black Raven, because
 * the provider's own gates use {@code isRole}, which the acting overlay widens.
 * NoellesRoles 存储的自身职业查询：真实职业，或伪装中黑羽鸦的扮演职业，因为提供方自身的门控使用 isRole，而扮演覆盖层
 * 会放宽 isRole。
 */
final class ForcedCooldownRoles {
    private ForcedCooldownRoles() {
    }

    /** The first of {@code roleIds} this player currently plays as, or null. / 该玩家当前扮演的匹配职业 id，或 null。 */
    static @Nullable Identifier selfRoleIn(ServerPlayerEntity player, Collection<Identifier> roleIds) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role raw = game.getRole(player);
        if (raw != null && roleIds.contains(raw.identifier())) {
            return raw.identifier();
        }
        Role acting = BlackRavenActingRole.actingRole(player);
        // Widened by the Black Raven acting overlay; getRole stays raw. / 黑羽鸦扮演覆盖层会放宽此判定；getRole 仍为真实身份。
        if (acting != null && roleIds.contains(acting.identifier()) && game.isRole(player, acting)) {
            return acting.identifier();
        }
        return null;
    }
}
