package dev.caecorthus.sparkwitch.roles.killer.hiro.witchification;

import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 二阶堂希罗's passive 魔女化 (Witchification), server-authoritative (owner rules 2026-11-11).
 * <p>Kills: every kill Wathe commits ({@code KillPlayer.AFTER}) whose responsible player is a living raw-role Hiro in
 * an ACTIVE round counts; the responsible player is the killer, else {@code JudgeKillAttribution}'s committed cause.
 * Self-kills and SparkTraits-intercepted deaths do not count. A 死亡回归 fake death counts only when it becomes a real
 * kill, because only then does {@code AFTER} fire. The threshold is {@code HiroRules.requiredKills} of the round's
 * player count, written at role assignment and again at {@code ON_FINISH_INITIALIZE}.
 * <p>Reaching it, once per round: a quota-free witch factor if she holds none
 * ({@code WitchFactorService.grantSelfMade}), her role colour in the witch-factor network view for the rest of her life
 * ({@code exposeRoleColor}), infinite Speed II, one loaded {@code wathe:derringer}, and a private line plus a sound
 * only she hears. Speed and role colour are re-asserted every tick while she is a living witchified Hiro and end at
 * her death, a role change, a reset and the round end; the factor then stays an ordinary factor.
 * <p>The derringer never drops: Wathe refuses every gun pickup by a non-innocent, so with no free shown slot it stays
 * owed (saved in the component) and is handed over as soon as a shown slot frees.
 * 二阶堂希罗的被动「魔女化」，服务端权威（所有者规则 2026-11-11）。
 * <p>击杀：Wathe 提交的每一次击杀（{@code KillPlayer.AFTER}），只要责任人是 ACTIVE 对局中存活的真实身份希罗就计数；责任人取
 * 凶手，否则取 {@code JudgeKillAttribution} 已提交的归因。自杀与被 SparkTraits 拦截的死亡不计数。死亡回归的假死只有在变成真正
 * 击杀时才计数，因为那时 {@code AFTER} 才会触发。所需数为本局玩家数的 {@code HiroRules.requiredKills}，在分配职业时写入，并在
 * {@code ON_FINISH_INITIALIZE} 时再次写入。
 * <p>达到后每局仅一次：若没有魔女因子则获得一个不占额度的因子（{@code WitchFactorService.grantSelfMade}），此后终生在魔女因子
 * 网络视图中以职业色显示（{@code exposeRoleColor}），获得无限速度 II、一把已装填的 {@code wathe:derringer}，以及只有她能看到的
 * 提示与只有她能听到的音效。速度与职业色在她是存活的已魔女化希罗期间每刻重新确认，并在死亡、职业变更、重置与对局结束时终止；
 * 因子此后仍是普通因子。
 * <p>德林加手枪从不掉落：Wathe 拒绝非无辜者拾取任何枪械，因此没有可见空栏位时它保持「待发放」（存于组件中），一旦有可见栏位
 * 空出就立即发放。
 */
public final class HiroWitchificationService {
    static final String WITCHIFIED_KEY = "message.sparkwitch.nikaido_hiro.witchified";
    static final String DERRINGER_OWED_KEY = "message.sparkwitch.nikaido_hiro.witchified.derringer_owed";
    static final String DERRINGER_DELIVERED_KEY = "message.sparkwitch.nikaido_hiro.witchified.derringer_delivered";
    /**
     * The Speed instance this passive put on each player; removal compares identity and shape, so a Speed another
     * system granted is never removed (Emma precedent). Instances do not survive a relog or restart, so the tick
     * adopts an effect of exactly this passive's shape on a witchified Hiro: it is hers, restored from saved data.
     * 本被动加在每位玩家身上的速度实例；移除时同时比较实例与形态，因此绝不移除其他系统授予的速度（沿用艾玛的做法）。实例在重连或
     * 重启后不再相同，因此每刻会认领已魔女化希罗身上形态与本被动完全一致的效果：那正是从存档恢复的本被动效果。
     */
    private static final Map<UUID, StatusEffectInstance> OWN_SPEED = new HashMap<>();
    private static boolean registered;

