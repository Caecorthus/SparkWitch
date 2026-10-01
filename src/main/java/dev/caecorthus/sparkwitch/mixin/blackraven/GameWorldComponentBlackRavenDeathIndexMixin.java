package dev.caecorthus.sparkwitch.mixin.blackraven;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import java.util.UUID;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops a disguised Raven from the server acting index the moment markPlayerDead records it, so off-thread
 * isRole readers never see the disguise inside the death window. endForDeath still runs at KillPlayer.AFTER.
 * HEAD (not KillPlayer.BEFORE, which a protection can cancel): the concurrent index entry goes before the
 * non-thread-safe deadPlayers set changes. Removing an absent UUID is a no-op.
 * 在 markPlayerDead 记录死亡的瞬间将伪装黑羽鸦移出服务端扮演索引，使其他线程的 isRole 读取在死亡窗口内看不到伪装；
 * endForDeath 仍在 KillPlayer.AFTER 执行。注入 HEAD（而非可被保护效果取消的 KillPlayer.BEFORE）：
 * 先移除并发索引条目，再修改非线程安全的 deadPlayers 集合。移除不存在的 UUID 不产生任何效果。
 */
@Mixin(value = GameWorldComponent.class, remap = false)
public abstract class GameWorldComponentBlackRavenDeathIndexMixin {
    @Shadow @Final private World world;

    @Inject(method = "markPlayerDead(Ljava/util/UUID;)V", at = @At("HEAD"))
    private void sparkwitch$dropActingIndexOnDeath(UUID uuid, CallbackInfo ci) {
        if (!world.isClient) {
            BlackRavenActingRole.removeServer(uuid);
        }
    }
}
