# SparkWitch 代码书写规范

> 本文不是通用 Java 风格指南，而是**从本仓库现有代码反推出来的、与它自洽的规范**。
>
> 证据来源：`main` 分支 `0aa48193` 全量扫描（1,813 个 `.java` / 175,437 行）+ 分层抽样精读 + `build.gradle` 中的机器校验逻辑 + `CONTEXT.md`。
>
> 规则分三级，请按级别对待：
>
> - **【硬】** 机器或 CI 会查，违反即出错。
> - **【主流】** 70% 以上代码如此，新代码应照做；偏离要在注释里说明理由。
> - **【禁止】** 全仓库零出现，不要开这个头。

---

## 0. 一句话概括

> **服务端权威；`Rules` 只做纯判定，`Service` 承担副作用，`Runtime` 管逐 tick 状态机；类默认 `final`；一切行为从 `register()` 进入；注释英文写一遍再用中文写一遍。**

SparkWitch 是一个「多职业社交推理玩法」Fabric 模组，代码的组织方式服务于一个目标：**把每个职业做成一个可独立审查的纵向切片，把每一处状态写入限制在服务端**。下面所有细则都是这个目标的副产品。

---

## 1. 工程事实与强制校验

| 项目 | 值 |
|---|---|
| 语言 / 平台 | Java 21，Minecraft 1.21.1，Fabric |
| 构建 | Gradle，`build.gradle`（1,523 行，含自定义校验） |
| 代码规模 | 1,813 个 Java 文件，175,437 行（main 1,246 / client 567） |
| 行构成 | 代码 98,409（56%）、注释 61,264（35%）、空行 15,764（9%） |
| 单元测试 | `src/test/` **被 .gitignore**，只在本机存在；CI 只有编译与架构校验 |

**【硬】提交前必须通过**（见 `CONTEXT.md` → Verification）：

```sh
./gradlew --no-daemon --no-watch-fs --console=plain --no-parallel --max-workers=1 \
    test verifyArchitecture compileJava compileClientJava
git diff --check        # 空白字符检查，CI 也跑
```

改动 client mixin 时追加 `verifyClientMixinSelectors`（`build`、`check`、`verifyArchitecture` 都不会带上它）。

这些任务实际守的是什么，写代码时就该知道：

- `verifyArchitecture`：必需文档/源码存在；**禁止引用 SparkTraits 内部实现包**（`dev.caecorthus.sparktraits.impl`、`.component`）；**实体渲染协议**——除 `EntityRedraw.java` 外不得出现 `getRenderer(...).render(`、`EntityRenderDispatcher#render`，`worldRenderer.render(` 只能出现在 `SecondaryWorldPass.run(` 之内。
- `verifySoundAssets`：每个 `.ogg` 必须恰好一条 Vorbis 流（首条流不是 Vorbis 会静音且不报错）。
- `verifyClientMixinSelectors`：mixin 选择器与 `@At` 目标必须与锁定版 Wathe/NoellesRoles 实际产物一致，**不允许把注入弱化成 `require = 0`**。
- CI 的 `git diff --check`：禁止行尾空白、混用换行。

---

## 2. 排版：4 空格、120 列、运算符行首

### 2.1 缩进与宽度

| 规则 | 级别 | 实测 |
|---|---|---|
| 4 空格缩进 | 【硬】 | Tab 缩进 0 行 |
| 不用 Tab | 【硬】 | 同上 |
| 行宽软上限 120 | 【主流】 | p50=42、p90=101、p99=120；>120 仅 1,283 行（0.7%） |
| 无行尾空白 | 【硬】 | 0 行；CI 强制 |

超 120 列基本只出现在长字符串、长注解签名（`@Inject(method = "Lnet/...;")`）这类无法拆分的地方。

### 2.2 折行：操作符和点号放行首

这是全仓库最一致的一条，**1,992 处断行 `&&`/`||` 全部在行首，行尾 0 处**；链式调用的 `.` 同理（814 处）。

```java
public boolean hasRoundState() {
    return matchId != null || echoTaskOrdinal != NO_TASK || echoDeadlineTick != 0L
            || heardUntilTick != 0L || tollReady;
}
```

```java
boolean realSanity = baseMood != null
        && baseMood != Role.MoodType.NONE
        && FactionIds.CIVILIAN.equals(effectiveFaction);
return realSanity && affectAllowed ? Audience.AFFECTED : Audience.HEARER;
```

三元运算符同理，`?` 和 `:` 放行首：

```java
ClientPlayerEntity player = MinecraftClient.getInstance().player;
return forwardsPerception(player != null && BlackRavenClientState.isPerceptionActive(player))
        ? player : focused;
```

### 2.3 折行的参数列表：续行 +8，右括号独占一行

方法**声明**和**调用**用同一套：参数各占一行、缩进 +8、`)` 或 `) {` 回到基准缩进。

```java
public static Audience classify(
        boolean participant,
        @Nullable Role.MoodType baseMood,
        @Nullable Identifier effectiveFaction,
        boolean affectAllowed
) {
```

```java
PayloadTypeRegistry.playC2S().register(
        UseOrthopedistSkillC2SPacket.ID,
        UseOrthopedistSkillC2SPacket.CODEC
);
```

