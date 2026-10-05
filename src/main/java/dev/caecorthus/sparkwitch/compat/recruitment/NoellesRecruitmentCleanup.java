package dev.caecorthus.sparkwitch.compat.recruitment;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.GrandWitchRecruitmentRoundComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.agmas.noellesroles.Noellesroles;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.assassin.AssassinPlayerComponent;
import org.agmas.noellesroles.bartender.BartenderPlayerComponent;
import org.agmas.noellesroles.bodyguard.BodyguardPlayerComponent;
import org.agmas.noellesroles.bomber.BomberPlayerComponent;
import org.agmas.noellesroles.corruptcop.CorruptCopPlayerComponent;
import org.agmas.noellesroles.demonhunter.DemonHunterPlayerComponent;
import org.agmas.noellesroles.detective.DetectivePlayerComponent;
import org.agmas.noellesroles.jester.JesterPlayerComponent;
import org.agmas.noellesroles.mermaid.MermaidPlayerComponent;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;
import org.agmas.noellesroles.noisemaker.NoisemakerPlayerComponent;
import org.agmas.noellesroles.partyanimal.PartyAnimalPlayerComponent;
import org.agmas.noellesroles.pathogen.PathogenPlayerComponent;
import org.agmas.noellesroles.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.reporter.ReporterPlayerComponent;
import org.agmas.noellesroles.serialkiller.SerialKillerPlayerComponent;
import org.agmas.noellesroles.shadowjester.ShadowJesterPlayerComponent;
import org.agmas.noellesroles.silencer.SilencerPlayerComponent;
import org.agmas.noellesroles.spiritualist.SpiritPlayerComponent;
import org.agmas.noellesroles.survivalmaster.SurvivalMasterPlayerComponent;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.agmas.noellesroles.voice.NoellesrolesVoiceChatPlugin;
import org.agmas.noellesroles.voodoo.VoodooPlayerComponent;
import org.agmas.noellesroles.vulture.VulturePlayerComponent;

/** Focused role-exit adapter for pinned NoellesRoles 1.7.6, not ResetPlayer.EVENT.
 * 锁定 NoellesRoles 1.7.6 的身份退出适配器，不调用全玩家重置事件。 */
public final class NoellesRecruitmentCleanup {
    private static boolean registered;

