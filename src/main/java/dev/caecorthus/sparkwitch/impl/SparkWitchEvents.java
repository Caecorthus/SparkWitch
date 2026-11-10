package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.roles.civilian.accountant.AccountantFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.athlete.AthleteFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaGunService;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaLifecycle;
import dev.caecorthus.sparkwitch.compat.SparkTraitsWraithBridge;
import dev.caecorthus.sparkwitch.component.PerfumerPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.economy.WitchEconomyService;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordCombatService;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordDashService;
import dev.caecorthus.sparkwitch.item.firepoker.FirePokerCombatService;
import dev.caecorthus.sparkwitch.item.firepoker.FirePokerFallAttributionService;
import dev.caecorthus.sparkwitch.item.sparekey.SpareKeyAttendantKit;
import dev.caecorthus.sparkwitch.item.sparekey.SpareKeyDoors;
import dev.caecorthus.sparkwitch.item.tofana.TofanaProtectionService;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticeFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceCombatService;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.discoverer.DiscovererService;
import dev.caecorthus.sparkwitch.roles.civilian.discoverer.DiscovererShopService;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightEconomyService;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightKillService;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelService;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorBlackoutService;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorDomainService;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorLoadoutService;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorObservationService;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorSanityService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerService;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistSkillService;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotLifecycle;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotService;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorPsychoGazeService;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorSkillService;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterCowardiceComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterFearComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldService;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterPeachwoodService;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterReplayService;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterRules;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterSecondSkillComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterShopService;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterSkillService;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaDisconnectService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaReplayService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaTerminalService;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumerEconomyService;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumerFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumerRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.recruit.RecruitRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumerShopService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.bewitched.BewitchedFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.bewitched.BewitchedPromotionService;
import dev.caecorthus.sparkwitch.mana.WitchManaService;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchFeature.MurderousWitchFeatureService;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFeatureService;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderFeatureService;
import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayFeatureService;
import dev.caecorthus.sparkwitch.roles.neutral.cat.SchrodingersCatService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodEconomyService;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodChaseRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetDeathLedger;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetProphecyService;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetEconomyService;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintEconomyService;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.tarotreader.TarotReaderFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.windspirit.WindSpiritFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellRingerFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowWorldFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperDragLifecycle;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperKnockoutService;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerFeatureService;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsRuntime;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateService;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenShopService;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackManager;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianReplayEvents;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetAttackHandlers;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianDecoyBodies;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianRoster;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.special.wraith.conversion.WraithConversion;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithLifecycle;
import dev.caecorthus.sparkwitch.skill.WitchSkillAssignmentService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class SparkWitchEvents {
    private static boolean registered;

    private SparkWitchEvents() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        JudgeRuntime.register();
        CeremonialSwordCombatService.register();
        CeremonialSwordDashService.register();
        GrandWitchFeatureService.register();
        EmmaLifecycle.register();
        EmmaGunService.register();
        MightyForceCombatService.register();
        FirePokerCombatService.register();
        TofanaProtectionService.register();
        WitchFactionFeatureService.register();
        MurderousWitchFeatureService.register();
        PigGodFeatureService.register();
        PigGodEconomyService.register();
        ProphetRuntime.register();
        ProphetDeathLedger.register();
        ProphetProphecyService.register();
        ProphetEconomyService.register();
        SaintFeatureService.register();
        PerfumerShopService.register();
        PerfumerFeatureService.register();
        PerfumerRuntime.register();
        PerfumerEconomyService.register();
        RecruitRuntime.register();
        AthleteFeatureService.register();
        NoaFeatureService.register();
        NinjaFeatureService.register();
        HunterFeatureService.register();
        OrthopedistSkillService.registerReplayFormatter();
        GuardianAngelFeatureService.register();
        VendettaInteractionService.register();
        VendettaDisconnectService.register();
        VendettaReplayService.register();
        VendettaTerminalService.register();
        KidnapperKnockoutService.register();
        KidnapperDragLifecycle.register();
        TarotReaderFeatureService.register();
        BlackRavenFeatureService.register();
        BlackRavenCrowFeatureService.register();
        BlackRavenCrowWorldFeatureService.register();
        BellRingerFeatureService.register();
        TimeStealerFeatureService.register();
        WindSpiritFeatureService.register();
        SaboteurFeatureService.register();
        FocusedFootstepsRuntime.register();
        WitchMaidenShopService.register();
        MiniWitherFeatureService.register();
        WraithLifecycle.register();
        ControlExpertFeatureService.register();
        KnightEconomyService.register();
        KnightFeatureService.register();
        KnightKillService.register();
        KnightDuelService.register();
        DominatorPsychoGazeService.register();
        DominatorForcedShotLifecycle.register();
        SpecterFeatureService.register();
        SpecterSkillService.register();
        SpecterPeachwoodService.register();
        SpecterOtherworldService.register();
        SpecterShopService.register();
        SpecterReplayService.register();
        // 夏目安安 (PR #194): runtime listeners, then its replay formatter (the fork kept it in a shared
        // SparkWitchReplayFormatters class that main does not have). 夏目安安（PR #194）：运行期监听器，然后是回放格式化器
        // （fork 中位于 main 没有的共享 SparkWitchReplayFormatters 类）。
        dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeService.register();
        dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeReplay.register();
        // Hypnosis item and skill lock (both sides). / 催眠的道具与技能锁（两端）。
        dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeHypnosisGuards.register();
        SaviorLoadoutService.register();
        SaviorSanityService.register();
        SaviorObservationService.register();
        SaviorBlackoutService.register();
        SeekerFeatureService.register();
        FisherFeatureService.register();
        FiendFeatureService.register();
        InsiderFeatureService.register();
        BlindFeatureService.register();
        AbyssListenerFeatureService.register();
        PotionGunnerFeatureService.register();
        RiftwalkerFeatureService.register();
        BewitchedFeatureService.register();
        ApprenticeFeatureService.register();
        StowawayFeatureService.register();
        SchrodingersCatService.register();
        JunkerService.register();
        // BEAR smoke (spike): round-edge clears, expiry and player resyncs. / BEAR 烟雾（原型）：回合节点清除、过期与玩家重新同步。
        dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudLifecycle.register();
        MagicianPlaybackManager.init();
        MagicianReplayEvents.register();
        MagicianPuppetAttackHandlers.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                DominatorForcedShotService.onRoleAssigned(serverPlayer);
                dev.caecorthus.sparkwitch.roles.civilian.medium.MediumPlayerComponent.KEY.get(serverPlayer)
                        .assignForRole(role);
                SpecterPlayerComponent.KEY.get(serverPlayer).assignForRole(role);
                SpecterFearComponent.KEY.get(serverPlayer).clear();
                SpecterSecondSkillComponent.KEY.get(serverPlayer).assignForRole(role);
                SpecterCowardiceComponent.KEY.get(serverPlayer).clear();
                if (SpecterRules.isSpecter(role)) {
                    // Cowardice syncs only to its owner and Specters: a new Specter needs every current flag now.
                    // 怯懦只同步给本人与幽魂：新的幽魂需要立即收到当前所有标记。
                    SpecterCowardiceComponent.resyncTo(serverPlayer);
                }
                PerfumerPlayerComponent.KEY.get(serverPlayer).clear();
                if (role != null && dev.caecorthus.sparkwitch.SparkWitchRoles.MAGICIAN_ID.equals(role.identifier())) {
                    // One sync with the final state instead of one per write. / 只用最终状态同步一次，而不是每次写入各同步一次。
                    MagicianPlayerComponent magician = MagicianPlayerComponent.KEY.get(serverPlayer);
                    magician.syncOnceAfter(() -> {
                        magician.reset();
                        magician.assignInitialCooldown();
                        // Mid-round assignments get the roster now; round start re-captures it once every role is dealt.
                        // 回合中途分配时立即记录名单；回合开始时会在全部身份发放后重新记录。
                        MagicianRoster.capture(serverPlayer);
                    });
                }
                ProphetRuntime.assignForRole(serverPlayer, role);
                ProphetEconomyService.assignForRole(serverPlayer, role);
                WitchSkillAssignmentService.assignForRole(serverPlayer, role);
                WitchManaService.assignForRole(serverPlayer, role);
                WitchFactionFeatureService.assignForRole(serverPlayer, role);
                MurderousWitchFeatureService.assignForRole(serverPlayer, role);
                PigGodEconomyService.assignForRole(serverPlayer, role);
                SaintFeatureService.assignForRole(serverPlayer, role);
                PerfumerEconomyService.assignForRole(serverPlayer, role);
                NinjaFeatureService.assignForRole(serverPlayer, role);
                OrthopedistSkillService.assignForRole(serverPlayer, role);
                DominatorSkillService.assignForRole(serverPlayer, role);
                SaviorDomainService.assignForRole(serverPlayer, role);
                if (!GuardianAngelRules.isGuardianAngel(role)) {
                    GuardianAngelFeatureService.detachPlayer(serverPlayer);
                }
            }
        });
        TaskComplete.EVENT.register(WitchManaService::onTaskComplete);
        DiscovererService.register();
        DiscovererShopService.register();
        AccountantFeatureService.register();
        SpareKeyDoors.register();
        SpareKeyAttendantKit.register();
        TaskComplete.EVENT.register((player, taskType) -> GrandWitchActiveSkillService.onTaskComplete(player));
        TaskComplete.EVENT.register((player, taskType) -> WitchEconomyService.onTaskComplete(player));
        // Bewitched promotion progress (C3): counts and enqueues only; the role changes at END_SERVER_TICK.
        // 魔化使晋升进度（C3）：只计数与入队；身份在 END_SERVER_TICK 变更。
        TaskComplete.EVENT.register((player, taskType) -> BewitchedPromotionService.onTaskComplete(player));
        TaskComplete.EVENT.register((player, taskType) -> PigGodEconomyService.onTaskComplete(player));
        TaskComplete.EVENT.register((player, taskType) -> ProphetEconomyService.onTaskComplete(player));
        TaskComplete.EVENT.register((player, taskType) -> SaintEconomyService.onTaskComplete(player));
        TaskComplete.EVENT.register((player, taskType) -> PerfumerEconomyService.onTaskComplete(player));
        KillPlayer.AFTER.register(WraithConversion::afterKill);
        KillPlayer.AFTER.register(WitchManaService::afterKill);
        KillPlayer.AFTER.register(WitchEconomyService::afterKill);
        // A SparkTraits-intercepted death (Last Stand) is not a death: no use for the killer, and a revived Dominator
        // keeps uses and mark (Time Stealer / Seeker precedent). / SparkTraits 拦截的死亡（背水一战）不算死亡：击杀者不加次数，
        // 复活的支配者保留次数与标记（窃时者、搜寻者先例）。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (!WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                DominatorSkillService.afterKill(victim, killer, deathReason);
            }
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            WitchFactionFeatureService.clearPlayerRuntime(victim);
            FirePokerFallAttributionService.clearPlayer(victim);
            PigGodChaseRuntime.clear(victim, WitchPlayerComponent.KEY.get(victim));
            PerfumerPlayerComponent.KEY.get(victim).stopCologne();
            if (!WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                // A Last Stand survivor keeps fear, the Otherworld state and cowardice (Dominator / Time Stealer /
                // Seeker precedent). / 背水一战存活者保留恐惧、里世界状态与怯懦（支配者、窃时者、搜寻者先例）。
                SpecterFearComponent.KEY.get(victim).clear();
                SpecterSecondSkillComponent.KEY.get(victim).clear();
                SpecterCowardiceComponent.KEY.get(victim).clear();
                // A Last Stand survivor keeps the Medium cooldowns and 招魂 bindings (same precedent).
                // 背水一战存活者保留通灵师冷却与招魂绑定（同一先例）。
                dev.caecorthus.sparkwitch.roles.civilian.medium.MediumPlayerComponent.KEY.get(victim).clear();
            }
            OrthopedistSkillService.clearPlayer(victim);
            if (!WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                DominatorSkillService.clearPlayer(victim);
            }
        });
        ResetPlayer.EVENT.register(player -> {
            WitchFactionFeatureService.clearPlayerRuntime(player);
            FirePokerFallAttributionService.clearPlayer(player);
            WitchPlayerComponent.KEY.get(player).clear();
            MagicianPlayerComponent.KEY.get(player).reset();
            PerfumerPlayerComponent.KEY.get(player).clear();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                // ResetPlayer fires after Wathe cleared the inventory: the stash is discarded, never re-inserted.
                // ResetPlayer 在 Wathe 清空物品栏之后触发：原物品被丢弃，绝不重新放入。
                DominatorForcedShotService.discard(serverPlayer);
                SpecterFearComponent.KEY.get(serverPlayer).clear();
                SpecterSecondSkillComponent.KEY.get(serverPlayer).clear();
                SpecterCowardiceComponent.KEY.get(serverPlayer).clear();
                dev.caecorthus.sparkwitch.roles.civilian.medium.MediumPlayerComponent.KEY.get(serverPlayer).clear();
                OrthopedistSkillService.clearPlayer(serverPlayer);
                DominatorSkillService.clearPlayer(serverPlayer);
                GuardianAngelFeatureService.clearRoundPlayer(serverPlayer);
                if (WraithStateService.isActive(serverPlayer)) {
                    // SparkTraits observes active Wraith state while dispatching terminal trait removals.
                    // SparkTraits 分发终局天赋移除事件时必须仍能观察到激活的冤魂状态。
                    SparkTraitsWraithBridge.clear(serverPlayer, true);
                }
                WraithLifecycle.clearPlayer(serverPlayer);
            }
        });
        GameEvents.ON_FINISH_INITIALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                WraithConversion.beginRound(serverWorld, gameComponent.getAllPlayers().size());
                MagicianRoster.captureAll(serverWorld);
                SpecterOtherworldComponent.KEY.get(serverWorld).clear();
                // A window from the previous round never survives into a new one.
                // 上一局的投票窗口绝不带入新一局。
                dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceComponent.KEY.get(serverWorld).clear();
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                // Round end clears only SparkWitch runtime state; role maps and other mods remain owned by wathe.
                // 回合结束只清理 SparkWitch 运行态，身份表和其他模组状态仍由 wathe 自己管理。
                WitchWorldComponent.KEY.get(serverWorld).clearRoundState();
                FirePokerFallAttributionService.clearAll();
                SpecterOtherworldComponent.KEY.get(serverWorld).clear();
                dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceComponent.KEY.get(serverWorld).clear();
                WraithLifecycle.clearRoundState(serverWorld);
                PoisonApplePlateService.clearLoadedPlates();
                MagicianPlaybackManager.clearAll();
                MagicianDecoyBodies.clear();
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    WitchFactionFeatureService.clearPlayerRuntime(player);
                    WitchPlayerComponent.KEY.get(player).clear();
                    MagicianPlayerComponent.KEY.get(player).reset();
                    PerfumerPlayerComponent.KEY.get(player).clear();
                    SpecterFearComponent.KEY.get(player).clear();
                    SpecterSecondSkillComponent.KEY.get(player).clear();
                    SpecterCowardiceComponent.KEY.get(player).clear();
                    dev.caecorthus.sparkwitch.roles.civilian.medium.MediumPlayerComponent.KEY.get(player).clear();
                    DominatorForcedShotService.discard(player);
                    OrthopedistSkillService.clearPlayer(player);
                    DominatorSkillService.clearPlayer(player);
                    GuardianAngelFeatureService.clearRoundPlayer(player);
                }
            }
        });
        // Headset slot round edges (BEAR earplugs, Blind ComTac). / 耳机槽回合节点（BEAR 耳塞、盲人 ComTac）。
        dev.caecorthus.sparkwitch.headset.HeadsetLifecycle.register();
        // BEAR (civilian): its hub fixes the order of the BEAR services; registered before USEC, which stays last.
        // BEAR（平民）：BEAR 各服务的顺序由其注册中心固定；注册在 USEC 之前，USEC 保持最后。
        dev.caecorthus.sparkwitch.roles.civilian.bear.BearFeatureService.register();
        // Medium (civilian): its hub fixes the order of the Medium services; registered after BEAR and before USEC,
        // which stays last. / 通灵师（平民）：通灵师各服务的顺序由其注册中心固定；注册在 BEAR 之后、USEC 之前，USEC 保持最后。
        dev.caecorthus.sparkwitch.roles.civilian.medium.MediumFeatureService.register();
        // USEC (police variant): appended last so every existing listener keeps its order; the hub fixes the order of
        // the USEC services. / USEC（警察变体）：追加在最后，使所有既有监听器保持顺序；USEC 各服务的顺序由注册中心固定。
        UsecFeatureService.register();
    }
}