`implements` 子句过长时同样 +8 换行：

```java
public final class BellEchoPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
```

### 2.4 空行

- `package` → 空行 → imports → 空行 → 类 Javadoc。imports 内部**允许**用空行分组（462 个文件这么做，通常是 `java.*` 与其余分组隔开），但不强制；同一分组内不空行。
- 常量块与实例字段之间、最后一个字段与构造器之间、构造器与第一个方法之间、方法与方法之间：各 1 个空行。
- 关联紧密的两三行（如先取 `GameWorldComponent` 再取 role）**不加**空行。

---

## 3. 命名：后缀即架构

### 3.1 类型后缀（最重要的约定）

SparkWitch 用**类名后缀**表达这一层「能不能有副作用」，读名字就能判断该不该在里面写 `ItemStack` 修改、发包或 `KillPlayer` 调用。

| 后缀 | 数量 | 语义 | 硬约束 |
|---|---|---|---|
| `*Rules` | 192 | 纯判定与稳定数值 | 2,499 个静态方法；192 个里 163 个只有静态成员，其余把嵌套值类型（`Result`/`Budget`）或客户端 HUD 的小状态机放在同一文件，仍然不碰世界状态 |
| `*Service` | 201 | 编排副作用 | 201/201 都有 `private` 无参构造器；守卫链 + 提前 `return fail(...)` |
| `*Runtime` | 17 | 逐 tick 状态机 | 由组件 `serverTick()` 驱动 |
| `*Targeting` | 19 | 候选筛选与评分 | 纯计算，返回 `@Nullable` 目标 |
| `*FeatureService` | 34 | 该职业**唯一**的事件注册入口 | `register()` + 私有构造器 |
| `*Component` | 45 | CCA 组件（持久化 + 同步） | 实现 `AutoSyncedComponent` 等 |
| `*NbtCodec` / `*SyncCodec` | 6 | 把组件的 NBT/同步逻辑拆出去 | 同包，字段 package-private |
| `*C2SPayload` / `*S2CPacket` | 62 | 网络包 | `record` + `CODEC` + `ID` |
| `*Entity` / `*Item` | 15 / 48 | 游戏对象 | 继承原版基类 |
| `*Screen` / `*Renderer` | — | 客户端 UI | client 源集 |
| `*State` / `*Tracker` / `*Marker` / `*Match` | — | 小而专的状态载体 | 多为 package-private |
| 其他纯函数收纳 | — | `*Math`、`*Format` 之类，见名知义 | — |

一个职业的典型切片（以敲钟人为例）：

```
roles/killer/bellringer/
  BellRingerRules.java          纯判定 + 常量
  BellRingerEchoService.java    施放回响（改时间、发钟声、派任务）
  BellRingerEchoRuntime.java    每 tick 结算超时、提示过期、发光标记
  BellRingerEchoTargeting.java  受众分类
  BellRingerFeatureService.java 唯一的事件注册点
  BellEchoPlayerComponent.java  每玩家状态（仅拥有者可见）
  BellRingerMatch.java          当前对局 id
  BellRingerLoadoutService.java 开局发放与回收
  BellRingerShopService.java    商店条目
```

### 3.2 方法与字段

```java
// 动词开头，能读出「做什么」
public static boolean canAffordEcho(int remainingTicks)
public static boolean isDeadlinePassed(long now, long deadline)
static void dropEchoSilently(ServerPlayerEntity player, BellEchoPlayerComponent component)

// 布尔量：is / has / can / should / may（1,005 处带前缀，另有谓词式 register/clear 返回「是否发生变化」）
public boolean hasEchoTask()
public boolean tollReady()

// 变更方法返回「有没有真的变」，调用方据此决定是否同步
public boolean clearEcho() { ... syncOwner(); return true; }
public void setTollReady(boolean ready) {
    if (tollReady == ready) {
        return;          // 无变化：不发包
    }
    ...
}
```

- 常量：`UPPER_SNAKE_CASE`，**用算式而不是算好的数字**表达语义：`ECHO_COOLDOWN_TICKS = 90 * 20`（90 秒 × 20 tick）。
- 字段：`private final` 优先；跨 codec 共享的字段显式写 package-private 并加注释说明为什么放宽。
- `this.x = x;` 在构造器中始终写 `this.`。
- **不用 `var`**（全仓库仅 62 行用到）；**不给参数加 `final`**（仅 16 处）。

### 3.3 包与资源标识

| 对象 | 规则 | 例 |
|---|---|---|
| 包名 | 全小写、单数、无下划线 | `component`、`mixin`、`net`、`skill`、`item`、`roles`、`compat` |
| 角色包 | 注册 id **去掉下划线** | `bell_ringer` → `roles/killer/bellringer`；`control_expert` → `controlexpert`；`black_raven` → `blackraven` |
| 注册 id | `snake_case`（358 处） | `sparkwitch:bell_echo`、`sparkwitch:black_raven` |
| 常量 id 字段 | `<NAME>_ID` | `public static final Identifier BELL_RINGER_TOLL_ID` |
| NBT 键 | **PascalCase**（163 处，压倒性） | `"Match"`、`"EchoTask"`、`"EchoDeadline"` |
| 翻译键 | `<类别>.sparkwitch[.<功能>].<snake.path>` | `message.sparkwitch.bell_echo.failed` |
| 声音 id | 带用途前缀 | `ambient.…`、`skill.…`、`item.usec_rifle.shoot` |

