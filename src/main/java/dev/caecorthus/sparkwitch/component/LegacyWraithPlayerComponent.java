package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * Read-only holder for the former SparkTraits player component.
 * 仅只读承接旧 SparkTraits 玩家组件，绝不回写旧命名空间。
 */
public final class LegacyWraithPlayerComponent implements Component {
    public static final ComponentKey<LegacyWraithPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Identifier.of("sparktraits", "wraith_player"),
            LegacyWraithPlayerComponent.class
    );

    private final WraithPlayerState state = new WraithPlayerState();
    private boolean dataPresent;

    public LegacyWraithPlayerComponent(Object provider) {
    }

    boolean hasData() {
        return dataPresent;
    }

    void restoreInto(WraithPlayerState target) {
        target.restore(
                state.isActive(),
                state.isRestricted(),
                state.getCompletedTasks(),
                state.getAlignment(),
                state.isPromotionPending()
        );
    }

    void clear() {
        dataPresent = false;
        state.clear();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        dataPresent = true;
        boolean active = tag.getBoolean("WraithActive");
        WraithState.Alignment alignment = active
                ? WraithState.Alignment.fromSerializedName(tag.getString("WraithAlignment"))
                : null;
        state.restore(
                active,
                tag.getBoolean("WraithRestricted"),
                tag.getInt("WraithCompletedTasks"),
                alignment,
                tag.getBoolean("WraithPromotionPending")
        );
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Legacy ids are read-only migration inputs; canonical components are the sole writers.
        // 旧 id 仅用于迁移读取；只有规范组件可以写入。
    }
}
