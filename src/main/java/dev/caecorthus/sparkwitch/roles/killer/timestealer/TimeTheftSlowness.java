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
 * {@code addStatusEffect}; removing reads the live chain through {@link StatusEffectInstance#CODEC} (the hidden chain is
 * private, so an NBT round trip with registry-aware ops is the only public view of it), lets {@link SlownessChain}
 * decide which node is ours, and writes the rest back with {@code setStatusEffect}, so foreign Slowness (a Control
 * Expert stun, Grand Witch Heaviness, other roles) keeps its level and duration. Any decode failure leaves the effect
 * untouched: the curse's node is bounded to {@link TimeStealerRules#SLOWNESS_DURATION_TICKS} and simply runs out.
 * 诅咒缓慢的仅服务端 Minecraft 适配器（计划 D5 / §3.8）。施加是普通的 {@code addStatusEffect}；移除时经
 * {@link StatusEffectInstance#CODEC} 读取实时效果链（隐藏链是私有字段，带注册表上下文的 NBT 往返是唯一的公开视图），
 * 由 {@link SlownessChain} 判定哪一节属于我们，再用 {@code setStatusEffect} 写回其余部分，
 * 因此外来缓慢（控场专家眩晕、大魔女沉重、其他职业）保持原有等级与时长。任何解码失败都不改动效果：
 * 诅咒那一节最长只有 {@link TimeStealerRules#SLOWNESS_DURATION_TICKS}，会自然到期。
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
     * players see nothing), icon shown to the victim. Re-applied every stage; vanilla merges it in place.
     * 施加第 {@code stage}（1..4）阶：放大器为阶段 - 1、有界时长、无粒子也非环境效果（附近玩家看不到任何迹象），
     * 受害者可见图标。每阶重新施加，由原版原地合并。
     */
    static void apply(ServerPlayerEntity victim, int stage) {
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                TimeStealerRules.SLOWNESS_DURATION_TICKS, TimeTheftSchedule.amplifier(stage), false, false, true));
    }

    /**
     * Removes only the curse's own Slowness node, given the last applied {@code stage} and the ticks elapsed since the
     * theft (which place where our node's remaining duration must be). Never throws.
     * 根据最近施加的 {@code stage} 与被窃后经过的 tick（据此推算我们那一节理应剩余的时长），只移除诅咒自己的缓慢节点。
     * 绝不抛出异常。
     */
    static SlownessChain.Surgery removeOwned(ServerPlayerEntity victim, int stage, long elapsedTicks) {
        int expected = TimeTheftSchedule.expectedSlownessRemaining(stage, elapsedTicks);
        if (expected <= 0) {
            return SlownessChain.Surgery.UNCHANGED;
        }
        StatusEffectInstance top = victim.getStatusEffect(StatusEffects.SLOWNESS);
        if (top == null) {
            return SlownessChain.Surgery.UNCHANGED;
        }
        try {
            RegistryOps<NbtElement> ops = victim.getRegistryManager().getOps(NbtOps.INSTANCE);
            NbtElement encoded = StatusEffectInstance.CODEC.encodeStart(ops, top).result().orElse(null);
            if (!(encoded instanceof NbtCompound root) || !root.contains(ID_KEY)) {
                return SlownessChain.Surgery.UNCHANGED;
            }
            NbtElement typeId = root.get(ID_KEY);
            int ourAmplifier = TimeTheftSchedule.amplifier(stage);
            List<NbtCompound> layers = new ArrayList<>();
            List<SlownessChain.Node> nodes = new ArrayList<>();
            boolean found = false;
            NbtCompound cursor = root;
            while (cursor != null) {
                if (layers.size() >= MAX_CHAIN_DEPTH) {
                    return SlownessChain.Surgery.UNCHANGED;
                }
                // Every layer is decoded standalone: hidden layers carry only parameters, so the type id is copied in.
                // 每一层单独解码：隐藏层只含参数，因此补上效果类型 id。
                NbtCompound layer = cursor.copy();
                layer.remove(HIDDEN_KEY);
                layer.put(ID_KEY, typeId.copy());
                StatusEffectInstance decoded = decode(ops, layer);
                if (decoded == null) {
                    return SlownessChain.Surgery.UNCHANGED;
                }
                boolean ours = !found && SlownessChain.isOurs(
                        decoded.getAmplifier(), decoded.getDuration(), ourAmplifier, expected);
                found |= ours;
                layers.add(layer);
                nodes.add(new SlownessChain.Node(decoded.getAmplifier(), decoded.getDuration(), ours));
                cursor = cursor.contains(HIDDEN_KEY, NbtElement.COMPOUND_TYPE) ? cursor.getCompound(HIDDEN_KEY) : null;
            }
            SlownessChain.Result result = SlownessChain.withoutOurs(nodes);
            if (!result.changed()) {
                return SlownessChain.Surgery.UNCHANGED;
            }
            layers.remove(result.removedIndex());
            if (layers.isEmpty()) {
                victim.removeStatusEffect(StatusEffects.SLOWNESS);
                return result.surgery();
            }
            StatusEffectInstance replacement = decode(ops, nest(layers));
            if (replacement == null) {
                return SlownessChain.Surgery.UNCHANGED;
            }
            // setStatusEffect swaps the map entry and re-applies the attribute modifier at the new level, and the
            // server player sends the updated effect to its own client.
            // setStatusEffect 替换映射条目并按新等级重新应用属性修饰符，服务端玩家会把更新后的效果发送给自己的客户端。
            victim.setStatusEffect(replacement, null);
            return result.surgery();
        } catch (RuntimeException exception) {
            if (!warned) {
                warned = true;
                SparkWitch.LOGGER.warn("Time Stealer: could not remove the curse's own Slowness; leaving it to expire",
                        exception);
            }
            return SlownessChain.Surgery.UNCHANGED;
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

    private static @Nullable StatusEffectInstance decode(RegistryOps<NbtElement> ops, NbtCompound compound) {
        return StatusEffectInstance.CODEC.parse(ops, compound).result().orElse(null);
    }
}
