package dev.caecorthus.sparkwitch.roles.killer.specter;

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

/**
 * Cowardice (怯懦), granted by the ghost trigger. While cowardly, the player counts as sanity below 25 for every
 * Specter mechanic: the Specter can kill them in the Otherworld even if their real bar is full, they receive the
 * peachwood sword / Specter x-ray during the window, and the Specter sees a blue-purple outline.
 * Synced to all clients because the Specter's client needs every target's flag for the outline.
 * 鬼魂触发器给予的怯懦。怯懦期间对所有幽魂机制视为理智低于 25：即使真实理智条是满的，幽魂也能在里世界击杀；
 * 里世界期间能拿到桃木剑并透视幽魂；幽魂视角显示蓝紫色描边。同步给所有客户端，因为幽魂客户端需要每个目标的标记来画描边。
 */
public final class SpecterCowardiceComponent implements AutoSyncedComponent {
    public static final ComponentKey<SpecterCowardiceComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("specter_cowardice"),
            SpecterCowardiceComponent.class
    );
    private static final String COWARD = "Coward";

    private final PlayerEntity player;
    private boolean coward;

    public SpecterCowardiceComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isCoward() {
        return coward;
    }

    /** Server only: grants cowardice and syncs. / 仅服务端：获得怯懦并同步。 */
    public void grant() {
        if (coward) {
            return;
        }
        coward = true;
        sync();
    }

    /** Death, reset or round end clears cowardice. / 死亡、重置或回合结束清空怯懦。 */
    public void clear() {
        if (!coward) {
            return;
        }
        coward = false;
        sync();
    }

    private void sync() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.sync(owner);
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (coward) {
            tag.putBoolean(COWARD, true);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        coward = tag.getBoolean(COWARD);
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(coward);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        coward = buf.readBoolean();
    }
}
