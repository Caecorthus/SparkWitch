package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticePlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.athlete.AthleteComponent;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStatusComponent;
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
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelPlayerComponent;
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
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeGiftPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeTheftPlayerComponent;
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
    }
}