    private HiroWitchificationService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        HiroWitchificationReplay.register();
        RoleAssigned.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            // Any assignment restarts the passive; leaving the role ends Speed and the role colour, never the factor.
            // 任何一次分配都会重置被动；离开本职业会结束速度与职业色，但不会移除因子。
            HiroWitchificationComponent component = HiroWitchificationComponent.KEY.get(serverPlayer);
            component.clear();
            endBenefits(serverPlayer);
            if (HiroRules.isHiro(role)) {
                component.set(0, HiroRules.requiredKills(roundPlayers(serverPlayer.getServerWorld())), false);
            }
        });
        ResetPlayer.EVENT.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                HiroWitchificationComponent.KEY.get(serverPlayer).clear();
                endBenefits(serverPlayer);
            }
        });
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            // The final roster: every role is assigned by now (JudgeRuntime / the factor ledger snapshot here too).
            // 最终名单：此时所有身份都已分配（法官与因子账本也在此取快照）。
            int required = HiroRules.requiredKills(game.getAllPlayers().size());
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                if (HiroRules.isHiro(game, player)) {
                    HiroWitchificationComponent component = HiroWitchificationComponent.KEY.get(player);
                    component.set(component.kills(), required, component.isWitchified());
                }
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                HiroWitchificationComponent.KEY.get(player).clear();
                endBenefits(player);
            }
            // Whoever is still listed is offline or elsewhere; a new instance replaces theirs on return.
            // 仍在表中的玩家已离线或不在此世界；他们回来时效果实例已不同。
            OWN_SPEED.keySet().removeIf(id -> serverWorld.getServer().getPlayerManager().getPlayer(id) == null);
        });
        KillPlayer.AFTER.register(HiroWitchificationService::afterKill);
        ServerTickEvents.END_WORLD_TICK.register(HiroWitchificationService::tickWorld);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> OWN_SPEED.clear());
    }

    private static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer, Identifier reason) {
        // A Last Stand death is no death: it neither counts for the killer nor ends the victim's passive.
        // 背水一战拦截的死亡不算死亡：既不给凶手计数，也不结束受害者的被动。
        boolean intercepted = WitchFactorTraitsBridge.isDeathIntercepted(victim);
        if (!intercepted) {
            // Hiro died: her Speed and role colour end now; the kill count stays as the once-per-round latch.
            // 希罗死亡：速度与职业色立即结束；击杀计数保留，作为每局一次的锁存。
            HiroWitchificationComponent.KEY.get(victim).setDerringerOwed(false);
            endBenefits(victim);
        }
        ServerPlayerEntity responsible = killer;
        if (responsible == null) {
            UUID cause = JudgeKillAttribution.committedResponsible(victim);
            responsible = cause == null ? null : victim.getServer().getPlayerManager().getPlayer(cause);
        }
        if (responsible == null) {
            return;
        }
        ServerWorld world = responsible.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!HiroWitchificationRules.countsKill(
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                HiroRules.isLivingHiro(responsible),
                responsible == victim,
                intercepted)) {
            return;
        }
        HiroWitchificationComponent component = HiroWitchificationComponent.KEY.get(responsible);
        int required = component.required() > 0
                ? component.required() : HiroRules.requiredKills(game.getAllPlayers().size());
        HiroWitchificationRules.Step step = HiroWitchificationRules.afterCountedKill(
                component.kills(), required, component.isWitchified());
        component.set(step.kills(), step.required(), step.witchified());
        if (step.witchifiesNow()) {
            witchify(world, responsible, component);
        }
    }

    private static void witchify(ServerWorld world, ServerPlayerEntity hiro, HiroWitchificationComponent component) {
        // The role colour first, so the factor's own sync already carries it to the viewers.
        // 先写入职业色，因子自身的同步即可把它一并发给观看者。
        WitchFactorService.exposeRoleColor(hiro);
        // False when she already carries a factor (a Grand Witch's, Emma's or an inherited one): that one is kept.
        // 她已携带因子（大魔女、艾玛传播或继承所得）时返回 false：保留原有因子。
        WitchFactorService.grantSelfMade(hiro);
        maintainSpeed(hiro);
        boolean delivered = deliverDerringer(hiro);
        component.setDerringerOwed(!delivered);
        // Private: chat lines and a sound sent to her connection only. / 仅本人：聊天提示与只发给她的音效。
        hiro.sendMessage(Text.translatable(WITCHIFIED_KEY), false);
        if (!delivered) {
            hiro.sendMessage(Text.translatable(DERRINGER_OWED_KEY), false);
        }
        hiro.playSoundToPlayer(SoundEvents.ENTITY_WITCH_CELEBRATE, SoundCategory.PLAYERS, 1.0F, 0.8F);
        HiroWitchificationReplay.record(world, hiro, component.kills());
    }

    private static void tickWorld(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        boolean active = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!active && OWN_SPEED.isEmpty()) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (active && HiroRules.isHiro(game, player)) {
                HiroWitchificationComponent component = HiroWitchificationComponent.KEY.get(player);
                if (component.isWitchified() && HiroRules.isLivingHiro(player)) {
                    maintainSpeed(player);
                    // Idempotent set insert; it also restores the entry after a restart or a ledger reset.
                    // 幂等的集合写入；重启或账本重置后也由此恢复条目。
                    WitchFactorService.exposeRoleColor(player);
                    if (component.isDerringerOwed() && deliverDerringer(player)) {
                        component.setDerringerOwed(false);
                        player.sendMessage(Text.translatable(DERRINGER_DELIVERED_KEY), false);
                    }
                    continue;
                }
            }
            // Nothing owned means nothing to release (the common round without a witchified Hiro).
            // 未持有即无可释放（没有已魔女化希罗的常见对局）。
            if (!OWN_SPEED.isEmpty()) {
                releaseSpeed(player);
            }
        }
    }

    /** Ends Speed and the role colour; the progress and the factor are the caller's business. / 结束速度与职业色；进度与因子由调用方处理。 */
    private static void endBenefits(ServerPlayerEntity player) {
        releaseSpeed(player);
        WitchFactorService.concealRoleColor(player);
    }

    private static void maintainSpeed(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        if (HiroWitchificationRules.shouldGrantSpeed(current != null,
                current == null ? -1 : current.getAmplifier(), current != null && current.isInfinite())) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, StatusEffectInstance.INFINITE,
                    HiroWitchificationRules.SPEED_AMPLIFIER, false, false, true));
            // Vanilla merges into an existing instance, so read back the one the player now holds.
            // 原版会合并进已有实例，因此取回玩家现在持有的实例。
            OWN_SPEED.put(player.getUuid(), player.getStatusEffect(StatusEffects.SPEED));
        } else if (isOwnShape(current) && OWN_SPEED.get(player.getUuid()) != current) {
            OWN_SPEED.put(player.getUuid(), current);
        }
    }

    /**
     * Removes the Speed only when the player still holds the very instance this passive granted and it has this
     * passive's shape. A replaced instance is forgotten; while a stronger foreign Speed sits on top of ours in the
     * same instance (vanilla keeps ours hidden underneath), the entry stays and the tick retries.
     * 仅当玩家仍持有本被动授予的那个实例、且其形态与本被动一致时才移除速度。实例已被替换则直接遗忘；若同一实例上叠着其他系统更强
     * 的速度（原版会把本被动的效果藏在下层），条目保留，由每刻重试。
     */
    private static void releaseSpeed(ServerPlayerEntity player) {
        StatusEffectInstance owned = OWN_SPEED.get(player.getUuid());
        if (owned == null) {
            return;
        }
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        if (current != owned) {
            OWN_SPEED.remove(player.getUuid());
        } else if (isOwnShape(current)) {
            player.removeStatusEffect(StatusEffects.SPEED);
            OWN_SPEED.remove(player.getUuid());
        }
    }

    private static boolean isOwnShape(@Nullable StatusEffectInstance effect) {
        return effect != null && HiroWitchificationRules.isOwnSpeedShape(effect.getAmplifier(), effect.isInfinite(),
                effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
    }

    /**
     * Puts one loaded derringer ({@code USED} unset) into the first empty shown slot through
     * {@code PlayerInventory#insertStack(int, ItemStack)}, so the gun-refusing insert guards still apply. False leaves
     * the inventory untouched.
     * 通过 {@code PlayerInventory#insertStack(int, ItemStack)} 把一把已装填的德林加手枪（不设置 {@code USED}）放进第一个可见
     * 空栏位，因此拒绝枪械的放入守卫仍然生效。返回 false 时物品栏不变。
     */
    private static boolean deliverDerringer(ServerPlayerEntity hiro) {
        PlayerInventory inventory = hiro.getInventory();
        int slot = HiroWitchificationRules.shownEmptySlot(SparkFactionSecondRowCompat.isShown(),
                index -> inventory.getStack(index).isEmpty());
        if (slot < 0) {
            return false;
        }
        ItemStack derringer = new ItemStack(WatheItems.DERRINGER);
        if (!inventory.insertStack(slot, derringer) || !derringer.isEmpty()) {
            return false;
        }
        inventory.markDirty();
        hiro.currentScreenHandler.sendContentUpdates();
        return true;
    }

    private static int roundPlayers(ServerWorld world) {
        return GameWorldComponent.KEY.get(world).getAllPlayers().size();
    }
}
