package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

/**
 * Server-authoritative bell toll: forced, terminal kills of every valid target whose sanity is below 0.
 * Owner-approved exception: the kill uses {@code force = true} with {@link SparkWitchDeathReasons#BELL_TOLL}
 * (registered SparkTraits-terminal), so no shield or revive stops it; only SparkFactionAPI's structural
 * {@code canAffectPlayer} policy is honoured, both here and inside Wathe's kill path.
 * 服务端权威的敲钟结算：对所有理智低于 0 的有效目标执行强制且终结的击杀。
 * 所有者批准的例外：以 {@code force = true} 与 {@link SparkWitchDeathReasons#BELL_TOLL}（已注册为 SparkTraits 终结原因）击杀，
 * 任何护盾或复活都无法阻止；仅遵循 SparkFactionAPI 的结构性 {@code canAffectPlayer} 策略（此处与 Wathe 击杀流程中均会检查）。
 */
public final class BellTollService {
    private BellTollService() {
    }

    public static TypedActionResult<ItemStack> use(ServerPlayerEntity ringer, ItemStack stack, Hand hand) {
        Item bell = stack.getItem();
        GameWorldComponent game = GameWorldComponent.KEY.get(ringer.getServerWorld());
        // Swallowed (Taotie) players are spectators; Grand Witch Fear intentionally does not block the bell.
        // 被饕餮吞下的玩家处于旁观模式；大魔女恐惧按设计不阻止敲钟。
        if (!BellRingerRules.isBellRinger(game.getRole(ringer))
                || !game.isRunning()
                || !GameFunctions.isPlayerPlayingAndAlive(ringer)
                || ringer.isSpectator()
                || ringer.getItemCooldownManager().isCoolingDown(bell)
                || SparkTraitsKillerBridge.blocksWeaponAction(ringer, stack)) {
            ringer.sendMessage(Text.translatable("message.sparkwitch.toll_bell.blocked"), true);
            return TypedActionResult.fail(stack);
        }

        // Snapshot before killing so side effects of one death cannot change who the toll claims.
        // 击杀前固定目标快照，避免某次死亡的副作用改变本次敲钟的目标。
        List<ServerPlayerEntity> targets = findTargets(ringer);
        if (targets.isEmpty()) {
            BellEchoPlayerComponent.KEY.get(ringer).setTollReady(false);
            ringer.sendMessage(Text.translatable("message.sparkwitch.toll_bell.no_target"), true);
            return TypedActionResult.fail(stack);
        }

        int claimed = 0;
        for (ServerPlayerEntity target : targets) {
            if (!GameFunctions.isPlayerPlayingAndAlive(target)) {
                continue;
            }
            GameFunctions.killPlayer(target, true, ringer, SparkWitchDeathReasons.BELL_TOLL, true);
            if (game.isPlayerDead(target.getUuid())) {
                claimed++;
            }
        }
        applyCooldown(ringer, bell);
        // Singular variant only fixes English grammar; the contract's plural key covers every other count.
        // 单数键仅用于修正英文语法；其余人数仍使用契约规定的复数键。
        String tolledKey = claimed == 1
                ? "message.sparkwitch.toll_bell.tolled.one"
                : "message.sparkwitch.toll_bell.tolled";
        ringer.sendMessage(Text.translatable(tolledKey, claimed), true);
        BellEchoPlayerComponent.KEY.get(ringer).setTollReady(
                BellRingerEchoTargeting.isParticipant(ringer) && hasAnyTarget(ringer));
        return TypedActionResult.success(stack);
    }

    /** Glint query; same filter as {@link #findTargets}, short-circuiting. / 光效查询；与 {@link #findTargets} 同一过滤，找到即返回。 */
    public static boolean hasAnyTarget(ServerPlayerEntity ringer) {
        GameWorldComponent game = GameWorldComponent.KEY.get(ringer.getServerWorld());
        for (ServerPlayerEntity candidate : ringer.getServerWorld().getPlayers()) {
            if (isTarget(ringer, candidate, game)) {
                return true;
            }
        }
        return false;
    }

    /** Detached snapshot of every current toll target. / 当前全部敲钟目标的独立快照。 */
    public static List<ServerPlayerEntity> findTargets(ServerPlayerEntity ringer) {
        GameWorldComponent game = GameWorldComponent.KEY.get(ringer.getServerWorld());
        List<ServerPlayerEntity> targets = new ArrayList<>();
        for (ServerPlayerEntity candidate : ringer.getServerWorld().getPlayers()) {
            if (isTarget(ringer, candidate, game)) {
                targets.add(candidate);
            }
        }
        return targets;
    }

    /**
     * Raw {@code getMood() < 0}: Wathe answers 1 for FAKE/NONE mood and SparkTraits already resolves the
     * effective mood type there; the {@code isLowerThan*} helpers are avoided because Well Trained overrides them.
     * 直接使用 {@code getMood() < 0}：FAKE/NONE 情绪在 Wathe 中返回 1，SparkTraits 已在该处解析有效情绪类型；
     * 不使用 {@code isLowerThan*}，因为“训练有素”会覆盖它们。
     */
    private static boolean isTarget(ServerPlayerEntity ringer, ServerPlayerEntity candidate, GameWorldComponent game) {
        return !candidate.getUuid().equals(ringer.getUuid())
                && PlayerMoodComponent.KEY.get(candidate).getMood() < 0.0F
                && BellRingerEchoTargeting.isParticipant(candidate)
                && SparkFactionApi.canAffectPlayer(ringer, candidate, SparkWitchDeathReasons.BELL_TOLL, game);
    }

    /**
     * Exact 30 s: SparkTraits' exact write performs the vanilla {@code set} itself after Fast Hands and other
     * cooldown modifiers, so it goes first and the plain vanilla write is only the fallback when Traits is
     * absent or older (never both, which would send two cooldown packets).
     * 精确 30 秒：SparkTraits 的精确写入会在“快手”等冷却倍率之后自行完成原版写入，因此优先调用；
     * 仅在 Traits 缺失或过旧时回退到原版写入（二者不会同时执行，以免发送两次冷却数据包）。
     */
    private static void applyCooldown(ServerPlayerEntity ringer, Item bell) {
        if (!SparkTraitsKillerBridge.setExactItemCooldownRemaining(ringer, bell, BellRingerRules.TOLL_COOLDOWN_TICKS)) {
            ringer.getItemCooldownManager().set(bell, BellRingerRules.TOLL_COOLDOWN_TICKS);
        }
    }
}
