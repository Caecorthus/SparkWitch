package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Owner-private acting overlay: widens ONLY isRole(UUID, Role) for a living disguised Black Raven and its exact
 * acting role (server: that UUID; client: the local player only). getRole, getAllWithRole, faction, win,
 * legality and serialization stay raw. Server authority; never @Redirect.
 * Wrapping the callee also covers isRole(PlayerEntity, Role), which delegates here in Wathe 1.5.6, and every
 * third-party isRole call; verifyBlackRavenActingRoleSeams pins those call sites in the pinned jars.
 * 仅限所有者的扮演覆盖层：只放宽 isRole(UUID, Role)，且仅对存活的伪装黑羽鸦及其确切扮演身份生效
 * （服务端按该 UUID；客户端仅限本地玩家）。getRole、getAllWithRole、阵营、胜负、合法性与序列化保持真实。
 * 包装被调用方即可覆盖 Wathe 1.5.6 中委托至此的 isRole(PlayerEntity, Role) 以及所有第三方 isRole 调用；
 * verifyBlackRavenActingRoleSeams 会锁定固定版本 jar 中的这些调用点。
 */
@Mixin(value = GameWorldComponent.class, remap = false)
public abstract class GameWorldComponentActingRoleMixin {
    @Shadow @Final private World world;
    @Shadow @Final private HashMap<UUID, Role> roles;
    @Shadow @Final private HashSet<UUID> deadPlayers;

    @WrapMethod(method = "isRole(Ljava/util/UUID;Ldev/doctor4t/wathe/api/Role;)Z")
    private boolean sparkwitch$actingRole(UUID uuid, Role role, Operation<Boolean> original) {
        if (original.call(uuid, role)) {
            return true;
        }
        boolean client = world.isClient;
        // Cheap empty/null fast path before touching the raw maps. / 在读取真实表之前先走廉价的空表/空值快速路径。
        if (!BlackRavenActingRole.matches(client, uuid, role)) {
            return false;
        }
        // Raw role must still be Black Raven and alive, so death-time hooks read the raw role.
        // 真实职业必须仍为黑羽鸦且存活，因此死亡时的钩子读取的是真实职业。
        return BlackRavenActingRole.widens(client, uuid, role, roles.get(uuid), deadPlayers.contains(uuid));
    }
}
