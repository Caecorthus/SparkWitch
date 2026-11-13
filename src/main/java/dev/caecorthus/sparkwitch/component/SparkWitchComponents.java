package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticePlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.athlete.AthleteComponent;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherPlayerComponent;
import dev.caecorthus.sparkwitch.item.rope.RopeHoldComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaComponent;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaComponent;
import dev.caecorthus.sparkwitch.roles.civilian.recruit.RecruitPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashComponent;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherSpiritComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeWorldComponent;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerLootWorldComponent;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRoundComponent;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceComponent;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellEchoPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenMarkPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseComponent;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotComponent;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroReturnComponent;
import dev.caecorthus.sparkwitch.roles.killer.hiro.witchification.HiroWitchificationComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterCowardiceComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterFearComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterSecondSkillComponent;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeGiftPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeTheftPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanFeignComponent;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanInstinctVeilComponent;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanMomentWorldComponent;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFeignComponent;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendMomentWorldComponent;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone.AbyssZoneExposureComponent;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantRoundComponent;
import dev.caecorthus.sparkwitch.roles.witch.bewitched.BewitchedPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRuntimeComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorWorldComponent;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRegistryComponent;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

public final class SparkWitchComponents implements EntityComponentInitializer, WorldComponentInitializer {
    @Override
    public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(PlayerEntity.class, WitchPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(WitchPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, EmmaPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(EmmaPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, GrandWitchRuntimeComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(GrandWitchRuntimeComponent::new);
        registry.beginRegistration(PlayerEntity.class, PerfumerPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(PerfumerPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, ProphetPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(ProphetPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, HunterPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(HunterPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, KidnapperControlComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(KidnapperControlComponent::new);
        registry.beginRegistration(PlayerEntity.class, OrthopedistPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(OrthopedistPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, GuardianAngelPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(GuardianAngelPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, VendettaPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(VendettaPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, BlackRavenMarkPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(BlackRavenMarkPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, BlackRavenPerceptionPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(BlackRavenPerceptionPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, BlackRavenDisguiseComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(BlackRavenDisguiseComponent::new);
        registry.beginRegistration(PlayerEntity.class, WraithPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(WraithPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, SaboteurPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SaboteurPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, CurserPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(CurserPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, MagicianPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(MagicianPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, LegacyWraithPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(LegacyWraithPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, BellEchoPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(BellEchoPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, TimeStealerPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(TimeStealerPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, TimeTheftPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(TimeTheftPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, TimeGiftPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(TimeGiftPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, ControlExpertStatusComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(ControlExpertStatusComponent::new);
        // Rope strangle hold (2026-11-11): which side of a strangle the owner is on, owner-synced and transient; kept
        // beside the stun status because it drives the same client input lock.
        // 绳索勒杀定身（2026-11-11）：拥有者处于勒杀的哪一方，只同步给本人且不保存；与眩晕状态相邻，因为它驱动同一套客户端输入锁。
        registry.beginRegistration(PlayerEntity.class, RopeHoldComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(RopeHoldComponent::new);
        registry.beginRegistration(PlayerEntity.class, HolyFlashComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(HolyFlashComponent::new);
        registry.beginRegistration(PlayerEntity.class, SeekerStatusComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SeekerStatusComponent::new);
        registry.beginRegistration(PlayerEntity.class, FisherSpiritComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(FisherSpiritComponent::new);
        registry.beginRegistration(PlayerEntity.class, BlindComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(BlindComponent::new);
        registry.beginRegistration(PlayerEntity.class, AbyssZoneExposureComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(AbyssZoneExposureComponent::new);
        registry.beginRegistration(PlayerEntity.class, RiftSessionComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(RiftSessionComponent::new);
        // Bewitched promotion task count, synced to its owner only (C3). / 魔化使晋升任务计数，只同步给本人（C3）。
        registry.beginRegistration(PlayerEntity.class, BewitchedPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(BewitchedPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, ApprenticePlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(ApprenticePlayerComponent::new);
        // USEC scope state for the lens glint, synced to everyone; appended last.
        // USEC 开镜状态（镜头反光用），同步给所有人；追加在最后。
        registry.beginRegistration(PlayerEntity.class, UsecPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(UsecPlayerComponent::new);
        // Fiend feign-death cycle, synced to its owner only; appended last.
        // 魔人假死周期，只同步给本人；追加在最后。
        registry.beginRegistration(PlayerEntity.class, FiendFeignComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(FiendFeignComponent::new);
        // Witch Maiden Mini Wither: the active flag for everyone, tasks and cooldown for its owner; appended last.
        // 巫女小凋灵：在场标记同步给所有人，任务与冷却只同步给本人；追加在最后。
        registry.beginRegistration(PlayerEntity.class, MiniWitherPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(MiniWitherPlayerComponent::new);
        // Black Raven Raven Form: the active bit for everyone, the rest for its owner; appended last.
        // 黑羽鸦化鸦：在场标记同步给所有人，其余只同步给本人；追加在最后。
        registry.beginRegistration(PlayerEntity.class, BlackRavenCrowPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(BlackRavenCrowPlayerComponent::new);
        // Recruit first-task progress and knife grant, synced to its owner only; appended last.
        // 新兵首个任务进度与发刀状态，只同步给本人；追加在最后。
        registry.beginRegistration(PlayerEntity.class, RecruitPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(RecruitPlayerComponent::new);
        // Athlete dash ready tick, synced to its owner only; appended last.
        // 运动员冲刺就绪刻，只同步给本人；追加在最后。
        registry.beginRegistration(PlayerEntity.class, AthleteComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(AthleteComponent::new);
        // 城崎诺亚's paint ready tick (owner-synced) and this round's painted one-offs; appended last.
        // 城崎诺亚的作画就绪刻（只同步给本人）与本局已画出的一次性结果；追加在最后。
        registry.beginRegistration(PlayerEntity.class, NoaComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(NoaComponent::new);
        // Ragpicker search progress and ready tick, synced to its owner only; appended last.
        // 拾荒者搜刮进度与就绪刻，只同步给本人；追加在最后。
        registry.beginRegistration(PlayerEntity.class, JunkerPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(JunkerPlayerComponent::new);
        // Dominator uses and mark target, synced to its owner only; appended last.
        // 支配者剩余次数与标记目标，只同步给本人；追加在最后。
        registry.beginRegistration(PlayerEntity.class, DominatorPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(DominatorPlayerComponent::new);
        // Forced-shot sequence. Only the "active" flag is saved/copied: a loaded or respawn-copied sequence never resumes,
        // it purges leftover marked guns on its next tick and keeps no stash.
        // 强制开枪序列。只保存/复制“进行中”标记：读档或重生复制后的序列绝不继续，下一刻清除残留的标记左轮且不保留原物品。
        registry.beginRegistration(PlayerEntity.class, DominatorForcedShotComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY)
                .end(DominatorForcedShotComponent::new);
        // 夏目安安 (PR #194): hypnosis window and cooldown ready tick (server), their shown seconds synced to the owner
        // only; appended last. / 夏目安安（PR #194）：催眠窗口与冷却就绪刻（服务端），显示秒数只同步给本人；追加在最后。
        registry.beginRegistration(PlayerEntity.class,
                        dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeComponent::new);
        // Headset slot storage (BEAR earplugs, Blind ComTac VIII): saved, synced to its owner only; appended last.
        // 耳机槽存储（BEAR 耳塞、盲人 ComTac VIII）：随存档保存，只同步给本人；追加在最后。
        registry.beginRegistration(PlayerEntity.class, dev.caecorthus.sparkwitch.headset.HeadsetComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(dev.caecorthus.sparkwitch.headset.HeadsetComponent::new);
        // Specter sanity-outline colours, fear levels, second-skill state and cowardice; appended last.
        // 幽魂理智描边颜色、恐惧层数、二技能状态与怯懦；追加在最后。
        registry.beginRegistration(PlayerEntity.class, SpecterPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SpecterPlayerComponent::new);
        registry.beginRegistration(PlayerEntity.class, SpecterFearComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SpecterFearComponent::new);
        registry.beginRegistration(PlayerEntity.class, SpecterSecondSkillComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SpecterSecondSkillComponent::new);
        registry.beginRegistration(PlayerEntity.class, SpecterCowardiceComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SpecterCowardiceComponent::new);
        // Savior G-skill cooldown and domain timer (PR #200), synced to its owner only; appended after the
        // Specter's components.
        // 救世主 G 技能冷却与领域计时（PR #200），只同步给本人；追加在幽魂组件之后。
        registry.beginRegistration(PlayerEntity.class, SaviorPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(SaviorPlayerComponent::new);
        // Medium cooldowns and 招魂 bindings, synced to the owner only; appended after the Savior's.
        // 通灵师冷却与招魂绑定，只同步给本人；追加在救世主组件之后。
        registry.beginRegistration(PlayerEntity.class, MediumPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(MediumPlayerComponent::new);
        // Charon (PR #202): pardon window (server only), both cooldowns and the marks (owner only); appended after the
        // Medium's component. / 卡戎（PR #202）：宽恕窗口（仅服务端）、两个冷却与标记（只同步给本人）；追加在通灵师组件之后。
        registry.beginRegistration(PlayerEntity.class, FerrymanPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(FerrymanPlayerComponent::new);
        // Charon gaze feign: the marked attacker's 10 s fake corpse, server only; appended last.
        // 卡戎「瞥视」假死：被标记者 10 秒的假尸体，仅服务端；追加在最后。
        registry.beginRegistration(PlayerEntity.class, FerrymanFeignComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(FerrymanFeignComponent::new);
        // Charon instinct veil: broadcast to everyone because instinct is drawn per client; appended last.
        // 卡戎的本能帷幕：本能按客户端绘制，因此同步给所有人；追加在最后。
        registry.beginRegistration(PlayerEntity.class, FerrymanInstinctVeilComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(FerrymanInstinctVeilComponent::new);
        // Tono Hanna (PR #212): float, penalty and handkerchief timers. Everyone receives only the float's remaining
        // ticks (instinct is drawn per client); the owner also gets the ready and penalty ticks. Appended last, after
        // the Charon's components.
        // 远野汉娜（PR #212）：浮游、惩罚与手帕计时。所有人只收到浮游的剩余刻数（本能按客户端绘制）；本人额外收到就绪刻与
        // 惩罚刻。追加在最后，位于卡戎的组件之后。
        registry.beginRegistration(PlayerEntity.class, HannaComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(HannaComponent::new);
        // 橘雪莉 (PR #206): skill cooldowns, her Mighty Force window, badge holders and reveal trackers, synced to the
        // owner only; appended last, after Tono Hanna's.
        // 橘雪莉（PR #206）：技能冷却、巨力窗口、徽章持有者与揭示追踪，只同步给本人；追加在最后，位于远野汉娜的组件之后。
        registry.beginRegistration(PlayerEntity.class,
                        dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent::new);
        // 二阶堂希罗 (2026-11-11): 死亡回归 cooldown, window and record point, then 魔女化 progress; both synced to
        // the owner only; appended last, after 橘雪莉's component.
        // 二阶堂希罗（2026-11-11）：死亡回归的冷却、窗口与记录点，以及魔女化进度；均只同步给本人；追加在最后，位于橘雪莉的
        // 组件之后。
        registry.beginRegistration(PlayerEntity.class, HiroReturnComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(HiroReturnComponent::new);
        registry.beginRegistration(PlayerEntity.class, HiroWitchificationComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(HiroWitchificationComponent::new);
        // 犯罪推理学家：推理记录、胜利进度与隐身充能。不跨命继承。
        registry.beginRegistration(PlayerEntity.class,
                        dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerComponent::new);
    }

    @Override
    public void registerWorldComponentFactories(@NotNull WorldComponentFactoryRegistry registry) {
        registry.register(JudgeWorldComponent.KEY, JudgeWorldComponent::new);
        registry.register(WitchWorldComponent.KEY, WitchWorldComponent::new);
        registry.register(WitchFactorWorldComponent.KEY, WitchFactorWorldComponent::new);
        registry.register(EmmaRoundComponent.KEY, EmmaRoundComponent::new);
        registry.register(WraithRoundComponent.KEY, WraithRoundComponent::new);
        registry.register(LegacyWraithRoundComponent.KEY, LegacyWraithRoundComponent::new);
        registry.register(FiendMomentWorldComponent.KEY, FiendMomentWorldComponent::new);
        registry.register(AccompliceVariantRoundComponent.KEY, AccompliceVariantRoundComponent::new);
        registry.register(RiftGateRegistryComponent.KEY, RiftGateRegistryComponent::new);
        registry.register(dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent.KEY,
                dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent::new);
        registry.register(JunkerLootWorldComponent.KEY, JunkerLootWorldComponent::new);
        registry.register(SpecterOtherworldComponent.KEY, SpecterOtherworldComponent::new);
        registry.register(MediumSeanceComponent.KEY, MediumSeanceComponent::new);
        // Charon (PR #202) Soul Moment, appended last, after the Medium's seance.
        // 卡戎（PR #202）亡魂时刻，追加在最后，位于通灵师通灵组件之后。
        registry.register(FerrymanMomentWorldComponent.KEY, FerrymanMomentWorldComponent::new);
    }
}