### 3.4 Mixin 专属命名

| 对象 | 规则 |
|---|---|
| 类名 | `<目标类><被改行为>Mixin`（410 个）、`<目标类>Accessor`（15）、`…Invoker`（2） |
| 注入成员 | `sparkwitch$<camelCase>`（978 处），如 `sparkwitch$preferNearerSeekerDevice` |
| `@Unique` 常量 | `SPARKWITCH$<UPPER_SNAKE>`（20 处）或 `SPARKWITCH_<UPPER_SNAKE>` |
| 类的形态 | `public abstract class …Mixin`：407 / 410；另有 1 个 `public final`、2 个包内可见 |
| 包 | `mixin/<角色 slug>/`，与 `roles/<阵营>/<slug>/` 同名；客户端在 `client/mixin/…` |
| 注册 | 必须同步加入 `sparkwitch.mixins.json` 的 `mixins` 数组（客户端同名文件） |

注入类型分布（说明偏好）：`@Inject` 366、`@WrapOperation` 116、`@WrapMethod` 111、`@Shadow` 95、`@Redirect` 48、`@ModifyArg` 10、`@ModifyVariable` 9、`@Overwrite` 8（极少，且需在 Javadoc 里论证为何非它不可）。

---

## 4. 注释：英文写一遍，中文再写一遍

这是本仓库最鲜明的特征：**95% 的类型 Javadoc 含中文**（1,632 / 1,717），1,177 处「英文注释 + 紧邻中文注释」成对出现。

### 4.1 三种形态

**单行**（一行英文，`/`，一行中文，结尾 `。`）：

```java
/** Echo audience buckets. / 回响受众分类。 */
public enum Audience {
```

**多行**（英文段落，空行后中文段落，中文用全角标点）：

```java
/**
 * Per-tick Echo resolution (stale match, marker validity, deadline penalty, hint expiry, toll flag), plus
 * the narrow decisions behind {@code PlayerMoodComponentBellEchoMixin}.
 * 每 tick 的回响结算（过期对局、标记有效性、截止惩罚、提示过期、敲钟标记），以及
 * {@code PlayerMoodComponentBellEchoMixin} 背后的窄决策。
 */
```

**行内**（`//` 永远带一个空格，0 处例外）：

```java
// Hot path: idle players do nothing on 9 of every 10 ticks.
// 热路径：无状态玩家每 10 tick 中有 9 tick 直接返回。
if (!echo && !heard && !tollReady && !scan) {
```

### 4.2 注释只写「为什么」

不解释代码在做什么（代码自己会说），解释**为什么这样做**、**为什么不能那样做**、**谁定的**：

```java
// Owner decision: any mid-round role change drops the Echo (marker and its task) and hint silently,
// no penalty; the task goes too so the new role cannot cash it in as an ordinary task.
// 所有者决定：对局中任何职业变更都静默清除回响（标记及其任务）与提示，不施加惩罚；
// 任务一并移除，避免新职业将其当作普通任务完成领取奖励。
```

写决策时**带上依据**：`owner decision 14`、`owner decision 2026-10-07 D7`、`owner rule`、`(D9)`。全仓库 101 个文件、约 280 处这样引用。

### 4.3 【禁止】不做的事

| 禁止 | 实测 |
|---|---|
| `TODO` / `FIXME` / `XXX` / `HACK` 标记 | **0 处**。未完成的工作写在 `docs/plans/*.md`（gitignored）与 issue 里，不写进代码 |
| 行尾注释 | 1 处（历史遗留）；注释一律独立成行 |
| 行内 `/* */` 块注释 | 0 处 |
| 中文出现在日志里 | 0 处（日志只用英文） |
| `@Deprecated` | 0 处（跨模组接缝用「冻结签名」+ `DOWNSTREAM_MIGRATION_NOTES.md` 管理，不靠注解发布废弃） |

### 4.4 Javadoc 细节

- 大量使用 `{@code ...}`（6,255 处）与 `{@link ...}`（2,756 处）做交叉引用，把「读哪段代码能验证」直接写进去。
- `@param` 209 处，`@return` 仅 4 处——**返回值靠正文散文描述**，不堆标签。
- 列表用 `<ul><li>`，需要强调用 `<b>absent</b>:` 这种小标题（见 `compat/OptionalStaticMethod`）。
- 私有方法只在**意图不明显**时才写注释；`getX()` 这类访问器不写。

### 4.5 日志

| 级别 | 次数 | 用途 |
|---|---|---|
| `warn` | 57 | 可恢复失败、降级（加载失败、源被丢弃） |
| `error` | 12 | 不可恢复 |
| `info` | 10 | 生命周期（初始化、版本握手） |
| `debug` | 2 | 极少 |

```java
SparkWitch.LOGGER.warn("Dropping a Blind see-through outline source that threw", failure);
SparkWitch.LOGGER.warn("Unable to load Black Raven Perception shader", exception);
```

参数化占位符 `{}`，异常作为最后一个参数直接传（不拼 `e.getMessage()`）。

