package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.GameRecordTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Specter skill two, server-authoritative: opens the 30 s Otherworld window and gives the caster Speed II,
 * invisibility and a Specter-owned shield of 3 layers per alive player with effective real sanity at or below 25
 * ({@link SpecterRules#isLowSanity}). The shield is not Wathe psycho mode: {@link #absorbLethalHit} spends one layer
 * per lethal hit at the position of Wathe's psycho armour (after every {@code KillPlayer.BEFORE} listener; forced
 * kills and {@code ShouldPiercePsychoArmour} reasons such as the Savior pistol/lightsaber go through).
 * 幽魂技能二，服务端权威：开启 30 秒里世界窗口，给予施放者速度 II、隐身，以及幽魂自有的护盾：每个存活且有效真实理智不高于
 * 25 的玩家（{@link SpecterRules#isLowSanity}）提供 3 层。护盾不是 Wathe 疯魔模式：{@link #absorbLethalHit} 在 Wathe 疯魔护甲
 * 的位置（所有 {@code KillPlayer.BEFORE} 监听之后）每次致命攻击消耗 1 层；强制击杀与 {@code ShouldPiercePsychoArmour} 死因
 * （如救世主手枪/光剑）直接穿过。
 */
public final class SpecterSecondSkillService {
    /**
     * Stable {@code shield_blocked} record source; the replay keys are
     * {@code replay.shield_blocked.sparkwitch.specter_otherworld[.by]}.
     * 稳定的 {@code shield_blocked} 记录来源；回放键为 {@code replay.shield_blocked.sparkwitch.specter_otherworld[.by]}。
     */
    public static final Identifier SHIELD_SOURCE_ID = SparkWitch.id("specter_otherworld");

    private SpecterSecondSkillService() {
    }

    public static void use(ServerPlayerEntity caster) {
        if (caster == null || !SpecterRules.mayCast(caster)) {
            return;
        }
        SpecterSecondSkillComponent component = SpecterSecondSkillComponent.KEY.get(caster);
        if (!component.canActivate()) {
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(caster)) {
            return;
        }
        ServerWorld world = caster.getServerWorld();
        if (!component.activate(countLowSanityPlayers(world) * SpecterRules.SECOND_SKILL_SHIELD_PER_LOW_SANITY)) {
            return;
        }
        // The world window is independent of the caster: it survives the Specter's death and only the timer/round end stops it.
        // 世界窗口独立于施法者：幽魂死亡不结束，只有计时或回合结束才关闭。
        SpecterOtherworldComponent.KEY.get(world).start(SpecterRules.SECOND_SKILL_DURATION_TICKS);
        // Low-sanity swords appear and non-killers are slowed as soon as the window opens.
        // 窗口一开，低理智玩家的桃木剑立刻到位，非杀手立刻缓慢。
        SpecterPeachwoodService.refresh(world);
        SpecterOtherworldService.refresh(world);
        GameRecordManager.recordSkillUse(caster, SpecterRules.OTHERWORLD_SKILL_ID, null, null);
    }

    /**
     * Called by {@code GameFunctionsSpecterShieldMixin} only for a non-forced, non-piercing kill: spends one shield
     * layer with Wathe's psycho-armour sound and a {@code shield_blocked} record, and answers true so the kill is
     * cancelled. False (no layer left, or no open window) leaves the kill untouched.
     * 仅由 {@code GameFunctionsSpecterShieldMixin} 在非强制、非穿盾的击杀中调用：消耗 1 层护盾，播放 Wathe 疯魔护甲音效并记录
     * {@code shield_blocked}，返回 true 以取消击杀。返回 false（无剩余护盾或窗口未开启）时击杀不受影响。
     */
    public static boolean absorbLethalHit(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                          Identifier deathReason) {
        SpecterSecondSkillComponent component = SpecterSecondSkillComponent.KEY.get(victim);
        if (!component.consumeShieldLayer()) {
            return false;
        }
        victim.playSoundToPlayer(WatheSounds.ITEM_PSYCHO_ARMOUR, SoundCategory.MASTER, 5.0F, 1.0F);
        var event = GameRecordManager.event(GameRecordTypes.SHIELD_BLOCKED)
                .actor(victim)
                .put("source", SHIELD_SOURCE_ID.toString())
                .put("death_reason", deathReason.toString())
                .putInt("armour_remaining", component.getShieldLayers());
        if (killer != null) {
            event.target(killer);
        }
        event.record();
        return true;
    }

    /**
     * Alive participants with effective real sanity at or below 25 ({@link SpecterRules#isLowSanity}); fake-mood roles
     * never count. / 有效真实理智不高于 25 的存活参与者（{@link SpecterRules#isLowSanity}）；虚假理智职业不计。
     */
    private static int countLowSanityPlayers(ServerWorld world) {
        int count = 0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()
                    || GameFunctions.isPlayerSpectatingOrCreative(player)
                    || !GameFunctions.isPlayerPlayingAndAlive(player)) {
                continue;
            }
            if (SpecterRules.isLowSanity(player)) {
                count++;
            }
        }
        return count;
    }
}
