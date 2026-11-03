package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Stores the random killer identity an undercover is disguised as for this round.
 * 保存卧底本局伪装成的随机杀手身份。
 */
public final class UndercoverDisguiseComponent implements AutoSyncedComponent {
    public static final ComponentKey<UndercoverDisguiseComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("undercover_disguise"),
            UndercoverDisguiseComponent.class
    );

    private final PlayerEntity player;
    private @Nullable Identifier disguise;

    public UndercoverDisguiseComponent(PlayerEntity player) {
        this.player = player;
    }

    public @Nullable Identifier disguise() {
        return disguise;
    }

    public void setDisguise(@Nullable Identifier roleId) {
        if (roleId == null ? disguise == null : roleId.equals(disguise)) {
            return;
        }
        disguise = roleId;
        sync();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        if (disguise != null) {
            tag.putString("UndercoverDisguise", disguise.toString());
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        disguise = tag.contains("UndercoverDisguise", NbtElement.STRING_TYPE)
                ? Identifier.tryParse(tag.getString("UndercoverDisguise"))
                : null;
    }
}
