package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The single loaded shell, stored as a stable key in the launcher's CUSTOM_DATA so it survives sync and reloads.
 * Reading never trusts an unknown key: it reads as empty.
 * 唯一的已装填炮弹，以稳定 key 存在炮筒的 CUSTOM_DATA 中，随同步与重载保留。读取时未知 key 一律视为未装填。
 */
public final class PotionLauncherLoad {
    /** Stable NBT key; do not rename. / 稳定的 NBT 键名，不得改名。 */
    public static final String LOADED_SHELL_KEY = "LoadedShell";

    private PotionLauncherLoad() {
    }

    public static Optional<PotionShellType> loaded(ItemStack launcher) {
        NbtComponent customData = launcher.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        NbtCompound nbt = customData.copyNbt();
        return nbt.contains(LOADED_SHELL_KEY) ? PotionShellType.byKey(nbt.getString(LOADED_SHELL_KEY)) : Optional.empty();
    }

    public static boolean isLoaded(ItemStack launcher) {
        return loaded(launcher).isPresent();
    }

    public static void setLoaded(ItemStack launcher, @Nullable PotionShellType type) {
        NbtComponent customData = launcher.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = customData == null ? new NbtCompound() : customData.copyNbt();
        if (type == null) {
            nbt.remove(LOADED_SHELL_KEY);
        } else {
            nbt.putString(LOADED_SHELL_KEY, type.key());
        }
        if (nbt.isEmpty()) {
            launcher.remove(DataComponentTypes.CUSTOM_DATA);
        } else {
            launcher.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }
    }
}
