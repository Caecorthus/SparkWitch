package dev.caecorthus.sparkwitch.client.savior;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.net.SaviorLightsaberIgniteC2SPayload;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Lightsaber ignition state, per hand-held stack. The model predicate reads {@code sparkwitch:held_frame}; only the
 * client renders, so nothing is synced and nothing changes server-side. The off/not-held appearance is the base model
 * (ls_0). After the stack has been held continuously for {@value #IGNITION_DELAY_TICKS} ticks (0.95 s), the client asks
 * the server to broadcast the ignition sound once and ls_0 → ls_19 runs over {@value #IGNITION_DURATION_TICKS} tick
 * (0.05 s); it then stays on the lit texture until it leaves both hands, at which point its start time is dropped and
 * the next pickup re-ignites. Only the local player's own holds send the ignition intent, from the client tick, so
 * rendering a bystander's saber (or hiding the hand with F1) never changes what is sent.
 * 光剑点火状态，按手持物品堆记录。模型谓词读取 {@code sparkwitch:held_frame}；只在客户端渲染，不同步、不改服务端。
 * 未持有/背包外观是基础模型（ls_0）。连续手持满 {@value #IGNITION_DELAY_TICKS} 刻（0.95 秒）后请求服务端广播一次点火音，
 * 并在 {@value #IGNITION_DURATION_TICKS} 刻（0.05 秒）内跑完 ls_0 → ls_19，然后停在点亮贴图；离开双手时丢弃起始时间，
 * 下次拿起会重新点火。只有本地玩家自己的持握会在客户端刻中发送点火意图，因此渲染旁人的光剑（或用 F1 隐藏手部）
 * 都不会改变发送内容。
 */
public final class SaviorLightsaberAnimation {
    /** Stable client model predicate id read by the lightsaber model overrides. / 光剑模型覆盖读取的稳定客户端谓词 id。 */
    public static final Identifier HELD_FRAME_ID = SparkWitch.id("held_frame");
    /**
     * Selects the unlit hilt model for the held pre-ignition window. Non-held/GUI renders never match it, so they fall
     * back to the base model, which is the lit texture.
     * 为手持预热窗口选择未点亮的剑柄模型。非手持/GUI 渲染不会命中，因此回落到基础上点亮贴图。
     */
    public static final Identifier PREIGNITION_ID = SparkWitch.id("preignition");
    /** Continuous hold required before ignition: 0.95 s = 19 ticks. / 点火前的连续手持时间：0.95 秒 = 19 刻。 */
    public static final double IGNITION_DELAY_TICKS = 19.0D;
    /**
     * Ignition spans 0.05 s = 1 client tick. Frames are sampled from render time, not tick count, so several frames can
     * be skipped or shown within one tick depending on FPS.
     * 点火全程 0.05 秒 = 1 客户端刻。帧由渲染时间采样而非 tick 计数，因此一 tick 内可以跳过或显示多帧，取决于 FPS。
     */
    public static final double IGNITION_DURATION_TICKS = 1.0D;
    /** Ignition frames ls_0..ls_19; frame 20 is the lit texture. / 点火帧 ls_0..ls_19；第 20 帧为点亮贴图。 */
    public static final int IGNITION_FRAMES = 20;

    /**
     * Start time (world ticks, with render partial tick) keyed by stack identity: two identical lightsabers must ignite
     * independently. Only touched on the client thread by the tick and render callbacks.
     * 起始时间（世界刻 + 渲染偏 tick），按物品堆身份键控：两把相同光剑必须独立点火。只在客户端线程的 tick 与渲染回调中访问。
     */
    private static final Map<ItemStack, Double> HELD_START = new IdentityHashMap<>();
    /** Stacks whose ignition intent has already been sent for the current hold. / 本次持有中已发送过点火意图的物品堆。 */
    private static final Set<ItemStack> IGNITE_SENT =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /** One looping hum per held lightsaber stack, following its holder. / 每把手持光剑一个循环嗡鸣，跟随持有者。 */
    private static final Map<ItemStack, SaviorLightsaberHumSoundInstance> HUMS = new IdentityHashMap<>();
    /**
     * Stacks whose blade was out when they left both hands, with the world tick of that release. The first-person
     * hand keeps drawing the old stack while it lowers after a hotbar switch; this keeps a lit saber lit and an unlit
     * one on its hilt during that window. / 离开双手时刀刃已伸出的物品堆及其离手世界刻。切换快捷栏后第一人称手部在放下
     * 期间仍绘制旧物品堆；据此让已点亮的光剑保持点亮、未点亮的保持剑柄。
     */
    private static final Map<ItemStack, Long> RELEASED_LIT = new IdentityHashMap<>();
    /** The first-person lower takes a few ticks; one second is ample. / 第一人称放下只需几刻，一秒足够。 */
    private static final long RELEASED_LIT_TICKS = 20L;
    /** A hum the sound engine dropped (or never started) is retried at most once a second. / 被声音引擎丢弃（或未能开始）的嗡鸣最多每秒重试一次。 */
    private static final long HUM_RETRY_TICKS = 20L;

    private SaviorLightsaberAnimation() {
    }

    public static void register() {
        FabricModelPredicateProviderRegistry.register(HELD_FRAME_ID, (stack, world, entity, seed) -> {
            if (world == null || !(entity instanceof PlayerEntity player)
                    || !stack.isOf(SparkWitchItems.saviorLightsaber())) {
                return 1.0F;
            }
            // A stack outside both hands (inventory slots, the lowering hand) never starts a hold from render code:
            // no frame, so the preignition and base models decide. / 不在双手中的物品堆（物品栏格、正在放下的手）不会由
            // 渲染代码开始计时：不选帧，由预热模型与基础模型决定。
            return isHeldBy(player, stack) ? frameValue(stack, world) : 0.0F;
        });
        // Held pre-ignition (including the first animation frame) uses the hilt model; everything else, including the
        // inventory GUI, falls through to the lit base model. This does not depend on the GUI passing a null entity.
        // 手持预热（含动画第 0 帧）使用剑柄模型；其他情况（包括物品栏 GUI）回落到点亮的 base 模型。不依赖 GUI 传 null 实体。
        FabricModelPredicateProviderRegistry.register(PREIGNITION_ID, (stack, world, entity, seed) -> {
            if (world == null || !(entity instanceof PlayerEntity player)
                    || !stack.isOf(SparkWitchItems.saviorLightsaber())) {
                return 0.0F;
            }
            if (!isHeldBy(player, stack)) {
                // Drawn in a hand but no longer held: the first-person lower after a switch. Keep the hilt unless the
                // blade was out at release; GUI and world renders keep the lit base model.
                // 在手中绘制但已不再持有：切换后的第一人称放下。除非离手时刀刃已伸出，否则保持剑柄；GUI 与世界渲染
                // 保持点亮的基础模型。
                return SaviorHandRenderScope.isRendering(stack) && !RELEASED_LIT.containsKey(stack) ? 1.0F : 0.0F;
            }
            Double start = HELD_START.get(stack);
            if (start == null) {
                return 1.0F;
            }
            double elapsed = renderTime(world) - start;
            double frameZeroEnd = IGNITION_DELAY_TICKS + IGNITION_DURATION_TICKS / IGNITION_FRAMES;
            return elapsed < frameZeroEnd ? 1.0F : 0.0F;
        });
        ClientTickEvents.END_CLIENT_TICK.register(SaviorLightsaberAnimation::tick);
        // Per rendered frame, so the hum starts on the same frame as the ignition even for other players' sabers.
        // 每个渲染帧检查一次，让嗡鸣与点火在同一帧开始，其他玩家的光剑也一样。
        WorldRenderEvents.END.register(context -> tickHums());
    }

    /** Starts/stops the looping hum for every player whose blade is currently out. / 为所有刀刃已伸出的玩家启停循环嗡鸣。 */
    private static void tickHums() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.getSoundManager() == null) {
            stopAllHums();
            return;
        }
        Set<ItemStack> active = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            trackHum(player, player.getMainHandStack(), active, client);
            trackHum(player, player.getOffHandStack(), active, client);
        }
        Iterator<Map.Entry<ItemStack, SaviorLightsaberHumSoundInstance>> iterator = HUMS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ItemStack, SaviorLightsaberHumSoundInstance> entry = iterator.next();
            if (!active.contains(entry.getKey())) {
                entry.getValue().stop();
                iterator.remove();
            }
        }
    }

    private static boolean isHeldBy(PlayerEntity player, ItemStack stack) {
        return player.getMainHandStack() == stack || player.getOffHandStack() == stack;
    }

    /**
     * Keeps one playing hum per lit stack. A hum the sound engine stopped or never started (sound reload, no free
     * channel, no device) is replaced, at most once per {@value #HUM_RETRY_TICKS} ticks so a failing engine is not
     * asked every frame; a silent holder keeps a muted hum ({@link SaviorLightsaberHumSoundInstance}).
     * 每把点亮的物品堆保持一个正在播放的嗡鸣。被声音引擎停止或未能开始的嗡鸣（声音重载、无空闲声道、无设备）会被替换，
     * 每 {@value #HUM_RETRY_TICKS} 刻最多一次，避免每帧询问失败的引擎；静音的持有者保留一个静音嗡鸣。
     */
    private static void trackHum(PlayerEntity holder, ItemStack stack, Set<ItemStack> active, MinecraftClient client) {
        if (!stack.isOf(SparkWitchItems.saviorLightsaber()) || !isBladeExtended(stack)) {
            return;
        }
        active.add(stack);
        SoundManager sounds = client.getSoundManager();
        long now = holder.getWorld().getTime();
        SaviorLightsaberHumSoundInstance hum = HUMS.get(stack);
        if (hum != null && (sounds.isPlaying(hum) || now - hum.startedAt() < HUM_RETRY_TICKS)) {
            return;
        }
        if (hum != null) {
            hum.stop();
        }
        hum = new SaviorLightsaberHumSoundInstance(holder, stack, now);
        HUMS.put(stack, hum);
        sounds.play(hum);
    }

    private static void stopAllHums() {
        for (SaviorLightsaberHumSoundInstance hum : HUMS.values()) {
            hum.stop();
        }
        HUMS.clear();
    }

    private static float frameValue(ItemStack stack, ClientWorld world) {
        double now = renderTime(world);
        double start = HELD_START.computeIfAbsent(stack, ignored -> now);
        double elapsed = now - start;
        if (elapsed < IGNITION_DELAY_TICKS) {
            return 0.0F;
        }
        double progress = (elapsed - IGNITION_DELAY_TICKS) / IGNITION_DURATION_TICKS;
        // ClampedModelPredicateProvider.call() clamps to 0..1, so frames must be normalized: frame f sits at
        // (f + 0.5) / 20, safely between the model override thresholds 0.05..0.95; frame 20 is the lit model 1.0.
        // ClampedModelPredicateProvider.call() 会把值钳制到 0..1，所以必须归一化：第 f 帧取 (f+0.5)/20，
        // 落在模型阈值 0.05..0.95 之间；第 20 帧返回 1.0 选择点亮模型。
        if (progress >= 1.0D) {
            return 1.0F;
        }
        int frame = (int) (progress * IGNITION_FRAMES);
        return (frame + 0.5F) / IGNITION_FRAMES;
    }

    /**
     * True once the blade has at least one visible ignition frame. The LambDynamicLights source reads this so the item
     * emits no light while fully retracted, during the 0.9 s hold, and on the first (bare-hilt) frame.
     * 刀刃至少伸出一帧后返回 true。LambDynamicLights 光源据此在完全未伸出、0.9 秒蓄力期间以及第 0 帧（只有剑柄）不发光。
     */
    public static boolean isBladeExtended(ItemStack stack) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return false;
        }
        Double start = HELD_START.get(stack);
        if (start == null) {
            return false;
        }
        double progress = (renderTime(world) - start - IGNITION_DELAY_TICKS) / IGNITION_DURATION_TICKS;
        if (progress >= 1.0D) {
            return true;
        }
        return progress > 0.0D && (int) (progress * IGNITION_FRAMES) >= 1;
    }

    /** Game time with the current frame's partial tick, so the frame changes between client ticks. / 带渲染偏 tick 的游戏时间。 */
    private static double renderTime(ClientWorld world) {
        // Cast to double before adding: long + float would round the world tick count through a float and lose the
        // sub-tick precision (and eventually whole ticks) on long-lived worlds.
        // 先转 double 再相加：long + float 会把世界刻数压进 float，长寿命存档会丢失偏 tick 精度甚至整刻。
        return (double) world.getTime()
                + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null) {
            HELD_START.clear();
            IGNITE_SENT.clear();
            RELEASED_LIT.clear();
            return;
        }
        double now = renderTime(client.world);
        long worldTime = client.world.getTime();
        Set<ItemStack> stillHeld = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PlayerEntity player : client.world.getPlayers()) {
            track(player.getMainHandStack(), stillHeld, now);
            track(player.getOffHandStack(), stillHeld, now);
        }
        RELEASED_LIT.keySet().removeAll(stillHeld);
        RELEASED_LIT.values().removeIf(releasedAt -> worldTime - releasedAt > RELEASED_LIT_TICKS
                || worldTime < releasedAt);
        for (ItemStack released : HELD_START.keySet()) {
            if (!stillHeld.contains(released) && isBladeExtended(released)) {
                RELEASED_LIT.put(released, worldTime);
            }
        }
        HELD_START.keySet().retainAll(stillHeld);
        IGNITE_SENT.retainAll(stillHeld);
        ClientPlayerEntity local = client.player;
        if (local != null && !local.isSpectator()) {
            sendIgnitionOnce(local.getMainHandStack(), now);
            sendIgnitionOnce(local.getOffHandStack(), now);
        }
    }

    /**
     * The local player's ignition intent, once per hold, on the tick the blade starts: the server broadcasts the
     * ignition sound at the holder, so nearby players hear it (including other clients that render this saber).
     * 本地玩家的点火意图，每次持握只发一次，在刀刃开始伸出的那一刻发送：服务端在持有者位置广播点火音，附近玩家
     * （包括渲染这把光剑的其他客户端）都能听到。
     */
    private static void sendIgnitionOnce(ItemStack stack, double now) {
        if (!stack.isOf(SparkWitchItems.saviorLightsaber())) {
            return;
        }
        Double start = HELD_START.get(stack);
        if (start == null || now - start < IGNITION_DELAY_TICKS || !IGNITE_SENT.add(stack)) {
            return;
        }
        if (ClientPlayNetworking.canSend(SaviorLightsaberIgniteC2SPayload.ID)) {
            ClientPlayNetworking.send(new SaviorLightsaberIgniteC2SPayload());
        }
    }

    /**
     * Connection edges ({@code SparkWitchClient.releaseClosedWorldPlayers}, client thread): stops every hum, whose
     * instances hold their player, and drops every stack-keyed state, so nothing keeps a closed world alive.
     * 连接边界（{@code SparkWitchClient.releaseClosedWorldPlayers}，客户端线程）：停止所有嗡鸣（其实例持有玩家），并丢弃
     * 所有按物品堆记录的状态，确保不会让已关闭的世界继续存活。
     */
    public static void reset() {
        stopAllHums();
        HELD_START.clear();
        IGNITE_SENT.clear();
        RELEASED_LIT.clear();
    }

    private static void track(ItemStack stack, Set<ItemStack> stillHeld, double now) {
        if (!stack.isOf(SparkWitchItems.saviorLightsaber())) {
            return;
        }
        stillHeld.add(stack);
        HELD_START.putIfAbsent(stack, now);
    }
}
