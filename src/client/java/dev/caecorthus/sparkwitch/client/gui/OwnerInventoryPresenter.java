package dev.caecorthus.sparkwitch.client.gui;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.client.grandwitch.GrandWitchClientPresentation;
import dev.caecorthus.sparkwitch.client.text.WitchSkillClientTexts;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.mana.WitchManaRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.caecorthus.sparkwitch.skill.WitchSkillHudRules;
import dev.caecorthus.sparkwitch.skill.WitchSkillPresentationRules;
import dev.doctor4t.wathe.api.Role;
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
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class OwnerInventoryPresenter {
    private record Frame(InventoryRenderScope.Binding binding, InventoryInfoCard.Snapshot card, boolean ownsTraits) {}
    /** One visited trait as Traits handed it over (not yet copied). 从天赋模组收到的一项天赋（尚未复制）。 */
    private record VisitedTrait(Text name, List<Text> tooltip) {}
    private static final InventoryRenderScope<Frame> CALLS = new InventoryRenderScope<>();
    private static final TraitsInventoryBridge TRAITS = new TraitsInventoryBridge(
            OwnerInventoryPresenter::traitsFacade, PlayerEntity.class, OwnerInventoryPresenter::ownsTraits);
    private static final SkillProgressTracker PROGRESS = new SkillProgressTracker();
    /** The Ceremonial Sword's kill cooldown, a Grand Witch timer beside the panel skill. 仪礼剑击杀冷却（大魔女专属计时）。 */
    private static final SkillProgressTracker SWORD_PROGRESS = new SkillProgressTracker();
    private static final Identifier SWORD = GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID;
    private static long clientTicks;
    // Connection-scoped failure latches (cleared by reset): a failing card stops drawing and logs once instead of
    // throwing, logging and half-drawing every frame. 按连接的失败锁存（reset 清除）：失败后停止绘制且只记录一次日志。
    private static boolean cardFailed;
    private static boolean prepareWarned;
    // The screen whose card drew the mana tail in its last frame; the mana HUD row hides for it. 上一帧显示魔力尾注的界面。
    private static Screen manaTailScreen;
    // The screen whose card laid out every hero row of the skill section in its last frame; the skill HUD hides for it.
    // 上一帧卡片完整排出技能分节全部技能行的界面；此时技能 HUD 隐藏。
    private static Screen skillSectionScreen;
    // Presentation caches (spec-v2 §4): the skill section is rebuilt only when its SkillKey changes, the traits section
    // only when the visited traits differ from the cached copies. Gauges stay live through InventoryInfoCard.Gauge.
    // 展示缓存：技能分节仅在键变化时重建，天赋分节仅在访问结果与缓存副本不同时重建；进度条通过 Gauge 保持实时。
    private static @Nullable SkillKey skillKey;
    private static @Nullable InventoryInfoCard.Section skillCache;
    private static @Nullable InventoryInfoCard.Section traitsCache;
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
            // The visit runs every frame (it is the V1 ownership handshake); only the copies and the section are cached.
            // 每帧都要访问（V1 所有权握手的一部分），仅缓存副本与分节。
            var traits = TRAITS.collect(player, (tag, tooltip) -> new VisitedTrait((Text) tag, tooltip.stream().map(t -> (Text) t).toList()));
            traits.ifPresent(entries -> {
                if (!entries.isEmpty()) sections.add(traitsSection(entries));
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

    /**
     * The traits section, rebuilt only when the visited traits differ from the cached detached copies (Text equality
     * is structural, so a fresh visit of the same traits matches). 天赋分节：仅当访问结果与缓存副本（按结构比较）不同时重建。
     */
    private static InventoryInfoCard.Section traitsSection(List<VisitedTrait> visited) {
        InventoryInfoCard.Section cached = traitsCache;
        if (cached != null && sameTraits(visited, cached.entries())) return cached;
        List<InventoryInfoCard.Entry> entries = visited.stream()
                .map(trait -> new InventoryInfoCard.Entry(List.of(trait.name()), trait.tooltip())).toList();
        return traitsCache = new InventoryInfoCard.Section(Text.translatable("gui.sparktraits.traits"), entries);
    }

    private static boolean sameTraits(List<VisitedTrait> visited, List<InventoryInfoCard.Entry> cached) {
        if (visited.size() != cached.size()) return false;
        for (int i = 0; i < visited.size(); i++) {
            var entry = cached.get(i);
            if (entry.lines().size() != 1 || !entry.lines().getFirst().equals(visited.get(i).name())
                    || !entry.tooltip().equals(visited.get(i).tooltip())) return false;
        }
        return true;
    }

    public static void draw(Screen screen, PlayerEntity player, TextRenderer font, DrawContext context, int mouseX, int mouseY) {
        manaTailScreen = null;
        skillSectionScreen = null;
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
            if (showsSkillSection(card)) skillSectionScreen = screen;
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

    /**
     * Skill HUD hook: true while {@code screen}'s owner card laid out every hero row of the skill section in its last
     * frame. The card then shows the same states (the Grand Witch's factor, recruit, kill and dash; a panel skill's
     * state), so WitchSkillHudRenderer skips its bottom-right lines, which would otherwise draw under the card and poke
     * out around it. Only a panel-gated section has hero rows, so no other role's HUD is affected.
     * 技能 HUD 钩子：该界面的卡片上一帧完整排出了技能分节的所有技能行时为真。卡片已显示相同状态（大魔女的因子、招募、击杀与冲刺；
     * 面板技能状态），因此右下角 HUD 不再绘制，避免压在卡片下方并从四周露出。只有通过面板资格的分节才有技能行，其他职业的 HUD 不受影响。
     */
    public static boolean showsSkillSection(Screen screen) { return screen != null && screen == skillSectionScreen; }

    /** Every hero of the first (skill) section has its own laid-out row: none overflowed or compacted. 技能行全部排出。 */
    private static boolean showsSkillSection(InventoryInfoCard.Snapshot card) {
        var box = card.layout().bounds();
        if (box.width() < 6 || box.height() < 6 || card.sections().isEmpty()) return false;
        var entries = card.sections().getFirst().entries();
        long heroes = entries.stream().filter(entry -> entry.status() != null).count();
        long drawn = card.layout().rows().stream().filter(row -> row.section() == 0 && !row.heading() && row.omitted() == 0
                && row.index() >= 0 && row.index() < entries.size() && entries.get(row.index()).status() != null).count();
        return heroes > 0 && drawn == heroes;
    }

    public static void reset() {
        CALLS.invalidate(); TRAITS.reset(); PROGRESS.reset(); SWORD_PROGRESS.reset();
        cardFailed = false; prepareWarned = false; manaTailScreen = null; skillSectionScreen = null;
        skillKey = null; skillCache = null; traitsCache = null;
        InventoryInfoCard.invalidateCaches();
    }

    /**
     * END_CLIENT_TICK hook (confirmed server only): records the local owner's panel-skill countdowns every tick,
     * also while the inventory is closed, so the gauge knows each phase's total when the card opens mid-phase.
     * Eligibility is the same three-role panel rule; any other role only clears the tracker. The Grand Witch's sword
     * kill cooldown is recorded only after that gate too.
     * 客户端每刻记录本地玩家面板技能的倒计时（背包关闭时也记录），中途打开背包时进度条仍知道阶段总量；
     * 资格与面板相同（仅三个魔女技能职业），其他职业只会清空记录；大魔女的仪礼剑击杀冷却同样只在通过该资格后记录。
     */
    public static void tick(MinecraftClient client) {
        // Drop a closed screen so it is not retained. 界面关闭后释放引用。
        if (manaTailScreen != null && client.currentScreen != manaTailScreen) manaTailScreen = null;
        if (skillSectionScreen != null && client.currentScreen != skillSectionScreen) skillSectionScreen = null;
        if (client.isPaused()) return;
        clientTicks++;
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || player.getWorld() != client.world) { PROGRESS.reset(); SWORD_PROGRESS.reset(); return; }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        Identifier skillId = component.getActiveSkillId();
        var role = GameWorldComponent.KEY.get(client.world).getRole(player);
        if (skillId == null || !WitchSkillPresentationRules.shouldShowInventorySkillPanel(role, skillId)) {
            PROGRESS.reset();
            SWORD_PROGRESS.reset();
            return;
        }
        PROGRESS.observe(skillId, true, component.getActiveSkillWindowTicks(), SkillProgressTracker.candidates(skillId, true), clientTicks);
        PROGRESS.observe(skillId, false, component.getCooldownTicks(), SkillProgressTracker.candidates(skillId, false), clientTicks);
        if (GrandWitchClientPresentation.isGrandWitch(player)) {
            SWORD_PROGRESS.observe(SWORD, false, GrandWitchClientPresentation.swordKillTicks(player),
                    SkillProgressTracker.candidates(SWORD, false), clientTicks);
        } else SWORD_PROGRESS.reset();
    }

    /**
     * Everything the skill section's texts depend on, read once per frame; the section is rebuilt only when this
     * changes (seconds, not ticks, so at most once per displayed second). The gauges are not part of it: they read
     * their live value every frame through {@link InventoryInfoCard.Gauge}.
     * 技能分节文本所依赖的全部输入，每帧读取一次；仅在其变化时重建（按显示秒数而非刻数，至多每秒一次）。进度条不在其中，每帧实时读取。
     */
    private record SkillKey(Language language, @Nullable Role role, Identifier skillId, boolean manaShown, int mana,
                            int activeSeconds, int cooldownSeconds, int swordTasks, int charges, Text abilityKey,
                            @Nullable GrandWitchRows grandWitch) {}

    /**
     * The Grand Witch's own rows beside the panel skill (Witch Factor), read from GrandWitchClientPresentation's data
     * accessors: unlock progress, recruit quota, sword kill and dash seconds, the second skill key.
     * 大魔女在面板主技能（魔女因子）之外的自有能力数据：解锁进度、招募名额、仪礼剑击杀与冲刺秒数、第二技能键。
     */
    record GrandWitchRows(boolean unlocked, int tasks, int remaining, int capacity, int killSeconds, int dashSeconds,
                          Text secondaryKey) {
        static GrandWitchRows read(ClientPlayerEntity player) {
            WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
            var quota = GrandWitchClientPresentation.recruitQuota(player);
            return new GrandWitchRows(component.hasUnlockedGrandWitchCeremonialSword(),
                    GrandWitchRules.clampCeremonialSwordTaskProgress(component.getGrandWitchCeremonialSwordTasks()),
                    quota.remaining(), quota.capacity(), secs(GrandWitchClientPresentation.swordKillTicks(player)),
                    secs(GrandWitchClientPresentation.swordDashTicks(player)), SecondaryAbilityController.secondaryKeyText());
        }
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
        // Grand Witch rows (Recruit, Ceremonial Sword) are read only after the three-role gate above passed; the
        // mixin never checks roles. 大魔女的招募与仪礼剑行只在上方三职业资格通过后读取；混入类从不判断职业。
        GrandWitchRows grandWitch = GrandWitchClientPresentation.isGrandWitch(client.player) ? GrandWitchRows.read(client.player) : null;
        SkillKey key = new SkillKey(Language.getInstance(), role, skillId,
                component.hasManaSystem() && WitchManaRules.isManaRole(role), component.getMana(),
                secs(component.getActiveSkillWindowTicks()), secs(component.getCooldownTicks()),
                component.getGrandWitchCeremonialSwordTasks(), component.getDeathRayCharges(), SparkWitchClient.abilityKeyText(), grandWitch);
        InventoryInfoCard.Section cached = skillCache;
        if (cached != null && key.equals(skillKey)) return cached;
        cached = buildSkillSection(component, role, skillId, key.abilityKey(), grandWitch);
        skillKey = key;
        return skillCache = cached;
    }

    private static InventoryInfoCard.Section buildSkillSection(WitchPlayerComponent component, @Nullable Role role, Identifier skillId,
                                                               Text abilityKey, @Nullable GrandWitchRows grandWitch) {
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
        float progress = switch (kind) {
            case ACTIVE -> liveProgress(skillId, true);
            case COOLDOWN -> liveProgress(skillId, false);
            case NO_MANA -> cost <= 0 ? 1f : Math.max(0f, Math.min(1f, mana / (float) cost));
            case LOCKED -> tasks / (float) GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS;
            case READY -> 1f;
        };
        // ACTIVE drains and COOLDOWN fills between the 1 s syncs, so their gauges read the tracker every frame.
        // 生效与冷却的进度条在两次同步之间平滑变化，因此每帧读取记录器。
        InventoryInfoCard.Gauge gauge = switch (kind) {
            case ACTIVE -> () -> liveProgress(skillId, true);
            case COOLDOWN -> () -> liveProgress(skillId, false);
            default -> null;
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
        // Appended facts after the status line: the mana line in NO_MANA, the charges line while charges >= 0.
        // 状态行之后的附加行：魔力不足时的魔力行、有发数时的发数行。
        int facts = (kind == InventoryInfoCard.Kind.NO_MANA ? 1 : 0) + (charges >= 0 ? 1 : 0);
        var status = new InventoryInfoCard.Status(kind, label, shortLabel(kind, label), reserveLabels(widest, false),
                reserveLabels(widest, true), progress, pips, kind == InventoryInfoCard.Kind.LOCKED ? tasks : 0, charges,
                costText, costShort, gauge, facts);
        List<Text> tooltip = new ArrayList<>(WitchSkillClientTexts.tooltip(skillId, cooldownTicks,
                activeTicks, component.getGrandWitchCeremonialSwordTasks()));
        // The shared tooltip already ends with the same active/locked/cooldown/ready status: recolour that line by
        // its own precedence instead of adding the pill label again. The mana and charge lines are extra facts.
        // 共享提示末行已是同一状态：按其自身优先级着色，不重复添加状态；魔力与发数为附加信息行。
        // In NO_MANA the shared "ready" line only means "off cooldown": it says so ("冷却完毕", not "可使用", which beside
        // the red mana line read "usable, needs 50") and is MUTED, not READY green; the red mana line alone alerts.
        // 魔力不足时该行仅表示“不在冷却”：文本改为“冷却完毕”（“可使用”与红色魔力行并列会被读成“可用但需要 50”），颜色为 MUTED
        // 而非绿色，由红色魔力行单独提示。
        if (!tooltip.isEmpty()) {
            int last = tooltip.size() - 1;
            int statusColor = kind == InventoryInfoCard.Kind.NO_MANA ? InventoryCardPaint.MUTED : InventoryCardPaint.kindText(tooltipKind);
            Text statusText = kind == InventoryInfoCard.Kind.NO_MANA ? Text.translatable("gui.sparkwitch.skill.off_cooldown") : tooltip.get(last);
            tooltip.set(last, statusText.copy().withColor(statusColor & 0xFFFFFF));
            // The bound key goes just above the status line, so the status stays the line after the +2 gap.
            // 实际绑定按键位于状态行正上方，状态行仍紧随 2 像素间距。
            tooltip.add(last, keyHint("gui.sparkwitch.skill.key.main", abilityKey));
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
        if (grandWitch != null) entries.addAll(grandWitchEntries(grandWitch));
        return new InventoryInfoCard.Section(Text.translatable("gui.sparkwitch.skills"), entries, manaTail,
                manaTail == null ? null : Text.literal("888 \uE782"), Text.translatable("gui.sparkwitch.skills.short"));
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
    static Map<InventoryInfoCard.Kind, Text> reserveLabels(int widestSeconds, boolean compact) {
        int tasks = GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS;
        Map<InventoryInfoCard.Kind, Text> labels = new EnumMap<>(InventoryInfoCard.Kind.class);
        labels.put(InventoryInfoCard.Kind.READY, Text.translatable(compact ? "gui.sparkwitch.skill.pill.short.ready" : "gui.sparkwitch.skill.ready"));
        labels.put(InventoryInfoCard.Kind.ACTIVE, Text.translatable(compact ? "gui.sparkwitch.skill.pill.short.active" : "gui.sparkwitch.skill.pill.active", widestSeconds));
        labels.put(InventoryInfoCard.Kind.COOLDOWN, Text.translatable(compact ? "gui.sparkwitch.skill.pill.short.cooldown" : "gui.sparkwitch.skill.pill.cooldown", widestSeconds));
        labels.put(InventoryInfoCard.Kind.LOCKED, Text.translatable("gui.sparkwitch.skill.pill.locked", tasks, tasks));
        labels.put(InventoryInfoCard.Kind.NO_MANA, Text.translatable(compact ? "gui.sparkwitch.skill.pill.short.no_mana" : "gui.sparkwitch.skill.pill.no_mana"));
        return labels;
    }

    /**
     * Grand Witch's Recruit Accomplice and Ceremonial Sword (spec-v2-grand-witch §1): her own abilities beside the
     * panel skill, as status-bearing hero rows sharing the section's status column. Built only for a Grand Witch that
     * passed the three-role panel gate in skillSection. Recruit: LOCKED "未解锁" (short "锁定") before 2 tasks (the task
     * pips live on the sword row only), then READY "剩余 n/m", LOCKED with the same label once the quota is used up, or
     * LOCKED "无名额" when the round has no quota at all. Sword: LOCKED "x/2" with task pips, then READY "可击杀" or
     * COOLDOWN on the 30 s kill cooldown; the 5 s dash cooldown is a tooltip fact, not a second pill.
     * 大魔女的招募同伙与仪礼剑：面板主技能之外的自有能力，作为带状态的技能行，与分节共用状态列；仅在 skillSection 的三职业资格通过后构建。
     * 招募：2 个任务前为“未解锁”（短标签“锁定”，任务点只画在仪礼剑行），之后为“剩余 n/m”，名额用尽时以同一文本显示为锁定，
     * 本局无名额时显示为锁定的“无名额”。仪礼剑：锁定时显示 x/2 与任务点，解锁后为“可击杀”或 30 秒击杀冷却；5 秒冲刺冷却只写在提示中，不另设状态牌。
     */
    private static List<InventoryInfoCard.Entry> grandWitchEntries(GrandWitchRows rows) {
        return List.of(recruitEntry(rows), swordEntry(rows));
    }

    /**
     * Recruit row. Short labels stay within the short column the factor and sword already need (34 px zh: "20秒" /
     * "30秒"), so the Grand Witch section keeps step A' down to a 98 px card (427 wide): LOCKED is "锁定" there, not
     * "未解锁" (40 px), and a round without quota (under 24 participants) reads "无名额" / "无", never "0/0". The LOCKED
     * reserves are the unlock labels; the no-quota labels are never wider (checked for zh and en by the W test), and
     * an exhausted quota "0/m" is as wide as the READY "m/m" reserve (same 5 px icon, digits all 6 px).
     * 招募行。短标签不超过因子与仪礼剑已需要的短列（中文 34 像素），使大魔女分节在 98 像素宽的卡片（427 宽屏幕）上仍为 A′：
     * 锁定时短标签为“锁定”而非“未解锁”（40 像素）；无名额的对局（参赛不足 24 人）显示“无名额”/“无”，而不是“0/0”。
     * 锁定状态按解锁标签预留，无名额标签不会更宽（W 测试检查中英文）；名额用尽的“0/m”与 READY 预留“m/m”等宽。
     */
    static InventoryInfoCard.Entry recruitEntry(GrandWitchRows rows) {
        boolean none = rows.unlocked() && rows.capacity() <= 0;
        boolean ready = rows.unlocked() && rows.remaining() > 0;
        var kind = ready ? InventoryInfoCard.Kind.READY : InventoryInfoCard.Kind.LOCKED;
        Text label = !rows.unlocked() ? Text.translatable("gui.sparkwitch.skill.pill.unlock")
                : none ? Text.translatable("gui.sparkwitch.skill.pill.recruit_none")
                : Text.translatable("gui.sparkwitch.skill.pill.recruit", rows.remaining(), rows.capacity());
        Text shortLabel = !rows.unlocked() ? Text.translatable("gui.sparkwitch.skill.pill.short.unlock")
                : none ? Text.translatable("gui.sparkwitch.skill.pill.short.recruit_none")
                : Text.translatable("gui.sparkwitch.skill.pill.short.recruit", rows.remaining(), rows.capacity());
        // The quota total is fixed for the round, so "n/m" is reserved at "m/m". 名额总数本局固定，按“m/m”预留。
        Map<InventoryInfoCard.Kind, Text> widest = new EnumMap<>(InventoryInfoCard.Kind.class);
        widest.put(InventoryInfoCard.Kind.READY, Text.translatable("gui.sparkwitch.skill.pill.recruit", rows.capacity(), rows.capacity()));
        widest.put(InventoryInfoCard.Kind.LOCKED, Text.translatable("gui.sparkwitch.skill.pill.unlock"));
        Map<InventoryInfoCard.Kind, Text> widestShort = new EnumMap<>(InventoryInfoCard.Kind.class);
        widestShort.put(InventoryInfoCard.Kind.READY, Text.translatable("gui.sparkwitch.skill.pill.short.recruit", rows.capacity(), rows.capacity()));
        widestShort.put(InventoryInfoCard.Kind.LOCKED, Text.translatable("gui.sparkwitch.skill.pill.short.unlock"));
        var status = new InventoryInfoCard.Status(kind, label, shortLabel, widest, widestShort, ready ? 1f : 0f,
                0, 0, -1, null, false, null, 0);
        Text statusLine = !rows.unlocked()
                ? Text.translatable("gui.sparkwitch.skill.ceremonial_sword.locked", rows.tasks(), GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS)
                : none ? Text.translatable("gui.sparkwitch.skill.recruit.none")
                : Text.translatable("gui.sparkwitch.skill.recruit.quota", rows.remaining(), rows.capacity());
        List<Text> tooltip = List.of(
                Text.translatable("skill.sparkwitch.recruit_accomplice.name"),
                Text.translatable("skill.sparkwitch.recruit_accomplice.description"),
                Text.translatable("skill.sparkwitch.recruit_accomplice.inventory"),
                keyHint("gui.sparkwitch.skill.key.secondary", rows.secondaryKey()),
                statusLine.copy().withColor(InventoryCardPaint.kindText(kind) & 0xFFFFFF));
        return new InventoryInfoCard.Entry(List.of(Text.translatable("skill.sparkwitch.recruit_accomplice.name"), label), tooltip,
                GrandWitchClientPresentation.COLOR, status);
    }

    static InventoryInfoCard.Entry swordEntry(GrandWitchRows rows) {
        int unlock = GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS;
        var kind = !rows.unlocked() ? InventoryInfoCard.Kind.LOCKED
                : rows.killSeconds() > 0 ? InventoryInfoCard.Kind.COOLDOWN : InventoryInfoCard.Kind.READY;
        Text label = switch (kind) {
            case LOCKED -> Text.translatable("gui.sparkwitch.skill.pill.locked", rows.tasks(), unlock);
            case COOLDOWN -> Text.translatable("gui.sparkwitch.skill.pill.cooldown", rows.killSeconds());
            default -> Text.translatable("gui.sparkwitch.skill.pill.kill_ready");
        };
        Text shortLabel = switch (kind) {
            case LOCKED -> label;
            case COOLDOWN -> Text.translatable("gui.sparkwitch.skill.pill.short.cooldown", rows.killSeconds());
            default -> Text.translatable("gui.sparkwitch.skill.pill.short.ready");
        };
        int widestSeconds = SkillProgressTracker.widestSeconds(SWORD);
        Map<InventoryInfoCard.Kind, Text> widest = new EnumMap<>(InventoryInfoCard.Kind.class);
        widest.put(InventoryInfoCard.Kind.READY, Text.translatable("gui.sparkwitch.skill.pill.kill_ready"));
        widest.put(InventoryInfoCard.Kind.COOLDOWN, Text.translatable("gui.sparkwitch.skill.pill.cooldown", widestSeconds));
        widest.put(InventoryInfoCard.Kind.LOCKED, Text.translatable("gui.sparkwitch.skill.pill.locked", unlock, unlock));
        Map<InventoryInfoCard.Kind, Text> widestShort = new EnumMap<>(InventoryInfoCard.Kind.class);
        widestShort.put(InventoryInfoCard.Kind.READY, Text.translatable("gui.sparkwitch.skill.pill.short.ready"));
        widestShort.put(InventoryInfoCard.Kind.COOLDOWN, Text.translatable("gui.sparkwitch.skill.pill.short.cooldown", widestSeconds));
        widestShort.put(InventoryInfoCard.Kind.LOCKED, Text.translatable("gui.sparkwitch.skill.pill.locked", unlock, unlock));
        boolean cooling = kind == InventoryInfoCard.Kind.COOLDOWN;
        float progress = cooling ? swordProgress() : kind == InventoryInfoCard.Kind.LOCKED ? rows.tasks() / (float) unlock : 1f;
        InventoryInfoCard.Gauge gauge = cooling ? OwnerInventoryPresenter::swordProgress : null;
        List<Text> tooltip = new ArrayList<>(List.of(
                Text.translatable("skill.sparkwitch.ceremonial_sword.name"),
                Text.translatable("skill.sparkwitch.ceremonial_sword.description"),
                Text.translatable("skill.sparkwitch.ceremonial_sword.protection")));
        Text killLine = switch (kind) {
            case LOCKED -> Text.translatable("gui.sparkwitch.skill.ceremonial_sword.locked", rows.tasks(), unlock);
            case COOLDOWN -> Text.translatable("gui.sparkwitch.skill.sword.kill_cooldown", rows.killSeconds());
            default -> Text.translatable("gui.sparkwitch.skill.sword.kill_ready");
        };
        tooltip.add(killLine.copy().withColor(InventoryCardPaint.kindText(kind) & 0xFFFFFF));
        // The dash has its own 5 s item cooldown: one fact line after the kill status, never a second pill.
        // 冲刺有独立的 5 秒物品冷却：作为击杀状态之后的一行附加信息，而不是第二个状态牌。
        if (rows.unlocked()) {
            Text dashLine = rows.dashSeconds() > 0
                    ? Text.translatable("gui.sparkwitch.skill.sword.dash_cooldown", rows.dashSeconds()).withColor(InventoryCardPaint.COOL_TEXT & 0xFFFFFF)
                    : Text.translatable("gui.sparkwitch.skill.sword.dash_ready").withColor(InventoryCardPaint.READY_TEXT & 0xFFFFFF);
            tooltip.add(dashLine);
        }
        var status = new InventoryInfoCard.Status(kind, label, shortLabel, widest, widestShort, progress,
                kind == InventoryInfoCard.Kind.LOCKED ? unlock : 0, kind == InventoryInfoCard.Kind.LOCKED ? rows.tasks() : 0,
                -1, null, false, gauge, rows.unlocked() ? 1 : 0);
        return new InventoryInfoCard.Entry(List.of(Text.translatable("skill.sparkwitch.ceremonial_sword.name"), label), tooltip,
                GrandWitchClientPresentation.COLOR, status);
    }

    /** "主技能键：G" with the bound key in brass (the key text is the binding's own localized name). 按键提示行。 */
    private static Text keyHint(String key, Text boundKey) {
        return Text.translatable(key, boundKey.copy().withColor(InventoryCardPaint.BRASS_HI & 0xFFFFFF));
    }

    /** Live fill of the panel skill's ACTIVE (drains) or COOLDOWN (fills) gauge; NaN without a player. 面板技能实时进度。 */
    private static float liveProgress(Identifier skillId, boolean active) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return Float.NaN;
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(client.player);
        int ticks = active ? component.getActiveSkillWindowTicks() : component.getCooldownTicks();
        return PROGRESS.progress(skillId, active, ticks, clientTicks, client.getRenderTickCounter().getTickDelta(true));
    }

    /** Live fill of the sword's kill-cooldown gauge (1 - remaining / 600); NaN without a player. 仪礼剑击杀冷却实时进度。 */
    private static float swordProgress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return Float.NaN;
        return SWORD_PROGRESS.progress(SWORD, false, GrandWitchClientPresentation.swordKillTicks(client.player), clientTicks,
                client.getRenderTickCounter().getTickDelta(true));
    }

    private static int secs(int ticks) { return (int) Math.ceil(ticks / 20.0); }
}