---

## 5. 类型设计：默认 `final`，值用 `record`，分支用箭头

### 5.1 final 是默认值

| 形态 | 数量 |
|---|---|
| `public final class` | 971 |
| 其他 `final class`（含 package-private） | 84 |
| `public class`（非 final、非 abstract） | **28** |

那 28 个例外的共同点：**都是需要被原版/框架继承或替换的游戏对象**——`*Entity`、`*Item`、`*Screen`、`*EntityRenderer`、`*RoundState`（可变态）。

- 【主流】工具、规则、服务、运行时、组件、payload：**全部 `final`**。
- 【主流】只有静态成员的类：`private` 构造器（空体，两行）。

```java
public final class BellRingerRules {
    private BellRingerRules() {
    }
```

### 5.2 record 用于值对象

226 处。规则：组件只有 1–2 个时与类名同行；更多则**每个组件一行**，闭合的 `)` 与 `{` 单独成行；用紧凑构造器做归一化；用静态工厂命名意图。

```java
public record WitchSkillUseResult(
        boolean accepted,
        int cooldownTicks,
        @Nullable String messageKey,
        boolean deferCooldownUntilActiveWindowEnds
) {
    public WitchSkillUseResult {
        cooldownTicks = Math.max(0, cooldownTicks);
    }

    public static WitchSkillUseResult success(int cooldownTicks) { ... }
    public static WitchSkillUseResult fail(String messageKey) { ... }
}
```

`record` 不用来承载可变状态；需要 `setX()` 的，写普通类或 CCA 组件。

### 5.3 可空性：只用 `@Nullable`，且写在类型前

```java
// 字段
private @Nullable UUID matchId;

// 返回值
public @Nullable PlayerMoodComponent.Task echoTask()
static @Nullable UUID currentId()

// 参数
void startEcho(PlayerMoodComponent.Task type, long deadline, @Nullable UUID match)
```

- 462 处「修饰符 + `@Nullable`」，**0 处** `@Nullable private`。原则：**注解属于类型，不属于修饰符**。
- 不标注「非空」这一默认情况：`@NonNull` **0 处**；`@NotNull` 共 84 处，其中 76 处是 CCA `writeToNbt`/`readFromNbt` 的形参（接口签名如此），其余也是覆写接口方法时照抄，不是主动设计。
- **不用 `Optional` 做字段或参数**（`Optional` 106 处，基本都是承接原版返回 `Optional` 的 API，如 `Box.raycast`）。
- 判断空用 `!= null` 早退，不在表达式里链式 `Optional.map`。

### 5.4 switch 只用箭头形式

| 形式 | 次数 |
|---|---|
| `case X ->` | 512 |
| `case X:` | **0** |

```java
switch (BellRingerEchoTargeting.audience(caster, player, game)) {
    case AFFECTED -> {
        if (!component.hasEchoTask()) {
            assignEchoTask(player, component, now, match);
        }
    }
    case HEARER -> component.startHeard(now + BellRingerRules.ECHO_HINT_TICKS, match);
    case NONE -> {
    }
}
```

穷尽 enum 的 switch **不加 `default`**；确实需要空分支时写 `case NONE -> { }`，明确表达「我知道这里什么都不做」。

---

## 6. 分层：依赖只能单向

### 6.1 源集边界

| 方向 | 次数 | 结论 |
|---|---|---|
| `src/main` → `src/client` | **0** | 【硬】服务端代码不得引用任何客户端类 |
| `src/client` → `src/main` | 720 | 允许 |

需要「公共代码调用客户端能力」时，用 `api/` 里的**函数式接口 + install 钩子**（见 `SparkWitchApi.installLastEscapeVisionRenderer`），main 侧只持有接口引用。

### 6.2 一个功能的调用链

```
mixin / 事件 / 网络包          ← 入口，只做一层转换
        ↓
*Service / *Runtime            ← 副作用与状态推进
        ↓
*Rules / *Targeting            ← 纯判定（无副作用，可直接单测）
        ↓
*Component (CCA)               ← 唯一的持久化状态，服务端权威
```

规则：

1. **`Rules` 里不准出现** `world`、`ItemStack` 修改、发包、`KillPlayer`、组件 `KEY.get`。它们接收已经取好的值（`boolean`、`int`、`@Nullable Role`），返回判定结果。
2. **`Service` 里不准写「怎么判定」**。判定一律下沉到 `Rules`，Service 只负责取数据、按判定结果执行、把结果包成 `*Result` 返回。
3. **事件的注册只能出现在 `*FeatureService.register()`** 或 `impl/SparkWitchEvents.register()`——全局聚合器只做一次性编排，业务逻辑放回职业自己的模块。
4. 一个职业的代码只能被自己、`SparkWitchEvents`、`SparkWitchPackets`、`api/` 引用；**跨职业直接调用要写明理由**。

### 6.3 跨模组：`compat/` 适配器

可选依赖一律通过 `compat/` 里的桥接类，用反射访问，**不产生编译期依赖**：

