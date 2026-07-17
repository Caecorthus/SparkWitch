package dev.caecorthus.sparkwitch.mixin;

import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Wraith-only access to Wathe's corpse role snapshot field.
 * 仅供冤魂写入 Wathe 尸体身份快照字段。
 */
@Mixin(value = PlayerBodyEntity.class, remap = false)
public interface PlayerBodyEntityWraithAccessor {
    @Accessor("DEATH_ROLE")
    static TrackedData<String> sparkwitch$getDeathRole() {
        throw new AssertionError();
    }
}
