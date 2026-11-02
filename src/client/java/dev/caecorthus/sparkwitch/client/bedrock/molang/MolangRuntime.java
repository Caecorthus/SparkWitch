package dev.caecorthus.sparkwitch.client.bedrock.molang;

import java.util.Arrays;
import java.util.Locale;

/**
 * Per-model Molang state: query values set by the caller each frame (canonical lower-case names without the
 * {@code query.}/{@code q.} prefix, e.g. {@code anim_time}), persistent {@code variable.*} values, and per-evaluation
 * {@code temp.*} values. Unknown names read as 0.
 * 每个模型各自的 Molang 状态：调用方每帧设置的查询值（规范的小写名字，不含 {@code query.}/{@code q.} 前缀，例如
 * {@code anim_time}）、持久的 {@code variable.*} 值，以及每次求值的 {@code temp.*} 值。未知名字读作 0。
 *
 * <p>Not thread-safe: one runtime belongs to one model on one thread (the render thread). Values live in arrays indexed
 * by {@link MolangNames} slots, so steady-state evaluation allocates nothing; an array only grows the first time a new
 * name is written. Temps are cleared in O(1) at the start of every top-level evaluation by bumping a generation stamp.
 * 非线程安全：一个运行时只属于一个模型、一个线程（渲染线程）。值存放在按 {@link MolangNames} 槽位索引的数组中，稳定状态下
 * 求值不分配内存；只有首次写入新名字时数组才会扩容。每次顶层求值开始时通过递增代号以 O(1) 清空临时变量。</p>
 */
public final class MolangRuntime {
    private static final int INITIAL_SLOTS = 16;

    private double[] queryValues = new double[INITIAL_SLOTS];
    private boolean[] queryDefined = new boolean[INITIAL_SLOTS];
    private double[] variableValues = new double[INITIAL_SLOTS];
    private boolean[] variableDefined = new boolean[INITIAL_SLOTS];
    private double[] tempValues = new double[INITIAL_SLOTS];
    private int[] tempStamp = new int[INITIAL_SLOTS];
    private int tempGeneration = 1;
    private long randomState = mixSeed(System.nanoTime());

    /** Set by a {@code return} statement; read by statement lists. / 由 return 语句设置，语句序列读取。 */
    boolean returning;
    double returnValue;

    public void setQuery(String name, double value) {
        int id = MolangNames.intern(canonical(name, "query.", "q."));
        if (id < 0) {
            return;
        }
        if (id >= queryValues.length) {
            int size = grownSize(queryValues.length, id);
            queryValues = Arrays.copyOf(queryValues, size);
            queryDefined = Arrays.copyOf(queryDefined, size);
        }
        queryValues[id] = value;
        queryDefined[id] = true;
    }

    public double query(String name) {
        return queryValue(MolangNames.find(canonical(name, "query.", "q.")));
    }

    public double variable(String name) {
        return variableValue(MolangNames.find(canonical(name, "variable.", "v.")));
    }

    public void setVariable(String name, double value) {
        int id = MolangNames.intern(canonical(name, "variable.", "v."));
        if (id >= 0) {
            storeVariable(id, value);
        }
    }

    /** Forgets every {@code variable.*} (e.g. on a skin change). / 清除所有 variable.*（例如切换皮肤时）。 */
    public void clearVariables() {
        Arrays.fill(variableValues, 0.0);
        Arrays.fill(variableDefined, false);
    }

    /** Forgets every query value. / 清除所有查询值。 */
    public void clearQueries() {
        Arrays.fill(queryValues, 0.0);
        Arrays.fill(queryDefined, false);
    }

    /** Makes {@code math.random*}/{@code math.die_roll*} repeatable. / 使随机函数可复现。 */
    public void setRandomSeed(long seed) {
        randomState = mixSeed(seed);
    }

    // ---- package-private fast paths used by compiled nodes / 编译节点使用的包内快速路径 ----

    double queryValue(int id) {
        return id >= 0 && id < queryDefined.length && queryDefined[id] ? queryValues[id] : 0.0;
    }

    boolean hasQuery(int id) {
        return id >= 0 && id < queryDefined.length && queryDefined[id];
    }

    double variableValue(int id) {
        return id >= 0 && id < variableDefined.length && variableDefined[id] ? variableValues[id] : 0.0;
    }

    boolean hasVariable(int id) {
        return id >= 0 && id < variableDefined.length && variableDefined[id];
    }

    void storeVariable(int id, double value) {
        if (id >= variableValues.length) {
            int size = grownSize(variableValues.length, id);
            variableValues = Arrays.copyOf(variableValues, size);
            variableDefined = Arrays.copyOf(variableDefined, size);
        }
        variableValues[id] = value;
        variableDefined[id] = true;
    }

    double tempValue(int id) {
        return hasTemp(id) ? tempValues[id] : 0.0;
    }

    boolean hasTemp(int id) {
        return id >= 0 && id < tempStamp.length && tempStamp[id] == tempGeneration;
    }

    void storeTemp(int id, double value) {
        if (id >= tempValues.length) {
            int size = grownSize(tempValues.length, id);
            tempValues = Arrays.copyOf(tempValues, size);
            tempStamp = Arrays.copyOf(tempStamp, size);
        }
        tempValues[id] = value;
        tempStamp[id] = tempGeneration;
    }

    /** Starts one top-level evaluation: clears temps and the return flag. / 开始一次顶层求值：清空临时变量与返回标记。 */
    void beginEvaluation() {
        if (++tempGeneration == 0) {
            Arrays.fill(tempStamp, 0);
            tempGeneration = 1;
        }
        returning = false;
        returnValue = 0.0;
    }

    /** Uniform in [0, 1); xorshift64*, allocation-free. / [0, 1) 均匀分布；xorshift64*，不分配内存。 */
    double nextRandom() {
        long x = randomState;
        x ^= x >>> 12;
        x ^= x << 25;
        x ^= x >>> 27;
        randomState = x;
        return ((x * 0x2545F4914F6CDD1DL) >>> 11) * 0x1.0p-53;
    }

    private static long mixSeed(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return z == 0L ? 0x9E3779B97F4A7C15L : z;
    }

    private static int grownSize(int current, int id) {
        return Math.min(MolangNames.MAX_NAMES, Math.max(id + 1, current * 2));
    }

    /** Lower-cases (no copy when already lower-case) and drops a long or short namespace prefix. */
    private static String canonical(String name, String longPrefix, String shortPrefix) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith(longPrefix)) {
            return lower.substring(longPrefix.length());
        }
        if (lower.startsWith(shortPrefix)) {
            return lower.substring(shortPrefix.length());
        }
        return lower;
    }
}