- 统一走 `compat/OptionalStaticMethod`（JDK + Fabric Loader only）：`of(modId, …)` 门控、`ungated(...)`、`returning(Type.class)` 形状校验、`warnOnce(...)` 可选告警。
- 查找结果**锁存**（`volatile` + `AtomicBoolean`），失败也锁存，失败语义由调用方显式选择（fail-open 还是 fail-closed）。
- 单次失败不要每次调用都打日志。
- 冻结的跨模组签名写进 `DOWNSTREAM_MIGRATION_NOTES.md`，不写 `@Deprecated`。

### 6.4 技能键与技能注册（【硬】）

**全仓库只注册一个 `KeyBinding`**（`KeyBindingHelper.registerKeyBinding` 出现 1 次）：SparkWitch 的二技能键 N。一技能**复用 NoellesRoles 的共享技能键**，不新增按键项。

| 技能 | 键 | 注册端 | 服务端入口 |
|---|---|---|---|
| 一技能 | NoellesRoles 共享键（`key.noellesroles.ability`） | **NoellesRoles**：`client/hooks/WitchAbilityKeyBridge` 读 `NoellesrolesClient.abilityBind` | 该职业自己的 `Use*AbilityC2SPacket` → `*Service.use(...)` |
| 二技能 | SparkWitch 键 N（`key.sparkwitch.secondary_skill`） | **SparkWitch**：`SecondaryAbilityRegistry.register(roleId, handler)`；对外接缝 `api/client/SecondarySkillKeyApi.register(...)` | handler 发包 → 服务端 `Rules` 校验 |
| 法力技能 | 一技能同一路径 | **SparkWitch**：`sparkwitch:use_skill` → `WitchSkillRegistry`（`SparkWitchBuiltInSkills.register()` 注册 12 个直接技能 + 6 个学徒技能，共 18 个） | `WitchSkillUseService` → `WitchSkillDefinition.useHandler` |

```java
// 一技能：只借用按键，不注册按键
if (WitchAbilityKeyBridge.wasPressed()) {
    CurserClientHooks.use();          // 发包，判定在服务端
}

// 二技能：按角色 id 分发，handler 归该职业的 client 模块
SecondaryAbilityRegistry.register(SparkWitchRoles.BLACK_RAVEN_CROW_ID, handler);
```

规则：

1. **不准**再 `KeyBindingHelper.registerKeyBinding` 新键，**不准**用 `GLFW.glfwGetKey` 轮询按键；键位由玩家在「控制」里改，硬编码轮询会绕过它。
2. 一技能用 `WitchAbilityKeyBridge.wasPressed()`，它把 NoellesRoles 已经消费掉的按下事件补回来（短按不丢）；不要在 `ClientTickEvents` 里自己判 `isPressed()`。
3. 二技能的 `SecondaryAbilityHandler` 放在**该职业的 client 模块**里注册（`client/<role>/XxxClientModule.register()`），只做「本地预判能不能发」+ 发包。
4. 客户端预判与服务端校验**共用同一个 `Rules` 函数**（如 `CurserRules.canSend(...)` 接收双方各自能拿到的布尔/数值），禁止客户端写一套、服务端另写一套。
5. HUD、提示、tooltip 里的键名**不准硬编码 `"G"` / `"N"`**：一技能用 `SparkWitchClient.abilityKeyText()`，二技能用 `SecondaryAbilityController.secondaryKeyText()`。
6. 新技能默认注册成 `WitchSkillDefinition`（冷却、法力、`selector`、`useHandler` 一体），**不要**另造一套冷却/法力记账。
7. 键位封锁双端都要拦：客户端 `GrandWitchFearClientHooks.shouldBlockRoleSkillPayload` / `ClientPlayNetworking` 的 Fear mixin，服务端在各 `*Service` 守卫链里查 `SparkTraitsKillerBridge.isRoleSkillBlocked`、眩晕、旁观。少拦一端就等于给恐惧开了后门。

---

## 7. 并发与热路径

### 7.1 【主流】幂等的 `register()`

257 处，形态完全统一：

```java
public final class BellRingerFeatureService {
    private static boolean registered;

    private BellRingerFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ...
    }
}
```

全仓库**没有** `new Thread`、`Thread.sleep`、`System.out`（各 0 处）。所有并发都交给游戏线程模型：服务端逻辑在 server thread，渲染逻辑在 render thread，跨线程用手写快照 + `volatile` 锁存。

### 7.2 每 tick 的代码先早退

```java
public static void tick(ServerPlayerEntity player, BellEchoPlayerComponent component) {
    boolean echo = component.hasEchoTask();
    boolean heard = component.heardUntilTick() != 0L;
    boolean tollReady = component.tollReady();
    long now = player.getServerWorld().getTime();
    boolean scan = now % BellRingerRules.TOLL_SCAN_INTERVAL_TICKS == 0L;
    // Hot path: idle players do nothing on 9 of every 10 ticks.
    // 热路径：无状态玩家每 10 tick 中有 9 tick 直接返回。
    if (!echo && !heard && !tollReady && !scan) {
        return;
    }
```

它的 Javadoc 明确写了调用频率和性能要求：「Called from `BellEchoPlayerComponent#serverTick()` for every player; must early-out cheaply.」

约定：

- 先读一次局部变量，再判断，避免重复跨组件调用。
- 昂贵操作（射线、遍历实体、发 `PlaySoundS2CPacket`）必须有**周期常量**：`*_SCAN_INTERVAL_TICKS`、`*_HINT_TICKS`。
- 周期性扫描用**世界时间取模**并对实体 id 偏移打散，避免同一 tick 全体触发。