    private NoellesRecruitmentCleanup() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            for (var world : server.getWorlds()) {
                var position = GrandWitchRecruitmentRoundComponent.KEY.get(world).takeRelease(player.getUuid());
                if (position == null) continue;
                var swallowed = SwallowedPlayerComponent.KEY.get(player);
                var game = GameWorldComponent.KEY.get(world);
                if (swallowed.isSwallowed() && game.isRunning() && game.hasAnyRole(player)
                        && !game.isPlayerDead(player.getUuid())) {
                    player.teleport(world, position.x, position.y, position.z, player.getYaw(), player.getPitch());
                    swallowed.release(position);
                } else {
                    swallowed.reset();
                }
                NoellesrolesVoiceChatPlugin.removeFromVoiceChat(player.getUuid());
            }
        });
    }

    public static void exitRole(ServerPlayerEntity player) {
        // Release synchronously before reset: pinned reset queues a release then clears its list.
        // 必须先同步释放：锁定版本 reset 会排队释放后立即清空吞噬列表。
        TaotiePlayerComponent taotie = TaotiePlayerComponent.KEY.get(player);
        for (var uuid : taotie.getSwallowedPlayers()) {
            ServerPlayerEntity swallowed = player.getServer().getPlayerManager().getPlayer(uuid);
            if (swallowed != null) {
                SwallowedPlayerComponent.KEY.get(swallowed).release(player.getPos());
                NoellesrolesVoiceChatPlugin.removeFromVoiceChat(uuid);
                taotie.removeSwallowedPlayer(swallowed);
            } else {
                // Keep an offline victim's release durable until their next join, even across restart.
                // 离线受害者的释放记录持久化到下次重连，跨服务器重启保留。
                GrandWitchRecruitmentRoundComponent.KEY.get(player.getServerWorld())
                        .queueRelease(uuid, player.getPos());
            }
        }
        if (taotie.isTaotieMomentActive()) taotie.endTaotieMoment();
        taotie.reset();
        SpiritPlayerComponent.KEY.get(player).cancelProjection("recruited");
        SpiritPlayerComponent.KEY.get(player).reset();
        JesterPlayerComponent.KEY.get(player).reset();
        MorphlingPlayerComponent.KEY.get(player).reset();
        CorruptCopPlayerComponent.KEY.get(player).reset();
        if (GameWorldComponent.KEY.get(player.getServerWorld()).isRole(player, Noellesroles.MERMAID)) {
            MermaidPlayerComponent.KEY.get(player).reset();
        }
        var psycho = PlayerPsychoComponent.KEY.get(player);
        if (psycho.getPsychoTicks() > 0) psycho.stopPsycho();
        AbilityPlayerComponent.KEY.get(player).reset();
        AssassinPlayerComponent.KEY.get(player).reset();
        BartenderPlayerComponent.KEY.get(player).reset();
        BodyguardPlayerComponent.KEY.get(player).reset();
        DemonHunterPlayerComponent.KEY.get(player).reset();
        DetectivePlayerComponent.KEY.get(player).reset();
        NoisemakerPlayerComponent.KEY.get(player).reset();
        PartyAnimalPlayerComponent.KEY.get(player).reset();
        PathogenPlayerComponent.KEY.get(player).reset();
        RecallerPlayerComponent.KEY.get(player).reset();
        ReporterPlayerComponent.KEY.get(player).reset();
        SerialKillerPlayerComponent.KEY.get(player).reset();
        ShadowJesterPlayerComponent.KEY.get(player).reset();
        SilencerPlayerComponent.KEY.get(player).reset();
        SurvivalMasterPlayerComponent.KEY.get(player).reset();
        VoodooPlayerComponent.KEY.get(player).reset();
        VulturePlayerComponent.KEY.get(player).reset();
        // The bomb item is refunded by the already captured inventory snapshot; stop its timer too.
        // 炸弹物品已纳入库存退款快照，同时停止对应计时器。
        BomberPlayerComponent.KEY.get(player).reset();
        // Target-owned infection, silence and drink buffs deliberately survive; so do Traits the new role could roll
        // (the transaction strips the rest after RoleAssigned, via RecruitmentTraits).
        // 目标持有的感染、沉默与饮品效果刻意保留；新身份可抽到的特质同样保留（其余由招募事务在 RoleAssigned 之后经
        // RecruitmentTraits 移除）。
    }

    /** The living Shadow Jester partner a recruited Shadow Jester leaves behind; read before {@link #exitRole} clears
     * the pair. Real roles only, never a Black Raven acting overlay. Null when there is no such partner online.
     * 被招募的影子小丑留下的存活影子小丑搭档，须在 exitRole 清除配对之前读取。只读真实身份，不读黑羽鸦伪装覆盖层；
     * 搭档不在线、已死亡或已不是影子小丑时返回 null。 */
    public static @Nullable ServerPlayerEntity shadowPartnerLeftBehind(ServerPlayerEntity recruit) {
        GameWorldComponent game = GameWorldComponent.KEY.get(recruit.getServerWorld());
        var partnerUuid = ShadowJesterPlayerComponent.KEY.get(recruit).getPartnerUuid();
        if (game.getRole(recruit) != Noellesroles.SHADOW_JESTER || partnerUuid == null) return null;
        return recruit.getServerWorld().getPlayerByUuid(partnerUuid) instanceof ServerPlayerEntity partner
                && GameFunctions.isPlayerPlayingAndAlive(partner)
                && game.getRole(partner) == Noellesroles.SHADOW_JESTER ? partner : null;
    }

    /** Owner 2026-10-05: recruiting one Shadow Jester breaks the Shadow Oath and makes the partner a real Jester,
     * bound or not. Mirrors the pinned transform for a fallen unbound partner; a real Jester carries no gun, so the
     * oath derringer goes while the knife stays a dead trophy. The unscoped addRole is replayed as shadow_transform.
     * 所有者 2026-10-05：招募其中一个影子小丑即解除影誓，无论是否缔结，搭档都化身真正的小丑。照搬锁定版本中
     * 未缔结搭档倒下时的化身流程；真正的小丑没有枪，因此收回影誓德加林，短刀保留为废刀。未标注原因的 addRole
     * 在回放中记为 shadow_transform。 */
    public static void releaseShadowPartner(ServerPlayerEntity partner) {
        GameWorldComponent game = GameWorldComponent.KEY.get(partner.getServerWorld());
        game.addRole(partner, Noellesroles.JESTER);
        game.sync();
        JesterPlayerComponent.KEY.get(partner).reset();
        PlayerMoodComponent.KEY.get(partner).reset();
        ShadowJesterPlayerComponent.KEY.get(partner).markBetrayalTrophy();
        partner.getInventory().remove(stack -> stack.isOf(WatheItems.DERRINGER), -1, partner.getInventory());
        partner.sendMessage(Text.translatable("message.sparkwitch.recruitment.shadow_partner_released"), false);
        GameRecordManager.event("shadow_transform").actor(partner).record();
    }
}
