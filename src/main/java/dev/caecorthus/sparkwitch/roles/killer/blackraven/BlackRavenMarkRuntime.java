package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;

/** Settles each delayed Feather Blade mark exactly once. */
public final class BlackRavenMarkRuntime {
    private BlackRavenMarkRuntime() {
    }

    public static void tick(ServerPlayerEntity victim, BlackRavenMarkPlayerComponent component) {
        if (!component.hasMark()) {
            return;
        }
        UUID currentMatch = BlackRavenMatch.currentId();
        if (currentMatch == null || !currentMatch.equals(component.matchUuid())
                || !GameFunctions.isPlayerPlayingAndAlive(victim)) {
            component.clear();
            return;
        }
        if (victim.getServerWorld().getTime() < component.expiryTick()) {
            return;
        }

        UUID markerUuid = component.markerUuid();
        component.clear();
        ServerPlayerEntity online = markerUuid == null || victim.getServer() == null
                ? null
                : victim.getServer().getPlayerManager().getPlayer(markerUuid);
        // Only a living, playing marker is passed as the killer. A dead marker (often already an active Wraith,
        // whom SparkFactionAPI's affect veto forbids touching the living) would otherwise cancel the kill; the
        // responsible UUID below still keeps the credit.
        // 只有存活且在局内的标记者才作为凶手传入。死亡的标记者（常常已成为活跃冤魂，SparkFactionAPI 的影响否决
        // 禁止其接触活人）否则会让击杀被取消；下方的责任 UUID 仍保留归属。
        ServerPlayerEntity killer = online != null && GameFunctions.isPlayerPlayingAndAlive(online) ? online : null;
        // Friendly-fire snapshot before the kill: a death may change factions (plan v2 §2.1). The mark still kills
        // after its Raven died or went offline; only the punishment needs a living, online marker.
        // 击杀前的误杀判定快照：死亡可能改变阵营（计划 v2 §2.1）。黑羽鸦死亡或离线后标记仍会击杀；只有惩罚需要在线且存活的标记者。
        GameWorldComponent game = GameWorldComponent.KEY.get(victim.getServerWorld());
        BlackRavenMarkPunishment.Snapshot snapshot = new BlackRavenMarkPunishment.Snapshot(
                killer == null ? null : SparkFactionApi.resolveEffectiveFaction(killer, game),
                SparkFactionApi.resolveEffectiveFaction(victim, game),
                game.isPlayerDead(victim.getUuid()));
        // Retain the marker UUID even when the online entity lookup fails. / 离线也保留清标前的责任 UUID。
        dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution.runWith(
                victim.getServerWorld(), markerUuid,
                () -> GameFunctions.killPlayer(victim, true, killer, GameConstants.DeathReasons.KNIFE));
        BlackRavenMarkPunishment.afterSettlement(killer, victim, game, snapshot);
    }
}