### 7.3 加缓存必须写清失效条件

`CONTEXT.md` 的 *Hot-Path Caches and Cadences* 一节列出每一个缓存并说明「什么保证它仍然正确」。改到这些路径时，**必须同时保留失效逻辑**；不在清单里的一律每次重算。新增缓存的 Javadoc 要写：缓存键是什么、在哪个钩子清空、失败时怎么降级。

---

## 8. 网络与持久化

### 8.1 数据包

```java
public record FireCrimsonSkullC2SPayload(float yaw, float pitch) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkWitch.id("fire_crimson_skull");
    public static final Id<FireCrimsonSkullC2SPayload> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, FireCrimsonSkullC2SPayload> CODEC =
            PacketCodec.of(FireCrimsonSkullC2SPayload::write, FireCrimsonSkullC2SPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public boolean hasAim() {
        return Float.isFinite(yaw) && Float.isFinite(pitch);
    }

    public static FireCrimsonSkullC2SPayload read(PacketByteBuf buf) {
        if (buf.readableBytes() >= 2 * Float.BYTES) {
            return new FireCrimsonSkullC2SPayload(buf.readFloat(), buf.readFloat());
        }
        // Drain a malformed tail so the outer decoder does not reject leftover bytes.
        // 丢弃残缺尾部，避免外层解码器因剩余字节而断开连接。
        buf.skipBytes(buf.readableBytes());
        return new FireCrimsonSkullC2SPayload(Float.NaN, Float.NaN);
    }
}
```

（节选：省略了 `write`、类级 Javadoc 与 import。）

- 三件套固定：`PAYLOAD_ID`（`SparkWitch.id("snake_case")`）、`ID`、`CODEC`，外加 `getId()`；解码遇到残缺输入必须归一化成安全值（如上例的 `NaN` + 丢弃尾部），不能让外层解码器断连。
- 注册集中在 `net/SparkWitchPackets.register()`；角色自有的包可以在 `roles/<...>/net/XxxNetworking.register()` 里注册，由 `SparkWitchPackets` 调用（注释会写明「与搜寻者一样在自有类中注册」）。
- **lambda 里只做转调**：`(payload, context) -> XxxService.fire(context.player(), payload)`。
- 【硬】服务端一律**二次校验**：职业、存活、冷却、距离、会话 id 都在 Service/Rules 里重算；客户端发来的 id、目标、数量都当作不可信。
- 需要重排到主线程时用 `context.server().execute(...)`。

### 8.2 状态与同步

- 状态放 CCA 组件；**服务端是唯一权威**，客户端只做预测与倒计时。
- 客户端只拿**剩余 tick**，不拿服务端绝对时间：`writeSyncPacket` 里现算 `echoRemainingTicks()`，客户端本地递减。
- **只在变化时同步**：`if (value == newValue) return;` 再写再 `syncOwner()`。
- 私密状态用 `shouldSyncWith(recipient) { return recipient == player; }` 只发给本人；其他玩家可见的字段才广播。
- NBT 读写成对出现，**缺字段给默认值**（`tag.contains("EchoTask") ? tag.getInt("EchoTask") : NO_TASK`），键名 PascalCase。

---

## 9. 资源与本地化

### 9.1 lang 文件必须成对

- `assets/sparkwitch/lang/en_us.json` 与 `zh_cn.json`：**各 1,443 个键，100% 对齐**（0 缺失）。
- 键按功能/职业分块追加，**不强制字母序**；新增一行要同时加到两个文件。
- 类别前缀（实际用量）：`message`(273)、`announcement`(211)、`item`(175)、`gui`(167)、`hud`(148)、`replay`(104)、`screen`(77)、`tip`(70)、`skill`(53)、`shop`(42)、`subtitles`、`death_reason`、`command`、`game`、`option`、`entity`、`effect`、`key`。
- 角色文本四件套：`announcement.role.*`、`announcement.title.*`、`announcement.goal(s).*`、`announcement.win.*`；为兼容还各有一份 `announcement.*.sparkwitch.*`。
- 代码里 **`Text.translatable` 680 处 vs `Text.literal` 71 处**：`literal` 只用于**动态数据**（玩家名、数字、符号），任何固定文案都必须走翻译键。

### 9.2 声音与贴图

- 每个 `.ogg` 必须单条 Vorbis 流（见 §1）；重编码只保留音频轨。
- 声音 id 用 `<用途前缀>.<名字>`，前缀只有 `ambient`、`skill`、`item` 三种（`item` 可再带一段武器名，如 `item.usec_rifle.shoot`）；新增时同时登记到 `SparkWitchSounds` 与 `sounds.json`，`subtitles.sparkwitch.*` 补中英两条。
- 模型 `<category>/<name>.json`（目前只有 `models/item/`），贴图按 `textures/<entity|gui|item|font|particle|mob_effect>/<name>.png` 对齐注册 id。

---

## 10. 分支与 PR 命名

分支名是评审者看到的第一行信息。格式固定为 **`<前缀>/<标题>`**。

### 10.1 前缀

