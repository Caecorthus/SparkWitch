package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import dev.caecorthus.sparkwitch.compat.SparkStrengthDisguiseCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Disguise parity surface of the Attendant: kit only (the NoellesRoles room book plus, when SparkStrength is loaded,
 * its starter flashlight by registry id; absent means no flashlight).
 * 乘务员的伪装对等面：仅开局物品（诺艾尔职业房间手册；SparkStrength 已加载时按注册 id 追加开局手电筒，缺失则不发）。
 */
public final class AttendantDisguiseAdapter implements BlackRavenDisguiseAdapter {
    public static final Identifier ROLE_ID = Identifier.of("noellesroles", "attendant");

    @Override
    public Identifier roleId() {
        return ROLE_ID;
    }

    @Override
    public List<ItemStack> firstKit(ServerPlayerEntity player) {
        List<ItemStack> kit = new ArrayList<>(2);
        kit.add(AttendantBookFactory.create(player));
        // Mirrors SparkStrength AttendantFlashlightService: skip when a flashlight is already carried.
        // 镜像 SparkStrength AttendantFlashlightService：已携带手电筒时不再发放。
        if (!SparkStrengthDisguiseCompat.hasFlashlight(player)) {
            ItemStack flashlight = SparkStrengthDisguiseCompat.flashlightStack();
            if (!flashlight.isEmpty()) {
                kit.add(flashlight);
            }
        }
        return List.copyOf(kit);
    }
}
