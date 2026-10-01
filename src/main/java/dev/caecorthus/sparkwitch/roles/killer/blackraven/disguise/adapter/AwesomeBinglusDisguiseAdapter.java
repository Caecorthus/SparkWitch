package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import dev.doctor4t.wathe.index.WatheItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Disguise parity surface of Awesome Binglus: kit only. NoellesRoles force-disables the role, so it normally never
 * reaches the pool; the adapter still mirrors its kit exactly.
 * Binglus 的伪装对等面：仅开局物品。诺艾尔职业会强制禁用该职业，因此通常不会进入列表；适配器仍逐一复刻其物品。
 */
public final class AwesomeBinglusDisguiseAdapter implements BlackRavenDisguiseAdapter {
    public static final Identifier ROLE_ID = Identifier.of("noellesroles", "awesome_binglus");
    /** NoellesRoles gives sixteen separate default notes. / 诺艾尔职业逐张发放 16 张默认便条。 */
    static final int NOTE_COUNT = 16;

    @Override
    public Identifier roleId() {
        return ROLE_ID;
    }

    /** Replica of NoellesRoles' RoleAssigned AWESOME_BINGLUS kit. / 复刻诺艾尔职业 RoleAssigned Binglus 开局物品。 */
    @Override
    public List<ItemStack> firstKit(ServerPlayerEntity player) {
        List<ItemStack> kit = new ArrayList<>(NOTE_COUNT);
        for (int i = 0; i < NOTE_COUNT; i++) {
            kit.add(WatheItems.NOTE.getDefaultStack());
        }
        return List.copyOf(kit);
    }
}