| 前缀 | 用于 | 本仓库历史用量 |
|---|---|---|
| `feat/`（`feature/`） | 新功能、新职业、新玩法 | 123 |
| `fix/`（`bugfix/`） | 修 bug | 75 |
| `hotfix/` | 紧急修复（线上或已合并版本） | 0 |
| `release/` | 版本发布准备 | 2 |
| `chore/` | 维护：依赖、版本号、许可、贴图替换，**不改任何代码逻辑** | 3 |
| `refactor/` | 结构调整，**行为不变** | 1 |
| `docs/` | 文档 | 2 |
| `test/` | 测试 | 0 |
| `experiment/`（`spike/`） | 试验性功能，可能不进入正式版 | 0 |

历史里还出现过 `update/`(14)、`merge/`(3)、`balance/`(1)、`rename/`(1)、`tune/`(1)、`perf/`(1)、`ci/`(1)——**不要再造新前缀**，按语义收敛到上表：

- `perf/…` → 纯提速、行为不变用 `refactor/`；带玩家可见效果用 `feat/`
- `ci/…` → `chore/`
- `update/…`、`merge/…`、`balance/`、`tune/`、`rename/` → 按实际改动归入 `fix/`、`feat/` 或 `chore/`

### 10.2 标题

- **全小写 kebab-case**，连字符分隔；不要下划线、不要驼峰、不要空格（Git 会把空格转义成 `%20`）。
- 3–6 个词，写「做了什么」而不是序号：`fix/knight-duel-bgm` ✅，`fix/bug-3` ❌。
- 不带 PR 号、日期、人名——PR 号由 GitHub 在合并时自动补进标题。
- **一个分支只做一件事**；顺手做的重构另开分支。

真实分支名示例：

```
feat/block-detective-interrogation
fix/emf-render-compat
fix/knight-duel-bgm
chore/version-bump
docs/code-style
```

### 10.3 叠分支

大功能允许在功能分支下再开子分支，先合并回父分支、再由父分支合 `main`：

```
Merge feat/brp-fix-client into feat/black-raven-police
Merge feat/black-raven-police into main (#153)
```

子分支同样遵循 `<前缀>/<标题>`，标题要写清它修的是父分支的哪一块（如上例的 `fix-client`）。

### 10.4 合并信息

`main` 上的合并提交形如 `Merge <分支名> into main (#NNN)`，由 GitHub 生成，**不要手写**成别的格式。

### 10.5 提交信息

单个提交用 Conventional Commits：`<type>(<scope>): <subject>`。

```
fix(render): entity render protocol for EMF/ETF and animation packs
fix(witch-maiden): aim the crimson skull at moving targets under the crosshair
feat(usec): right-click the AXMC with a magazine on the cursor to insert it
chore: version bump
docs(context): HUD text caches also key on HudFontOptions
```

- `type` 与分支前缀同名（`feat` / `fix` / `chore` / `docs` / `refactor` / `test`）。
- `scope` 取子系统或职业，小写英文（`render`、`net`、`hud`、`lang`）或用中文职业名（`fix(骑士): …`、`feat(猎人): …`）。
- `subject` 写「改了什么、为什么」，祈使句、句末不加句号，尽量一行说完。
- 一个提交只做一件事；纯格式化/重命名单列 `chore`。

---

## 11. 提交前检查清单

复制到 PR 描述里逐条打勾：

**格式**

- [ ] 4 空格缩进，无 Tab，无行尾空白（`git diff --check` 通过）
- [ ] 超长表达式断行时 `&&`/`||`/`?`/`:`/`.` 在行首
- [ ] 折行的参数列表续行 +8，右括号独占一行
- [ ] import 无通配符、无未使用项

**命名**

- [ ] 新类型落在既有后缀体系里（`Rules`/`Service`/`Runtime`/`Targeting`/`FeatureService`/`Component`/`Payload`…）
- [ ] 注册 id `snake_case`，NBT 键 PascalCase，翻译键遵循 `<类别>.sparkwitch.<path>`
- [ ] mixin 注入成员带 `sparkwitch$` 前缀，并已加入 `*.mixins.json`

**注释**

- [ ] 新类型有类级 Javadoc，**英文段 + 中文段**
- [ ] 非显然的决策写了「为什么」并引用 owner decision / 日期
- [ ] 没有 `TODO`/`FIXME`；没有中文日志
- [ ] 没有行尾注释

**设计**

- [ ] 新类默认 `final`；纯静态类有 `private` 构造器
- [ ] 判定在 `Rules`，副作用在 `Service`，注册在 `FeatureService.register()`
- [ ] `src/main` 没有引用 `src/client`
- [ ] 可空返回值标了 `@Nullable`，且写在类型前
- [ ] switch 用箭头形式，穷尽 enum 不加 `default`

**技能键与注册端**

- [ ] 没有新增 `KeyBinding`；一技能走 NoellesRoles 共享键，二技能走键 N（见 §6.4）
- [ ] 二技能 handler 注册在该职业自己的 client 模块，走 `SecondaryAbilityRegistry.register`
- [ ] 客户端预判与服务端校验共用同一个 `Rules` 函数，没有各写一套
- [ ] 键名文案来自 `abilityKeyText()` / `secondaryKeyText()`，没有硬编码 `"G"` / `"N"`
- [ ] Fear、眩晕、旁观等封锁在客户端与服务端**都**拦了

