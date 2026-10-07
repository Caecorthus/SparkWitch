package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.ModItems;

/**
 * Disguise parity surface of the Conductor: kit only. The master key works on the item alone, so no role seam is
 * needed; it is an ordinary identity item (stashed on switch, dropped on death like a real Conductor's).
 * 列车长的伪装对等面：仅开局物品。万能钥匙只依赖物品本身，无需职业接缝；它是普通身份物品
 * （切换时存入该身份，死亡时与真实列车长一样掉落）。
 */
public final class ConductorDisguiseAdapter implements BlackRavenDisguiseAdapter {
    public static final Identifier ROLE_ID = Identifier.of("noellesroles", "conductor");

    @Override
    public Identifier roleId() {
        return ROLE_ID;
    }

    /** Replica of NoellesRoles' RoleAssigned CONDUCTOR kit. / 复刻诺艾尔职业 RoleAssigned 列车长开局物品。 */
    @Override
    public List<ItemStack> firstKit(ServerPlayerEntity player) {
        return List.of(ModItems.MASTER_KEY.getDefaultStack());
    }
}
