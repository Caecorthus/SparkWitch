package dev.caecorthus.sparkwitch.mixin.cat;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.HashSet;
import java.util.UUID;

/**
 * Exposes Wathe's private dead-player set so a role can be revived from outside.
 * <p>Wathe only offers {@code markPlayerDead}, never the reverse; 薛定谔之猫 needs to clear its own entry when it
 * survives a kill, the same way SparkTraits' Last Stand does.
 * 暴露 Wathe 的私有死亡集合，供外部复活使用。Wathe 只提供 markPlayerDead，没有撤销；
 * 薛定谔之猫在被击杀后存活时需要清掉自己的记录，做法与 SparkTraits 的背水一战一致。
 */
@Mixin(value = GameWorldComponent.class, remap = false)
public interface GameWorldComponentDeadAccessor {

    @Accessor("deadPlayers")
    HashSet<UUID> sparkwitch$getDeadPlayers();
}