**状态与网络**

- [ ] 所有写入服务端权威；客户端只拿剩余量
- [ ] 同步只在值变化时发生；私密状态只发 owner
- [ ] 服务端对新包做了完整二次校验
- [ ] 新增/修改的缓存写明了失效条件

**构建**

- [ ] `./gradlew ... test verifyArchitecture compileJava compileClientJava` 通过
- [ ] 动过 client mixin：`verifyClientMixinSelectors` 通过
- [ ] 动过 `lang`/`sounds`/模型：`verifySoundAssets` 通过，中英键对齐

**分支与 PR**

- [ ] 分支名符合 `<前缀>/<kebab-case 标题>`，前缀在 §10.1 表内（没有自创新前缀）
- [ ] 标题让人不看 diff 也能猜到改了什么，且不含序号、日期、PR 号
- [ ] 一个分支只做一件事；大功能已按子分支拆分并先合回父分支

---

## 12. 反例对照表

| 不要写 | 为什么 | 照这个写 |
|---|---|---|
| `class Foo { }` | 本项目默认封闭 | `public final class Foo { }` |
| `public Foo() {}`（纯工具类） | 防止被实例化 | `private Foo() { }` |
| `case A:` 冒号 switch | 全仓库 0 处 | `case A -> { ... }` |
| `x == null ? a : b` 在行尾断 | 阅读时先看到值看不到条件 | `?`/`:` 移到行首 |
| `@Nullable private UUID id;` | 注解属于类型 | `private @Nullable UUID id;` |
| `Optional<Foo> get()`（自定义 API） | 本项目用 `@Nullable` | `public @Nullable Foo get()` |
| `// TODO: 之后修` | 0 处；工作项在 plan 文件里 | 写清当前行为与约束，或直接不留 |
| `// 这里清空标记` | 重复代码语义 | 写「为什么必须在这里清空」并引用决策 |
| `LOGGER.info("已完成")` | 日志只用英文 | `LOGGER.warn("Dropping a … source that threw", failure);` |
| `System.out.println(...)` | 0 处 | `SparkWitch.LOGGER.warn(...)` |
| `new Thread(...)` / `Thread.sleep` | 0 处；破坏 tick 模型 | 挂到 `serverTick` / `ServerLifecycleEvents` |
| `if (world.isClient) return;` 写在 Service 顶层当成唯一防护 | 客户端仍会走到部分路径 | 在入口按职责分层校验，服务端再算一遍 |
| `component.sync()` 每次写都调 | 网络放大 | 先判断是否变化 |
| `Text.literal("冷却中")` | 无法翻译 | `Text.translatable("message.sparkwitch.x.cooldown")` |
| `import dev.caecorthus.sparkwitch.client.*;`（main 里） | 服务端无客户端类 | 用 `api/` 的接口 + install 钩子 |
| 反射直连 SparkTraits 内部包 | `verifyArchitecture` 直接失败 | 只反射公共门面（`SparkTraitsApi`） |
| `getRenderer(entity).render(...)` | 破坏 EMF/ETF 与动画包兼容 | 走 `EntityRedraw` / `SecondaryWorldPass.run` |
| `require = 0` 放宽注入 | `verifyClientMixinSelectors` 会失败 | 保持 `require` 为真实数量 |

---

## 13. 附：本次统计的原始指标

| 指标 | 值 |
|---|---|
| Java 文件 / 行数 | 1,813 / 175,437 |
| 代码 : 注释 : 空行 | 56% : 35% : 9% |
| 类级 Javadoc 覆盖率 | 1,717 / 1,813（95%） |
| 双语（含中文）类文档 | 1,632 |
| `public final class` / 其他 final | 971 / 84 |
| 非 final 公有类 | 28（全部为 `Entity`/`Item`/`Screen`/`Renderer` 子类与 `*State` 状态容器） |
| `switch` 箭头 / 冒号 | 512 / 0 |
| `@Nullable` / `@NotNull` / `@NonNull` | 1,292 / 84 / 0 |
| `record` | 226 |
| `var` / `final` 参数 | 62 / 16 |
| `static synchronized void register()` | 257（223 个 public + 34 个包内） |
| `Rules` 类 / 其中静态方法 | 192 / 2,499 |
| mixin 文件 | 429（Mixin 410、Accessor 15、Invoker 2、MixinPlugin 2） |
| 通配 import | 3 |
| `TODO`/`FIXME`/`XXX`/`HACK` | 0 |
| `System.out` / `new Thread` / `Thread.sleep` | 0 / 0 / 0 |
| 日志 warn : error : info : debug | 57 : 12 : 10 : 2 |
| `Text.translatable` / `Text.literal` | 680 / 71 |
| lang 键（en = zh） | 1,443 = 1,443 |
| `src/main` → `src/client` 引用 | 0 |

---

*本规范由对 `0aa48193` 的全量扫描与抽样精读得出，描述的是**现状**而非理想。新增代码与既有代码冲突时：以 `CONTEXT.md` 的架构约束和 `build.gradle` 的机器校验为准；其余情况保持与同目录邻近代码一致。*
