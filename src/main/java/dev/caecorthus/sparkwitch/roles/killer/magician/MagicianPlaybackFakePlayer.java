package dev.caecorthus.sparkwitch.roles.killer.magician;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.world.ServerWorld;
import java.util.UUID;

/** 只在服务端执行回放动作，不会被客户端追踪。 */
public final class MagicianPlaybackFakePlayer extends FakePlayer {
    private final UUID owner; private final UUID actor; private final String actorName;
    public MagicianPlaybackFakePlayer(ServerWorld world, GameProfile profile, UUID owner, UUID actor, String actorName) { super(world, profile); this.owner=owner; this.actor=actor; this.actorName=actorName; }
    public UUID owner() { return owner; } public UUID actor() { return actor; } public String actorName() { return actorName; }
    public void setReplayItemUseTimeLeft(int ticks) { this.itemUseTimeLeft = Math.max(0, ticks); }
}
