package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.client.potiongunner.PotionGunnerClient;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionGunnerEntities;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.client.blind.BlindClient;
import dev.caecorthus.sparkwitch.client.blind.kit.BlindKitClientWiring;
import dev.caecorthus.sparkwitch.client.fisher.FisherClient;
import dev.caecorthus.sparkwitch.client.judge.JudgeClientModule;
import dev.caecorthus.sparkwitch.client.prophet.ProphetClientModule;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRules;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.client.emma.EmmaClientModule;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchClientModule;
import dev.caecorthus.sparkwitch.client.bellringer.BellRingerClient;
import dev.caecorthus.sparkwitch.client.timestealer.TimeStealerClient;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenClientModule;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerScreen;
import dev.caecorthus.sparkwitch.client.prophet.ProphetNecrologyBookScreen;
import dev.caecorthus.sparkwitch.client.controlexpert.ControlExpertStatusHud;
import dev.caecorthus.sparkwitch.client.controlexpert.ControlExpertStunClient;
import dev.caecorthus.sparkwitch.client.insider.InsiderClient;
import dev.caecorthus.sparkwitch.client.abysslistener.AbyssListenerClient;
import dev.caecorthus.sparkwitch.client.seeker.SeekerClientModule;
import dev.caecorthus.sparkwitch.client.hooks.DeathRayClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.GrandWitchFearClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.HunterTrapClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.KidnapperThrowClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.OrthopedistClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.ProphetCorpseHighlightClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.WitchAbilityKeyBridge;
import dev.caecorthus.sparkwitch.client.hooks.WitchCohortClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctSuppressionClientHooks;
import dev.caecorthus.sparkwitch.client.hooks.WitchPoisonVisionClientHooks;
import dev.caecorthus.sparkwitch.client.curser.CurserClientHooks;
import dev.caecorthus.sparkwitch.client.net.version.SparkWitchClientVersionHandshake;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.client.renderer.HunterTrapEntityRenderer;
import dev.caecorthus.sparkwitch.client.screen.TarotDivinationSelectorScreen;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationClientState;
import dev.caecorthus.sparkwitch.client.tarot.TarotReadingLog;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaKnifeModelLoadingPlugin;
import dev.caecorthus.sparkwitch.client.witchmaiden.WitchMaidenClientModule;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.net.OpenBlackRavenLedgerS2CPacket;
import dev.caecorthus.sparkwitch.net.OpenProphetNecrologyS2CPacket;
import dev.caecorthus.sparkwitch.net.OpenTarotDivinationSelectorS2CPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.net.TarotDivinationReadingS2CPacket;
import dev.caecorthus.sparkwitch.net.TarotDivinationSnapshotS2CPacket;
import dev.caecorthus.sparkwitch.net.UseWitchSkillC2SPacket;
import dev.caecorthus.sparkwitch.net.WraithRoleAnnouncementS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertEntities;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.UseGuardianAngelSkillC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.UseOrthopedistSkillC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintRules;
import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashEntities;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterEntities;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRole;
import dev.caecorthus.sparkwitch.client.saboteur.SaboteurClientAbilityRules;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.UseSaboteurSkillC2SPacket;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.caecorthus.sparkwitch.roles.witch.curser.UseCurserAbilityC2SPacket;
import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import dev.doctor4t.wathe.api.event.AllowPlayerChat;
import dev.doctor4t.wathe.api.event.CanSeePoison;
import dev.doctor4t.wathe.api.event.ShouldAllowSuppressedKey;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.util.Util;

