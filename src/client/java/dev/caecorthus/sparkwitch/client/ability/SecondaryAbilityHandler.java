package dev.caecorthus.sparkwitch.client.ability;

import net.minecraft.client.MinecraftClient;

/** Role-owned handler for SparkWitch's generic secondary ability key. / SparkWitch 通用第二技能键的角色自有处理器。 */
public interface SecondaryAbilityHandler {
    void onPressed(MinecraftClient client);

    default void tick(MinecraftClient client) {
    }

    default void reset() {
    }
}
