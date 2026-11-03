package dev.caecorthus.sparkwitch.roles.civilian.noa;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/** 诺亚's own state: the painting cooldown shown on her HUD. 诺亚自己的状态：HUD 上的作画冷却。 */
public final class NoaComponent implements AutoSyncedComponent {
    public static final ComponentKey<NoaComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("noa_player"),
            NoaComponent.class
    );

    private final PlayerEntity player;
    private int paintCooldownSeconds;

    public NoaComponent(PlayerEntity player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(player);
    }

    public int getPaintCooldownSeconds() {
        return paintCooldownSeconds;
    }

    /** Server-driven; syncs only when the displayed second changes. 服务端驱动；秒数变化才同步。 */
    public void setPaintCooldownSeconds(int seconds) {
        int clamped = Math.max(0, seconds);
        if (clamped != paintCooldownSeconds) {
            paintCooldownSeconds = clamped;
            sync();
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeInt(paintCooldownSeconds);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        paintCooldownSeconds = buf.readInt();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        paintCooldownSeconds = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
    }
}
