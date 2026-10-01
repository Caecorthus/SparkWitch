package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistRules;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Disguise parity surface of the Orthopedist: the skill, recipient sync, HUD, outline and key dispatch read isRole.
 * The component cooldown keeps ticking across switches; only the first entry sets it, aligned to the round clock.
 * 骨科医生的伪装对等面：技能、接收方同步、HUD、描边与按键分发均读取 isRole。组件冷却跨切换持续计时；
 * 仅首次进入时按本局时钟对齐设置。
 */
public final class OrthopedistDisguiseAdapter implements BlackRavenDisguiseAdapter {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "orthopedist");

    @Override
    public Identifier roleId() {
        return ROLE_ID;
    }

    @Override
    public void onFirstEntry(ServerPlayerEntity player, FirstEntry entry) {
        OrthopedistPlayerComponent.KEY.get(player).setCooldownTicks(entry.aligned(OrthopedistRules.INITIAL_COOLDOWN_TICKS));
    }

    /**
     * The recipient gate now passes, so resend every player's public Bone Setting bit (as OrthopedistSkillService does
     * on assignment).
     * 接收门控现已放行，按 OrthopedistSkillService 分配时的做法补发所有玩家的公开正骨标记。
     */
    @Override
    public void onEnter(ServerPlayerEntity player, boolean firstEntry) {
        for (ServerPlayerEntity target : player.getServerWorld().getPlayers()) {
            OrthopedistPlayerComponent.KEY.sync(target);
        }
    }
}
