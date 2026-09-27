package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server-only Minecraft adapter for the curse's Slowness (plan D5 / §3.8). Applying is a plain
 * {@code addStatusEffect} bracketed by two read-only chain reads that decide whether the stage owns its node; removing
 * (only for an owned stage) reads the live chain through {@link StatusEffectInstance#CODEC} (the hidden chain is
 * private, so an NBT round trip with registry-aware ops is the only public view of it), lets {@link SlownessChain}
 * decide which node is ours, and writes the rest back with {@code setStatusEffect}, so foreign Slowness (a Control
 * Expert stun, Grand Witch Heaviness, other roles) keeps its level and duration. Any decode failure leaves the effect
 * untouched: the curse's node is bounded to {@link TimeStealerRules#SLOWNESS_DURATION_TICKS} and simply runs out.
 * Known limitation (cross-role): a changed chain is written back as a new {@link StatusEffectInstance}, because the
 * in-place setters ({@code copyFrom}, the hidden chain) are not public and this role adds no mixin or access widener.
 * Levels and durations are kept, but the object identity is not, so a role that remembers the live instance by identity
 * (Control Expert's owned-effect record) no longer recognises it and leaves that Slowness to expire at its own cleanup
 * (death or disconnect; {@code resetPlayer} clears every effect anyway). The leftover is bounded by that role's own
 * duration.
 * 诅咒缓慢的仅服务端 Minecraft 适配器（计划 D5 / §3.8）。施加是普通的 {@code addStatusEffect}，前后各做一次只读的
 * 效果链读取以判定该阶段是否拥有其节点；移除（仅对拥有节点的阶段）时经
 * {@link StatusEffectInstance#CODEC} 读取实时效果链（隐藏链是私有字段，带注册表上下文的 NBT 往返是唯一的公开视图），
 * 由 {@link SlownessChain} 判定哪一节属于我们，再用 {@code setStatusEffect} 写回其余部分，
 * 因此外来缓慢（控场专家眩晕、大魔女沉重、其他职业）保持原有等级与时长。任何解码失败都不改动效果：
 * 诅咒那一节最长只有 {@link TimeStealerRules#SLOWNESS_DURATION_TICKS}，会自然到期。
 * 已知限制（跨职业）：效果链被改动时会以新的 {@link StatusEffectInstance} 写回，因为原地修改的入口（{@code copyFrom}、
 * 隐藏链）都不是公开的，本职业也不添加 mixin 或访问加宽器。等级与时长保持不变，但对象身份改变，
 * 因此按身份记住实时实例的职业（控场专家的归属记录）将不再认出它，并在其自身清理时（死亡或断线；
 * {@code resetPlayer} 本来就会清除所有效果）任该缓慢自然到期。残留时长以该职业自身的时长为上限。
 */
final class TimeTheftSlowness {
    /** {@code StatusEffectInstance.Parameters} codec field names (1.21.1). / {@code StatusEffectInstance.Parameters} 编解码字段名（1.21.1）。 */
    private static final String ID_KEY = "id";
    private static final String HIDDEN_KEY = "hidden_effect";
    /** Real chains are a few nodes long; this only bounds a corrupt one. / 实际链只有几节；此上限仅防御损坏的数据。 */
    private static final int MAX_CHAIN_DEPTH = 32;
    private static boolean warned;

    private TimeTheftSlowness() {
    }

    /**
     * Applies stage {@code stage} (1..4): amplifier stage - 1, bounded duration, no particles or ambient flag (so nearby
     * players see nothing), icon shown to the victim. Re-applied every stage; vanilla merges it in place. Returns whether
     * this application established the curse's own node ({@link SlownessChain#ownsApplication}, from the chain read
     * just before and just after): false when a same-level foreign node already lasted at least as long (vanilla then
     * left it untouched, so it must never be taken for ours later), when a stronger, longer foreign node swallowed ours,
     * or when the chain could not be read. The caller keeps the answer for the removal.
     * 施加第 {@code stage}（1..4）阶：放大器为阶段 - 1、有界时长、无粒子也非环境效果（附近玩家看不到任何迹象），
     * 受害者可见图标。每阶重新施加，由原版原地合并。返回本次施加是否确立了诅咒自己的节点（{@link SlownessChain#ownsApplication}，
     * 依据施加前后读取的效果链）：已有同级且持续不短于我们的外来节点（原版因此不改动它，之后绝不能把它当成我们的）、
     * 更强且更久的外来节点吞没了我们的效果，或效果链无法读取时返回 false。调用方保存该结果供移除时使用。
     */
    static boolean apply(ServerPlayerEntity victim, int stage) {
        List<SlownessChain.Observed> before = observe(victim);
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                TimeStealerRules.SLOWNESS_DURATION_TICKS, TimeTheftSchedule.amplifier(stage), false, false, true));
        List<SlownessChain.Observed> after = before == null ? null : observe(victim);
        return before != null && after != null && SlownessChain.ownsApplication(before, after,
                TimeTheftSchedule.amplifier(stage), TimeStealerRules.SLOWNESS_DURATION_TICKS);
    }

    /**
     * Removes only the curse's own Slowness node, given the last applied {@code stage}, the ticks elapsed since the
     * theft (which place where our node's remaining duration must be), and whether that stage's application owned its
     * node ({@link #apply}); an unowned stage leaves everything to expire. Never throws.
     * 根据最近施加的 {@code stage}、被窃后经过的 tick（据此推算我们那一节理应剩余的时长），以及该阶段的施加是否拥有其节点
     * （{@link #apply}），只移除诅咒自己的缓慢节点；不拥有时一律任其到期。绝不抛出异常。
     */
    static SlownessChain.Surgery removeOwned(ServerPlayerEntity victim, int stage, long elapsedTicks, boolean owned) {
        int expected = TimeTheftSchedule.expectedSlownessRemaining(stage, elapsedTicks);
        if (!owned || expected <= 0 || victim.getStatusEffect(StatusEffects.SLOWNESS) == null) {
            return SlownessChain.Surgery.UNCHANGED;
        }
        try {
            Chain chain = read(victim);
            if (chain == null || chain.effects().isEmpty()) {
                return SlownessChain.Surgery.UNCHANGED;
            }
            int ourAmplifier = TimeTheftSchedule.amplifier(stage);
            List<SlownessChain.Node> nodes = new ArrayList<>();
            boolean found = false;
            for (StatusEffectInstance decoded : chain.effects()) {
                boolean ours = !found && SlownessChain.isOurs(decoded.getAmplifier(), decoded.getDuration(),
                        decoded.isAmbient(), decoded.shouldShowParticles(), decoded.shouldShowIcon(), ourAmplifier,
                        expected);
                found |= ours;
                nodes.add(new SlownessChain.Node(decoded.getAmplifier(), decoded.getDuration(), ours));
            }
            SlownessChain.Result result = SlownessChain.withoutOurs(nodes);
            if (!result.changed()) {
                return SlownessChain.Surgery.UNCHANGED;
            }
            List<NbtCompound> layers = new ArrayList<>(chain.layers());
            layers.remove(result.removedIndex());
            if (layers.isEmpty()) {
                victim.removeStatusEffect(StatusEffects.SLOWNESS);
                return result.surgery();
            }
            StatusEffectInstance replacement = decode(chain.ops(), nest(layers));
            if (replacement == null) {
                return SlownessChain.Surgery.UNCHANGED;
            }
            // setStatusEffect swaps the map entry and re-applies the attribute modifier at the new level, and the
            // server player sends the updated effect to its own client.
            // setStatusEffect 替换映射条目并按新等级重新应用属性修饰符，服务端玩家会把更新后的效果发送给自己的客户端。
            victim.setStatusEffect(replacement, null);
            return result.surgery();
        } catch (RuntimeException exception) {
            warnOnce(exception);
            return SlownessChain.Surgery.UNCHANGED;
        }
    }

    /**
     * The live chain as seen by {@link SlownessChain#ownsApplication}; null when it cannot be read. Never throws.
     * 供 {@link SlownessChain#ownsApplication} 使用的实时效果链视图；无法读取时为 null。绝不抛出异常。
     */
    private static @Nullable List<SlownessChain.Observed> observe(ServerPlayerEntity victim) {
        try {
            Chain chain = read(victim);
            if (chain == null) {
                return null;
            }
            List<SlownessChain.Observed> observed = new ArrayList<>();
            for (StatusEffectInstance effect : chain.effects()) {
                observed.add(new SlownessChain.Observed(effect.getAmplifier(), effect.getDuration(),
                        SlownessChain.hasCurseFlags(effect.isAmbient(), effect.shouldShowParticles(),
                                effect.shouldShowIcon())));
            }
            return observed;
        } catch (RuntimeException exception) {
            warnOnce(exception);
            return null;
        }
    }

    /**
     * Reads the live Slowness chain, top first: every layer as a standalone compound plus its decoded instance. An
     * absent effect is an empty chain; any decode failure or a corrupt depth returns null.
     * 自顶向下读取实时缓慢效果链：每层为独立的 NBT 结构及其解码实例。没有效果时为空链；任何解码失败或深度异常返回 null。
     */
    private static @Nullable Chain read(ServerPlayerEntity victim) {
        RegistryOps<NbtElement> ops = victim.getRegistryManager().getOps(NbtOps.INSTANCE);
        StatusEffectInstance top = victim.getStatusEffect(StatusEffects.SLOWNESS);
        if (top == null) {
            return new Chain(ops, List.of(), List.of());
        }
        NbtElement encoded = StatusEffectInstance.CODEC.encodeStart(ops, top).result().orElse(null);
        if (!(encoded instanceof NbtCompound root) || !root.contains(ID_KEY)) {
            return null;
        }
        NbtElement typeId = root.get(ID_KEY);
        List<NbtCompound> layers = new ArrayList<>();
        List<StatusEffectInstance> effects = new ArrayList<>();
        NbtCompound cursor = root;
        while (cursor != null) {
            if (layers.size() >= MAX_CHAIN_DEPTH) {
                return null;
            }
            // Every layer is decoded standalone: hidden layers carry only parameters, so the type id is copied in.
            // 每一层单独解码：隐藏层只含参数，因此补上效果类型 id。
            NbtCompound layer = cursor.copy();
            layer.remove(HIDDEN_KEY);
            layer.put(ID_KEY, typeId.copy());
            StatusEffectInstance decoded = decode(ops, layer);
            if (decoded == null) {
                return null;
            }
            layers.add(layer);
            effects.add(decoded);
            cursor = cursor.contains(HIDDEN_KEY, NbtElement.COMPOUND_TYPE) ? cursor.getCompound(HIDDEN_KEY) : null;
        }
        return new Chain(ops, List.copyOf(layers), List.copyOf(effects));
    }

    private static void warnOnce(RuntimeException exception) {
        if (!warned) {
            warned = true;
            SparkWitch.LOGGER.warn("Time Stealer: could not read or remove the curse's own Slowness; leaving it to expire",
                    exception);
        }
    }

    /** Rebuilds the nested compound, top first; only the top keeps the type id. / 自顶向下重建嵌套结构；只有顶层保留类型 id。 */
    private static NbtCompound nest(List<NbtCompound> layers) {
        NbtCompound below = null;
        for (int index = layers.size() - 1; index >= 0; index--) {
            NbtCompound layer = layers.get(index).copy();
            if (index > 0) {
                layer.remove(ID_KEY);
            }
            if (below != null) {
                layer.put(HIDDEN_KEY, below);
            }
            below = layer;
        }
        return below;
    }

    /** One read of the live chain: codec ops, standalone layers, decoded layers. / 一次实时效果链读取：编解码上下文、独立层与解码后的层。 */
    private record Chain(RegistryOps<NbtElement> ops, List<NbtCompound> layers, List<StatusEffectInstance> effects) {
    }

    private static @Nullable StatusEffectInstance decode(RegistryOps<NbtElement> ops, NbtCompound compound) {
        return StatusEffectInstance.CODEC.parse(ops, compound).result().orElse(null);
    }
}