public final class SparkWitchClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SparkWitch.LOGGER.info("Initializing SparkWitch client hooks.");
        SparkWitchServerConnection.reset();
        SecondaryAbilityController.registerKeyBinding();
        BlackRavenClientModule.register();
        GrandWitchClientModule.register();
        EmmaClientModule.register();
        WitchMaidenClientModule.register();
        BellRingerClient.init();
        TimeStealerClient.init();
        VendettaKnifeModelLoadingPlugin.register();
        SecondaryAbilityController.reset();
        SparkWitchClientVersionHandshake.registerClient();
        registerEntityRenderers();
        registerTarotDivinationNetworking();
        registerBlackRavenNetworking();
        registerProphetNecrologyNetworking();
        registerWraithRoleAnnouncementNetworking();
        JudgeClientModule.register();
        ProphetClientModule.register();
        ControlExpertStunClient.register();
        ControlExpertStatusHud.register();
        InsiderClient.init();
        PotionGunnerClient.init();
        SeekerClientModule.register();
        FisherClient.register();
        dev.caecorthus.sparkwitch.client.fiend.FiendClient.init();
        BlindClient.register();
        dev.caecorthus.sparkwitch.client.saint.HolyFlashAudioClient.register();
        AbyssListenerClient.init();
        AllowPlayerChat.EVENT.register(player -> {
            if (!SparkWitchServerConnection.isConfirmedServer()) {
                return false;
            }
            var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
            return WraithParticipationRules.mayUseTextChat(
                    WraithClientState.isActive(player),
                    GuardianAngelRules.isGuardianAngel(role),
                    player.isCreative()
            );
        });
        ShouldAllowSuppressedKey.EVENT.register(keyBinding -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!SparkWitchServerConnection.isConfirmedServer()
                    || client.player == null
                    || keyBinding != client.options.jumpKey) {
                return false;
            }
            // Wathe 会先询问该事件再压制跳跃键；活跃冤魂必须无视地图禁跳和跳跃体力检查。
            boolean activeWraith = WraithClientState.isActive(client.player);
            return activeWraith && WraithParticipationRules.mayJump(true, false);
        });

        // Reset on every connection lifecycle edge so failed login attempts cannot leak confirmed state.
        // 在每个连接生命周期节点清理状态，避免失败的登录尝试残留已确认标记。
        ClientLoginConnectionEvents.INIT.register((handler, client) -> resetConnectionState());
        ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> resetConnectionState());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetConnectionState());
        WitchInstinctSuppressionClientHooks.register();
        HunterTrapClientHooks.register();
        OrthopedistClientHooks.register();
        ProphetCorpseHighlightClientHooks.register();
        registerGrandWitchCeremonialSwordBgm();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TarotDivinationClientState.tick(client);
            JudgeClientModule.tick(client);
            ProphetClientModule.tick(client);
            SecondaryAbilityController.tick(client);
            if (!SparkWitchServerConnection.isConfirmedServer()) {
                WitchAbilityKeyBridge.reset();
                DeathRayClientHooks.reset();
                KidnapperThrowClientHooks.reset();
                return;
            }
            KidnapperThrowClientHooks.tick(client);
            GrandWitchFearClientHooks.tick();
            DeathRayClientHooks.tick(client);
            dev.caecorthus.sparkwitch.client.gui.OwnerInventoryPresenter.tick(client);
            if (client.player != null
                    && client.getNetworkHandler() != null
                    && WitchAbilityKeyBridge.wasPressed()) {
                var role = GameWorldComponent.KEY.get(client.player.getWorld()).getRole(client.player);
                boolean exactSaboteurRole = role != null
                        && SaboteurRole.ID.equals(role.identifier());
                if (JudgeRules.isJudge(role)) {
                    JudgeClientModule.requestSelection(client);
                } else if (ProphetClientModule.ownsAbilityKey(client.player, role)) {
                    // Prophecy opens its own session instead of sending the generic skill packet.
                    // 预言打开自有会话，而不是发送通用技能包。
                    ProphetClientModule.requestProphecy(client);
                } else if (EmmaClientModule.isEmma(client.player)) {
                    EmmaClientModule.use(client.player);
                } else if (exactSaboteurRole) {
                    if (SaboteurClientAbilityRules.shouldSend(
                            true,
                            true,
                            WraithClientState.isPromoted(client.player),
                            ClientPlayNetworking.canSend(UseSaboteurSkillC2SPacket.ID)
                    )) {
                        ClientPlayNetworking.send(new UseSaboteurSkillC2SPacket());
                    }
                } else if (role != null && role.identifier().equals(dev.caecorthus.sparkwitch.SparkWitchRoles.CURSER_ID)) {
                    if (CurserClientHooks.canUse(client.player)) {
                        CurserClientHooks.use();
                    }
                } else if (GuardianAngelRules.isGuardianAngel(role)) {
                    ClientPlayNetworking.send(new UseGuardianAngelSkillC2SPacket());
                } else if (GameWorldComponent.KEY.get(client.player.getWorld())
                        .isRole(client.player, dev.caecorthus.sparkwitch.SparkWitchRoles.orthopedist())) {
                    // Widened by the Black Raven acting overlay; getRole stays raw. / 黑羽鸦扮演覆盖层会放宽此判定；getRole 仍为真实身份。
                    ClientPlayNetworking.send(new UseOrthopedistSkillC2SPacket());
                } else if (BlindRules.isBlind(role)) {
                    // Real role only (the Blind is never a disguise target). / 仅真实职业（盲人不可被伪装）。
                    BlindKitClientWiring.onAbilityKey(client);
                } else if (!WitchMaidenRules.isWitchMaiden(role)
                        && (WitchPlayerComponent.KEY.get(client.player).hasSkill()
                        || SaintRules.isSaint(role))) {
                    ClientPlayNetworking.send(new UseWitchSkillC2SPacket());
                }
            }
        });

        ShouldShowCohort.EVENT.register((viewer, target) -> {
            if (!SparkWitchServerConnection.isConfirmedServer()) {
                return null;
            }
            if (WitchCohortClientHooks.isGrandWitchCohortPair(viewer, target)) {
                return ShouldShowCohort.CohortResult.hide(110);
            }
            return null;
        });
        CanSeePoison.EVENT.register(WitchPoisonVisionClientHooks::canSeeHiddenPoison);
    }

    public static Text abilityKeyText() {
        return WitchAbilityKeyBridge.keyText();
    }

    private static void registerEntityRenderers() {
        // The projectile renders its synced shuriken item stack, never the upstream generic knife texture.
        // 投射物始终渲染同步的手里剑物品模型，不复用上游通用飞刀贴图。
        EntityRendererRegistry.register(
                SparkWitchEntities.ninjaShuriken(),
                context -> new FlyingItemEntityRenderer<>(context, 1.0F, true)
        );
        EntityRendererRegistry.register(HunterEntities.hunterTrap(), HunterTrapEntityRenderer::new);
        // The thrown Shock Device renders its synced item stack like vanilla thrown items.
        // 投出的电击装置与原版投掷物一样渲染其同步的物品。
        EntityRendererRegistry.register(ControlExpertEntities.shockDevice(), FlyingItemEntityRenderer::new);
        // The thrown Holy Flash renders its synced item stack. / 投出的圣光弹渲染其同步的物品。
        EntityRendererRegistry.register(HolyFlashEntities.holyFlash(), FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(PotionGunnerEntities.potionShell(), FlyingItemEntityRenderer::new);
        SeekerClientModule.registerEntityRenderers();
    }

    private static void registerWraithRoleAnnouncementNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(WraithRoleAnnouncementS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    var roleId = net.minecraft.util.Identifier.tryParse(payload.roleId());
                    RoundTextRenderer.startWelcome(
                            RoleAnnouncementTexts.getForRole(roleId),
                            payload.killers(),
                            payload.targets()
                    );
                }));
    }

    private static void registerGrandWitchCeremonialSwordBgm() {
        AmbienceUtil.registerBackgroundAmbience(new BackgroundAmbience(
                SparkWitchSounds.GRAND_WITCH_CEREMONIAL_SWORD_BGM,
                player -> SparkWitchServerConnection.isConfirmedServer()
                        && WitchWorldComponent.KEY.get(player.getWorld()).hasGrandWitchCeremonialSwordBgm(),
                20
        ));
    }

    private static void registerTarotDivinationNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(TarotDivinationSnapshotS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (!SparkWitchServerConnection.isConfirmedServer()) {
                        return;
                    }
                    TarotDivinationClientState.snapshotState().overwrite(
                            payload.civilianCount(),
                            payload.killerCount(),
                            payload.neutralCount(),
                            payload.witchCount()
                    );
                }));
        ClientPlayNetworking.registerGlobalReceiver(OpenTarotDivinationSelectorS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (!SparkWitchServerConnection.isConfirmedServer()) {
                        return;
                    }
                    context.client().setScreen(new TarotDivinationSelectorScreen(
                            payload.mode(),
                            payload.playerIds(),
                            payload.playerNames()
                    ));
                }));
        // Purchaser-only result; stored so the slip and the selector's stamps repeat what the server already said.
        // 仅购买者可见的结果；保存下来，供结果条与选择界面的印记重复展示服务端已给出的结论。
        ClientPlayNetworking.registerGlobalReceiver(TarotDivinationReadingS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (!SparkWitchServerConnection.isConfirmedServer()) {
                        return;
                    }
                    TarotDivinationClientState.readingLog().record(new TarotReadingLog.Reading(
                            payload.mode(),
                            payload.target(),
                            payload.displayName(),
                            payload.positive(),
                            Util.getMeasuringTimeMs()
                    ));
                }));
    }

    private static void registerBlackRavenNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(OpenBlackRavenLedgerS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (SparkWitchServerConnection.isConfirmedServer()) {
                        BlackRavenLedgerScreen.open(context.client());
                    }
                }));
    }

    /**
     * The Necrology opens only on the server's empty authorization; its pages come from the owner-synced component.
     * 亡者名录只在收到服务端的空授权包后打开；书页内容来自仅同步给所有者的组件。
     */
    private static void registerProphetNecrologyNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(OpenProphetNecrologyS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (SparkWitchServerConnection.isConfirmedServer()) {
                        ProphetNecrologyBookScreen.open(context.client());
                    }
                }));
    }

    private static void resetConnectionState() {
        dev.caecorthus.sparkwitch.client.gui.OwnerInventoryPresenter.reset();
        SparkWitchServerConnection.reset();
        WitchAbilityKeyBridge.reset();
        SecondaryAbilityController.reset();
        KidnapperThrowClientHooks.reset();
        WitchMaidenClientModule.clear();
        TarotDivinationClientState.clear();
        JudgeClientModule.clear();
        ProphetClientModule.clear();
        SeekerClientModule.reset();
        BlindClient.reset();
    }
}
