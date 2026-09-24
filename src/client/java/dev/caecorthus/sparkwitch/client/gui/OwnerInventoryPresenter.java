package dev.caecorthus.sparkwitch.client.gui;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchClientPresentation;
import dev.caecorthus.sparkwitch.client.text.WitchSkillClientTexts;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.mana.WitchManaRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.caecorthus.sparkwitch.skill.WitchSkillHudRules;
import dev.caecorthus.sparkwitch.skill.WitchSkillPresentationRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class OwnerInventoryPresenter {
    private record Frame(InventoryRenderScope.Binding binding, InventoryInfoCard.Snapshot card, boolean ownsTraits) {}
    private static final InventoryRenderScope<Frame> CALLS = new InventoryRenderScope<>();
    private static final TraitsInventoryBridge TRAITS = new TraitsInventoryBridge(
            OwnerInventoryPresenter::traitsFacade, PlayerEntity.class, OwnerInventoryPresenter::ownsTraits);
    private static final SkillProgressTracker PROGRESS = new SkillProgressTracker();
    private static long clientTicks;
    // Connection-scoped failure latches (cleared by reset): a failing card stops drawing and logs once instead of
    // throwing, logging and half-drawing every frame. 按连接的失败锁存（reset 清除）：失败后停止绘制且只记录一次日志。
    private static boolean cardFailed;
    private static boolean prepareWarned;
    // The screen whose card drew the mana tail in its last frame; the mana HUD row hides for it. 上一帧显示魔力尾注的界面。
    private static Screen manaTailScreen;
    private OwnerInventoryPresenter() {}

    private static Class<?> traitsFacade() {
        if (!FabricLoader.getInstance().isModLoaded("sparktraits")) return null;
        try { return Class.forName("dev.caecorthus.sparktraits.api.SparkTraitsApi"); }
        catch (ClassNotFoundException | LinkageError unavailable) { return null; }
    }

    /** Called outside the wrapped body, before ANY render HEAD/TAIL and closed after ALL TAILs. */
    public interface RenderCall extends AutoCloseable { @Override void close(); }

    public static RenderCall begin(Screen screen, PlayerEntity player, TextRenderer font) {
        var scope = CALLS.open();
        MinecraftClient client = MinecraftClient.getInstance();
        // After a draw failure nothing is published, so Traits keeps its native card. 绘制失败后不再发布，天赋使用原生卡。
        if (cardFailed) return scope::close;
        if (player != client.player || client.world == null || player.getWorld() != client.world || client.currentScreen != screen) return scope::close;
        try {
            List<InventoryInfoCard.Section> sections = new ArrayList<>();
            var skill = skillSection(player);
            if (skill != null) sections.add(skill);
            var traits = TRAITS.collect(player, (tag, tooltip) -> new InventoryInfoCard.Entry(
                    List.of((Text) tag), tooltip.stream().map(t -> (Text) t).toList()));
            traits.ifPresent(entries -> {
                if (!entries.isEmpty()) sections.add(new InventoryInfoCard.Section(Text.translatable("gui.sparktraits.traits"), entries));
            });
            // Publish only AFTER all text conversion, eligibility, collection and geometry succeeded.
            var card = InventoryInfoCard.prepare(screen, font, sections,
                    FabricLoader.getInstance().isModLoaded("sparktraits"), traits.isPresent());
            scope.publish(new Frame(new InventoryRenderScope.Binding(screen, player, client.world, client.getNetworkHandler()), card, traits.isPresent()));
        } catch (RuntimeException | LinkageError failure) {
            // No committed snapshot: Traits keeps its native TAIL and Witch uses its native fallback. Warn once per
            // connection; the fallback retries every frame. 每个连接只警告一次，回退路径每帧重试。
            if (!prepareWarned) {
                prepareWarned = true;
                SparkWitch.LOGGER.warn("Could not prepare owner inventory card; retaining native presentation", failure);
            }
        }
        return scope::close;
    }

    private static Frame current() {
        Frame frame = CALLS.current();
        MinecraftClient client = MinecraftClient.getInstance();
        return frame != null && frame.binding.matches(client.currentScreen, client.player, client.world, client.getNetworkHandler()) ? frame : null;
    }
    private static boolean ownsTraits() { Frame frame = current(); return frame != null && frame.ownsTraits; }

    public static void draw(Screen screen, PlayerEntity player, TextRenderer font, DrawContext context, int mouseX, int mouseY) {
        manaTailScreen = null;
        if (cardFailed) return;
        Frame frame = current();
        try {
            InventoryInfoCard.Snapshot card;
            if (frame != null && frame.binding.screen() == screen && frame.binding.player() == player) card = frame.card;
            else {
                var skill = skillSection(player);
                card = InventoryInfoCard.prepare(screen, font, skill == null ? List.of() : List.of(skill),
                        FabricLoader.getInstance().isModLoaded("sparktraits"), false);
            }
            InventoryInfoCard.draw(context, font, card, mouseX, mouseY, screen.width, screen.height);
            if (showsManaTail(card)) manaTailScreen = screen;
        } catch (RuntimeException | LinkageError failure) {
            // The current commitment stays frozen: a previously skipped Traits TAIL cannot be replayed.
            // Stop drawing and taking over subsequent frames until the next connection lifecycle reset, and log once.
            // 当前帧的承诺无法撤回；此后直到连接重置都不再绘制或接管天赋，并只记录一次错误。
            cardFailed = true;
            TRAITS.disable();
            SparkWitch.LOGGER.error("Owner inventory card draw failed; disabling the card for this connection", failure);
        }
    }

    /**
     * Mana HUD hook: true while {@code screen}'s owner card showed the mana tail in its last frame. The tail then
     * replaces the HUD mana row (spec-final §2.1), also when the card sits below y = 20 and does not cover the row.
     * 魔力 HUD 钩子：该界面的卡片上一帧显示了魔力尾注时为真；尾注取代 HUD 魔力行，卡片不在顶端时也不重复显示。
     */
    public static boolean showsManaTail(Screen screen) { return screen != null && screen == manaTailScreen; }

    /** The skill section (with a mana tail) exists and its header row is laid out. 技能分节带魔力尾注且标题行已排版。 */
    private static boolean showsManaTail(InventoryInfoCard.Snapshot card) {
        var box = card.layout().bounds();
        if (box.width() < 6 || box.height() < 6 || card.sections().isEmpty()) return false;
        var skill = card.sections().getFirst();
        return skill.tail() != null && skill.entries().stream().anyMatch(entry -> entry.status() != null)
                && card.layout().rows().stream().anyMatch(row -> row.heading() && row.section() == 0);
    }

    public static void reset() {
        CALLS.invalidate(); TRAITS.reset(); PROGRESS.reset();
        cardFailed = false; prepareWarned = false; manaTailScreen = null;
    }

    /**
     * END_CLIENT_TICK hook (confirmed server only): records the local owner's panel-skill countdowns every tick,
     * also while the inventory is closed, so the gauge knows each phase's total when the card opens mid-phase.
     * Eligibility is the same three-role panel rule; any other role only clears the tracker.
     * 客户端每刻记录本地玩家面板技能的倒计时（背包关闭时也记录），中途打开背包时进度条仍知道阶段总量；
     * 资格与面板相同（仅三个魔女技能职业），其他职业只会清空记录。
     */
    public static void tick(MinecraftClient client) {
        // Drop a closed screen so it is not retained. 界面关闭后释放引用。
        if (manaTailScreen != null && client.currentScreen != manaTailScreen) manaTailScreen = null;
        if (client.isPaused()) return;
        clientTicks++;
        PlayerEntity player = client.player;
        if (player == null || client.world == null || player.getWorld() != client.world) { PROGRESS.reset(); return; }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        Identifier skillId = component.getActiveSkillId();
        var role = GameWorldComponent.KEY.get(client.world).getRole(player);
        if (skillId == null || !WitchSkillPresentationRules.shouldShowInventorySkillPanel(role, skillId)) { PROGRESS.reset(); return; }
        PROGRESS.observe(skillId, true, component.getActiveSkillWindowTicks(), SkillProgressTracker.candidates(skillId, true), clientTicks);
        PROGRESS.observe(skillId, false, component.getCooldownTicks(), SkillProgressTracker.candidates(skillId, false), clientTicks);
    }

    private static InventoryInfoCard.Section skillSection(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SparkWitchServerConnection.isConfirmedServer() || player == null || player != client.player
                || client.world == null || player.getWorld() != client.world) return null;
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        var skillId = component.getActiveSkillId();
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        // Do not gate on usability: active, locked, and cooling-down skills are still owned skills.
        if (skillId == null || !WitchSkillPresentationRules.shouldShowInventorySkillPanel(role, skillId)) return null;
        WitchSkillDefinition definition = WitchSkillRegistry.get(skillId);
        int cost = definition == null ? 0 : definition.manaCost();
        int mana = component.getMana();
        int activeTicks = component.getActiveSkillWindowTicks();
        int cooldownTicks = component.getCooldownTicks();
        // Mana (header tail, cost, NO_MANA) is additionally gated by the mana-role rule. 魔力展示另受魔力职业规则约束。
        boolean manaShown = component.hasManaSystem() && WitchManaRules.isManaRole(role);
        Text label = stateText(component);
        InventoryInfoCard.Kind tooltipKind = kindOf(label);
        InventoryInfoCard.Kind kind = tooltipKind;
        // NO_MANA refines READY only; the precedence itself stays in stateText. 魔力不足只细化“可使用”。
        if (kind == InventoryInfoCard.Kind.READY && manaShown && mana < cost) {
            kind = InventoryInfoCard.Kind.NO_MANA;
            label = Text.translatable("gui.sparkwitch.skill.pill.no_mana");
        }
        int tasks = GrandWitchRules.clampCeremonialSwordTaskProgress(component.getGrandWitchCeremonialSwordTasks());
        int charges = kind == InventoryInfoCard.Kind.ACTIVE && MurderousWitchDeathRayRules.isDeathRaySkill(skillId)
                ? Math.max(0, Math.min(MurderousWitchDeathRayRules.MAX_CHARGES, component.getDeathRayCharges())) : -1;
        float delta = client.getRenderTickCounter().getTickDelta(true);
        float progress = switch (kind) {
            case ACTIVE -> PROGRESS.progress(skillId, true, activeTicks, clientTicks, delta);
            case COOLDOWN -> PROGRESS.progress(skillId, false, cooldownTicks, clientTicks, delta);
            case NO_MANA -> cost <= 0 ? 1f : Math.max(0f, Math.min(1f, mana / (float) cost));
            case LOCKED -> tasks / (float) GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS;
            case READY -> 1f;
        };
        int pips = kind == InventoryInfoCard.Kind.LOCKED ? GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS
                : charges >= 0 ? MurderousWitchDeathRayRules.MAX_CHARGES : 0;
        boolean costShort = manaShown && mana < cost;
        // The card cannot restyle a nested translation argument, so the presenter colours the numbers; the root
        // stays white so the mana glyph keeps its own colours. 卡片无法改写嵌套参数样式，数字颜色由此处设置。
        Text costText = manaShown && cost > 0 ? Text.translatable("gui.sparkwitch.mana", Text.literal(String.valueOf(cost))
                .withColor((costShort ? InventoryCardPaint.ALERT_TEXT : InventoryCardPaint.MUTED) & 0xFFFFFF)).withColor(0xFFFFFF) : null;
        Text manaTail = manaShown ? Text.translatable("gui.sparkwitch.mana", Text.literal(String.valueOf(mana))
                .withColor(InventoryCardPaint.MANA & 0xFFFFFF)).withColor(0xFFFFFF) : null;
        int widest = SkillProgressTracker.widestSeconds(skillId);
        var status = new InventoryInfoCard.Status(kind, label, shortLabel(kind, label), reserveLabels(widest, false),
                reserveLabels(widest, true), progress, pips, kind == InventoryInfoCard.Kind.LOCKED ? tasks : 0, charges,
                costText, costShort);
        List<Text> tooltip = new ArrayList<>(WitchSkillClientTexts.tooltip(skillId, cooldownTicks,
                activeTicks, component.getGrandWitchCeremonialSwordTasks()));
        // The shared tooltip already ends with the same active/locked/cooldown/ready status: recolour that line by
        // its own precedence instead of adding the pill label again. The mana and charge lines are extra facts.
        // 共享提示末行已是同一状态：按其自身优先级着色，不重复添加状态；魔力与发数为附加信息行。
        // In NO_MANA the shared "ready" line only means "off cooldown": it is MUTED, not READY green, so it does not
        // contradict the red mana line below, which alone carries the alert.
        // 魔力不足时不再用绿色表示可用，避免与红色魔力行冲突；该行仅表示“不在冷却”，由红色魔力行单独提示。
        if (!tooltip.isEmpty()) {
            int last = tooltip.size() - 1;
            int statusColor = kind == InventoryInfoCard.Kind.NO_MANA ? InventoryCardPaint.MUTED : InventoryCardPaint.kindText(tooltipKind);
            tooltip.set(last, tooltip.get(last).copy().withColor(statusColor & 0xFFFFFF));
        }
        if (kind == InventoryInfoCard.Kind.NO_MANA) {
            Text manaLine = Text.translatable("hud.sparkwitch.skill.not_enough_mana", cost).withColor(InventoryCardPaint.ALERT_TEXT & 0xFFFFFF);
            tooltip.add(manaLine);
        }
        if (charges >= 0) {
            Text chargesLine = Text.translatable("gui.sparkwitch.skill.death_ray.charges", charges).withColor(InventoryCardPaint.ACTIVE_TEXT & 0xFFFFFF);
            tooltip.add(chargesLine);
        }
        var entry = new InventoryInfoCard.Entry(List.of(WitchSkillClientTexts.name(skillId), label), tooltip,
                WitchSkillClientTexts.color(skillId), status);
        List<InventoryInfoCard.Entry> entries = new ArrayList<>(List.of(entry));
        if (GrandWitchClientPresentation.isGrandWitch(client.player)) entries.addAll(grandWitchEntries(client.player));
        return new InventoryInfoCard.Section(Text.translatable("gui.sparkwitch.skills"), entries, manaTail,
                manaTail == null ? null : Text.literal("888 \uE782"), Text.translatable("gui.sparkwitch.skills.short"));
    }

    /**
     * Grand Witch's Recruit and Ceremonial Sword are her own abilities but not the panel's active skill (Witch Factor);
     * they are listed as plain entries only after the same three-role panel gate passed.
     * 大魔女的招募与仪礼剑属于其自有能力但不是面板主技能（魔女因子）；仅在同一三职业面板资格通过后作为普通条目列出。
     */
    private static List<InventoryInfoCard.Entry> grandWitchEntries(ClientPlayerEntity player) {
        int color = GrandWitchClientPresentation.COLOR & 0xFFFFFF;
        List<Text> recruit = List.of(Text.translatable("skill.sparkwitch.recruit_accomplice.name").withColor(color),
                GrandWitchClientPresentation.recruitmentState(player));
        List<Text> sword = new ArrayList<>();
        sword.add(Text.translatable("skill.sparkwitch.ceremonial_sword.name").withColor(color));
        sword.addAll(GrandWitchClientPresentation.swordStates(player));
        return List.of(
                new InventoryInfoCard.Entry(recruit, List.of(
                        Text.translatable("skill.sparkwitch.recruit_accomplice.description"),
                        Text.translatable("skill.sparkwitch.recruit_accomplice.inventory"))),
                new InventoryInfoCard.Entry(sword, List.of(
                        Text.translatable("skill.sparkwitch.ceremonial_sword.description"),
                        Text.translatable("skill.sparkwitch.ceremonial_sword.protection"))));
    }

    /**
     * The pill label and the card's only status precedence (WitchSkillClientTexts.tooltip keeps the same order).
     * 状态牌文本，也是卡片唯一的状态优先级（与 WitchSkillClientTexts.tooltip 顺序一致）。
     */
    private static Text stateText(WitchPlayerComponent component) {
        int activeTicks = component.getActiveSkillWindowTicks();
        if (activeTicks > 0) return Text.translatable("gui.sparkwitch.skill.pill.active", secs(activeTicks));
        if (WitchSkillHudRules.shouldShowCeremonialSwordTaskUnlock(component.getActiveSkillId(),
                component.getGrandWitchCeremonialSwordTasks(), activeTicks, component.getCooldownTicks())) {
            return Text.translatable("gui.sparkwitch.skill.pill.locked",
                    component.getGrandWitchCeremonialSwordTasks(), GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS);
        }
        if (component.getCooldownTicks() > 0) return Text.translatable("gui.sparkwitch.skill.pill.cooldown", secs(component.getCooldownTicks()));
        return Text.translatable("gui.sparkwitch.skill.ready");
    }

    /** Kind of a stateText label, read back from its key so stateText stays the only precedence. 由键名反推状态。 */
    private static InventoryInfoCard.Kind kindOf(Text label) {
        String key = label.getContent() instanceof TranslatableTextContent translation ? translation.getKey() : "";
        return switch (key) {
            case "gui.sparkwitch.skill.pill.active" -> InventoryInfoCard.Kind.ACTIVE;
            case "gui.sparkwitch.skill.pill.locked" -> InventoryInfoCard.Kind.LOCKED;
            case "gui.sparkwitch.skill.pill.cooldown" -> InventoryInfoCard.Kind.COOLDOWN;
            default -> InventoryInfoCard.Kind.READY;
        };
    }

    /** Narrow-card pill label with the same arguments (LOCKED has no shorter form). 窄卡片的短标签，参数相同。 */
    private static Text shortLabel(InventoryInfoCard.Kind kind, Text label) {
        Object[] args = label.getContent() instanceof TranslatableTextContent translation ? translation.getArgs() : new Object[0];
        return switch (kind) {
            case READY -> Text.translatable("gui.sparkwitch.skill.pill.short.ready");
            case ACTIVE -> Text.translatable("gui.sparkwitch.skill.pill.short.active", args);
            case COOLDOWN -> Text.translatable("gui.sparkwitch.skill.pill.short.cooldown", args);
            case LOCKED -> label;
            case NO_MANA -> Text.translatable("gui.sparkwitch.skill.pill.short.no_mana");
        };
    }

    /**
     * Reserve labels for every kind at the skill's widest countdown: the card measures the pill and its own width
     * from these, never from the live label, so the rect stays identical in every state and second.
     * 各状态在最长倒计时下的预留标签：卡片据此测量状态牌与卡片宽度，从不使用实时标签，因此尺寸恒定。
     */
    private static Map<InventoryInfoCard.Kind, Text> reserveLabels(int widestSeconds, boolean compact) {
        int tasks = GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS;
        Map<InventoryInfoCard.Kind, Text> labels = new EnumMap<>(InventoryInfoCard.Kind.class);
        labels.put(InventoryInfoCard.Kind.READY, Text.translatable(compact ? "gui.sparkwitch.skill.pill.short.ready" : "gui.sparkwitch.skill.ready"));
        labels.put(InventoryInfoCard.Kind.ACTIVE, Text.translatable(compact ? "gui.sparkwitch.skill.pill.short.active" : "gui.sparkwitch.skill.pill.active", widestSeconds));
        labels.put(InventoryInfoCard.Kind.COOLDOWN, Text.translatable(compact ? "gui.sparkwitch.skill.pill.short.cooldown" : "gui.sparkwitch.skill.pill.cooldown", widestSeconds));
        labels.put(InventoryInfoCard.Kind.LOCKED, Text.translatable("gui.sparkwitch.skill.pill.locked", tasks, tasks));
        labels.put(InventoryInfoCard.Kind.NO_MANA, Text.translatable(compact ? "gui.sparkwitch.skill.pill.short.no_mana" : "gui.sparkwitch.skill.pill.no_mana"));
        return labels;
    }

    private static int secs(int ticks) { return (int) Math.ceil(ticks / 20.0); }
}
