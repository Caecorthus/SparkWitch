package dev.caecorthus.sparkwitch.client.bedrock.molang;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockModelException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Compiles the Molang subset that Blockbench and YSM animations use. See the plan for the supported grammar.
 * 编译 Blockbench 与 YSM 动画使用的 Molang 子集。支持的语法见计划文档。
 *
 * <p>Semantics follow the Bedrock Molang docs (bedrock.dev/docs/stable/Molang and Microsoft Learn "Molang Syntax
 * Guide"): precedence from high to low is unary {@code ! -}, {@code * /}, {@code + -}, {@code < <= > >=},
 * {@code == !=}, {@code &&}, {@code ||}, {@code ?:} (right-associative, as since 1.18.10), {@code ??}, then
 * {@code =}. Trig takes and returns degrees. A list of two or more statements is 0 unless a {@code return} runs; a
 * single expression with one trailing {@code ;} keeps its value, as Blockbench's molangjs preview does.
 * {@code math.sign} follows the Bedrock description ("1 if value is positive, -1 otherwise"), {@code math.round}
 * rounds half up like molangjs and GeckoLib. Constant sub-trees are folded at compile time.
 * 语义遵循 Bedrock Molang 文档：优先级从高到低为一元 {@code ! -}、乘除、加减、大小比较、相等比较、{@code &&}、
 * {@code ||}、三元运算（右结合，1.18.10 起）、{@code ??}、赋值。三角函数输入输出均为角度。两条及以上语句在没有 return 时值为
 * 0；单个表达式带一个结尾分号时保留其值（与 Blockbench 的 molangjs 预览一致）。{@code math.sign} 按 Bedrock 描述（正数为
 * 1，否则为 -1），{@code math.round} 与 molangjs、GeckoLib 一样四舍五入（.5 向上）。常量子树在编译期折叠。</p>
 */
public final class MolangCompiler {
    /** Longest accepted source. / 允许的最长源码。 */
    public static final int MAX_SOURCE_LENGTH = 16384;
    /** Deepest accepted syntax tree (bounds evaluation recursion). / 允许的最深语法树（限制求值递归）。 */
    public static final int MAX_TREE_HEIGHT = 256;

    private static final Map<String, MathFunction> MATH = new HashMap<>();

    static {
        unary("abs", Math::abs);
        unary("sin", x -> Math.sin(Math.toRadians(x)));
        unary("cos", x -> Math.cos(Math.toRadians(x)));
        unary("asin", x -> Math.toDegrees(Math.asin(x)));
        unary("acos", x -> Math.toDegrees(Math.acos(x)));
        unary("atan", x -> Math.toDegrees(Math.atan(x)));
        binary("atan2", (y, x) -> Math.toDegrees(Math.atan2(y, x)));
        ternary("clamp", (v, min, max) -> Math.max(min, Math.min(max, v)));
        ternary("lerp", (a, b, t) -> a + (b - a) * t);
        // Shortest way round the circle, like Mojang's Mth.rotLerp (GeckoLib LerpRotFunction).
        // 沿圆周最短方向插值，同 Mojang Mth.rotLerp（GeckoLib LerpRotFunction）。
        ternary("lerprotate", (a, b, t) -> a + minAngle(b - a) * t);
        binary("min", Math::min);
        binary("max", Math::max);
        binary("pow", Math::pow);
        unary("sqrt", Math::sqrt);
        unary("exp", Math::exp);
        unary("ln", Math::log);
        unary("floor", Math::floor);
        unary("ceil", Math::ceil);
        unary("round", x -> Math.floor(x + 0.5));
        unary("trunc", x -> x < 0.0 ? Math.ceil(x) : Math.floor(x));
        binary("mod", (a, b) -> a % b);
        unary("sign", x -> x > 0.0 ? 1.0 : -1.0);
        unary("hermite_blend", t -> 3.0 * t * t - 2.0 * t * t * t);
        unary("min_angle", MolangCompiler::minAngle);
        MATH.put("pi", new MathFunction(0, null, null, null, -1));
        MATH.put("random", new MathFunction(2, null, null, null, MolangNode.Random.RANDOM));
        MATH.put("random_integer", new MathFunction(2, null, null, null, MolangNode.Random.RANDOM_INTEGER));
        MATH.put("die_roll", new MathFunction(3, null, null, null, MolangNode.Random.DIE_ROLL));
        MATH.put("die_roll_integer", new MathFunction(3, null, null, null, MolangNode.Random.DIE_ROLL_INTEGER));
    }

    private MolangCompiler() {
    }

    public static MolangExpression compile(String source) throws BedrockModelException {
        if (source == null) {
            throw new BedrockModelException("Molang expression is missing");
        }
        if (source.length() > MAX_SOURCE_LENGTH) {
            throw new MolangSyntaxException(source, MAX_SOURCE_LENGTH,
                    "expression is longer than " + MAX_SOURCE_LENGTH + " characters");
        }
        MolangNode root = new Parser(source).parseProgram();
        if (root.isConstant()) {
            return MolangExpression.constant(root.eval(null));
        }
        return new Compiled(root, source);
    }

    /** [-180, 180), as Bedrock describes {@code math.min_angle}. / 映射到 [-180, 180)。 */
    static double minAngle(double degrees) {
        return degrees - 360.0 * Math.floor((degrees + 180.0) / 360.0);
    }

    private static void unary(String name, DoubleUnaryOperator f) {
        MATH.put(name, new MathFunction(1, f, null, null, -1));
    }

    private static void binary(String name, DoubleBinaryOperator f) {
        MATH.put(name, new MathFunction(2, null, f, null, -1));
    }

    private static void ternary(String name, MolangNode.DoubleTernaryOperator f) {
        MATH.put(name, new MathFunction(3, null, null, f, -1));
    }

    private record MathFunction(int arity, DoubleUnaryOperator f1,
                                DoubleBinaryOperator f2, MolangNode.DoubleTernaryOperator f3,
                                int randomKind) {
    }

    /** The top-level entry: clears temps and the return flag around every evaluation. / 顶层入口。 */
    private static final class Compiled implements MolangExpression {
        private final MolangNode root;
        private final String source;

        Compiled(MolangNode root, String source) {
            this.root = root;
            this.source = source;
        }

        @Override
        public double evaluate(MolangRuntime runtime) {
            if (runtime == null) {
                return 0.0;
            }
            runtime.beginEvaluation();
            double value = root.eval(runtime);
            if (runtime.returning) {
                value = runtime.returnValue;
                runtime.returning = false;
            }
            return value;
        }

        @Override
        public String toString() {
            return source;
        }
    }

    // ---- lexer / 词法 ----

    private enum Kind {
        NUMBER, NAME, LPAREN, RPAREN, LBRACE, RBRACE, COMMA, SEMI, PLUS, MINUS, STAR, SLASH, BANG,
        LT, LE, GT, GE, EQ, NE, AND, OR, QUESTION, COALESCE, COLON, ASSIGN, EOF
    }

    private record Token(Kind kind, int start, int end, String text, double number) {
    }

    private static List<Token> tokenize(String s) throws MolangSyntaxException {
        List<Token> tokens = new ArrayList<>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int start = i;
            if (isDigit(c) || (c == '.' && i + 1 < n && isDigit(s.charAt(i + 1)))) {
                while (i < n && isDigit(s.charAt(i))) {
                    i++;
                }
                if (i < n && s.charAt(i) == '.') {
                    i++;
                    while (i < n && isDigit(s.charAt(i))) {
                        i++;
                    }
                }
                if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    int j = i + 1;
                    if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
                        j++;
                    }
                    if (j < n && isDigit(s.charAt(j))) {
                        i = j;
                        while (i < n && isDigit(s.charAt(i))) {
                            i++;
                        }
                    }
                }
                double value;
                try {
                    value = Double.parseDouble(s.substring(start, i));
                } catch (NumberFormatException e) {
                    throw new MolangSyntaxException(s, start, "bad number '" + s.substring(start, i) + "'");
                }
                if (i < n && isNameStart(s.charAt(i))) {
                    throw new MolangSyntaxException(s, i, "unexpected '" + s.charAt(i) + "' after a number");
                }
                tokens.add(new Token(Kind.NUMBER, start, i, null, value));
                continue;
            }
            if (isNameStart(c)) {
                i++;
                while (true) {
                    while (i < n && isNamePart(s.charAt(i))) {
                        i++;
                    }
                    if (i + 1 < n && s.charAt(i) == '.' && isNameStart(s.charAt(i + 1))) {
                        i++;
                        continue;
                    }
                    break;
                }
                tokens.add(new Token(Kind.NAME, start, i, s.substring(start, i).toLowerCase(Locale.ROOT), 0.0));
                continue;
            }
            char next = i + 1 < n ? s.charAt(i + 1) : '\0';
            Kind kind;
            int width = 1;
            switch (c) {
                case '(' -> kind = Kind.LPAREN;
                case ')' -> kind = Kind.RPAREN;
                case '{' -> kind = Kind.LBRACE;
                case '}' -> kind = Kind.RBRACE;
                case ',' -> kind = Kind.COMMA;
                case ';' -> kind = Kind.SEMI;
                case '+' -> kind = Kind.PLUS;
                case '*' -> kind = Kind.STAR;
                case '/' -> kind = Kind.SLASH;
                case ':' -> kind = Kind.COLON;
                case '-' -> {
                    if (next == '>') {
                        throw new MolangSyntaxException(s, start, "the arrow operator '->' is not supported");
                    }
                    kind = Kind.MINUS;
                }
                case '!' -> {
                    kind = next == '=' ? Kind.NE : Kind.BANG;
                    width = next == '=' ? 2 : 1;
                }
                case '<' -> {
                    kind = next == '=' ? Kind.LE : Kind.LT;
                    width = next == '=' ? 2 : 1;
                }
                case '>' -> {
                    kind = next == '=' ? Kind.GE : Kind.GT;
                    width = next == '=' ? 2 : 1;
                }
                case '=' -> {
                    kind = next == '=' ? Kind.EQ : Kind.ASSIGN;
                    width = next == '=' ? 2 : 1;
                }
                case '?' -> {
                    kind = next == '?' ? Kind.COALESCE : Kind.QUESTION;
                    width = next == '?' ? 2 : 1;
                }
                case '&' -> {
                    if (next != '&') {
                        throw new MolangSyntaxException(s, start, "unexpected '&' (use '&&')");
                    }
                    kind = Kind.AND;
                    width = 2;
                }
                case '|' -> {
                    if (next != '|') {
                        throw new MolangSyntaxException(s, start, "unexpected '|' (use '||')");
                    }
                    kind = Kind.OR;
                    width = 2;
                }
                case '\'', '"' -> throw new MolangSyntaxException(s, start, "strings are not supported");
                case '[', ']' -> throw new MolangSyntaxException(s, start, "arrays are not supported");
                default -> throw new MolangSyntaxException(s, start, "unexpected character '" + c + "'");
            }
            i += width;
            tokens.add(new Token(kind, start, i, null, 0.0));
        }
        tokens.add(new Token(Kind.EOF, n, n, null, 0.0));
        return tokens;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isNamePart(char c) {
        return isNameStart(c) || isDigit(c);
    }

    // ---- parser / 语法分析 ----

    private static final class Parser {
        private final String source;
        private final List<Token> tokens;
        private int index;
        private int depth;

        Parser(String source) throws MolangSyntaxException {
            this.source = source;
            this.tokens = tokenize(source);
        }

        MolangNode parseProgram() throws MolangSyntaxException {
            List<MolangNode> statements = new ArrayList<>();
            int semicolons = parseStatements(statements, Kind.EOF);
            expect(Kind.EOF, "end of expression");
            if (statements.isEmpty()) {
                return new MolangNode.Const(0.0);
            }
            // One expression, optionally with a trailing ';', keeps its value (molangjs strips a final ';').
            // 单个表达式（可带一个结尾分号）保留其值（molangjs 会去掉结尾分号）。
            if (statements.size() == 1 && semicolons <= 1) {
                return statements.get(0);
            }
            return statementList(statements);
        }

        /** Parses {@code stmt (; stmt?)*} until {@code end}; returns how many ';' it saw. */
        private int parseStatements(List<MolangNode> out, Kind end) throws MolangSyntaxException {
            int semicolons = 0;
            while (peek().kind() != end && peek().kind() != Kind.EOF) {
                if (peek().kind() == Kind.SEMI) {
                    next();
                    semicolons++;
                    continue;
                }
                out.add(parseStatement());
                Kind after = peek().kind();
                if (after == Kind.SEMI) {
                    next();
                    semicolons++;
                } else if (after != end && after != Kind.EOF) {
                    throw error(peek(), "expected ';' between statements");
                }
            }
            return semicolons;
        }

        private MolangNode statementList(List<MolangNode> statements) throws MolangSyntaxException {
            int height = 1;
            for (MolangNode statement : statements) {
                height = Math.max(height, statement.height + 1);
            }
            return checked(new MolangNode.Statements(statements.toArray(new MolangNode[0]), height), peek());
        }

        private MolangNode parseStatement() throws MolangSyntaxException {
            Token token = peek();
            if (token.kind() == Kind.NAME && token.text().equals("return")) {
                next();
                return checked(new MolangNode.Return(parseExpression()), token);
            }
            return parseExpression();
        }

        private MolangNode parseExpression() throws MolangSyntaxException {
            descend();
            try {
                return parseExpressionBody();
            } finally {
                depth--;
            }
        }

        private MolangNode parseExpressionBody() throws MolangSyntaxException {
            MolangNode target = parseCoalesce();
            if (peek().kind() != Kind.ASSIGN) {
                return target;
            }
            Token assign = next();
            MolangNode value = parseExpression();
            if (target instanceof MolangNode.VariableRead read) {
                return checked(new MolangNode.VariableAssign(read.name, value), assign);
            }
            if (target instanceof MolangNode.TempRead read) {
                return checked(new MolangNode.TempAssign(read.name, value), assign);
            }
            throw error(assign, "can only assign to variable.* or temp.*");
        }

        private MolangNode parseCoalesce() throws MolangSyntaxException {
            descend();
            try {
                return parseCoalesceBody();
            } finally {
                depth--;
            }
        }

        private MolangNode parseCoalesceBody() throws MolangSyntaxException {
            MolangNode left = parseConditional();
            if (peek().kind() != Kind.COALESCE) {
                return left;
            }
            Token op = next();
            MolangNode right = parseCoalesce();
            if (left instanceof MolangNode.Undefined) {
                return right;
            }
            if (left.isConstant()) {
                return left;
            }
            return checked(new MolangNode.Coalesce(left, right), op);
        }

        private MolangNode parseConditional() throws MolangSyntaxException {
            descend();
            try {
                return parseConditionalBody();
            } finally {
                depth--;
            }
        }

        private MolangNode parseConditionalBody() throws MolangSyntaxException {
            MolangNode condition = parseOr();
            if (peek().kind() != Kind.QUESTION) {
                return condition;
            }
            Token op = next();
            MolangNode then = parseConditional();
            MolangNode otherwise;
            if (peek().kind() == Kind.COLON) {
                next();
                otherwise = parseConditional();
            } else {
                otherwise = new MolangNode.Const(0.0);
            }
            if (condition.isConstant()) {
                return condition.eval(null) != 0.0 ? then : otherwise;
            }
            return checked(new MolangNode.Conditional(condition, then, otherwise), op);
        }

        private MolangNode parseOr() throws MolangSyntaxException {
            MolangNode left = parseAnd();
            while (peek().kind() == Kind.OR) {
                Token op = next();
                left = fold(new MolangNode.Or(left, parseAnd()), op);
            }
            return left;
        }

        private MolangNode parseAnd() throws MolangSyntaxException {
            MolangNode left = parseEquality();
            while (peek().kind() == Kind.AND) {
                Token op = next();
                left = fold(new MolangNode.And(left, parseEquality()), op);
            }
            return left;
        }

        private MolangNode parseEquality() throws MolangSyntaxException {
            MolangNode left = parseRelational();
            while (true) {
                Kind kind = peek().kind();
                int op;
                if (kind == Kind.EQ) {
                    op = MolangNode.Binary.EQ;
                } else if (kind == Kind.NE) {
                    op = MolangNode.Binary.NE;
                } else {
                    return left;
                }
                Token token = next();
                left = fold(new MolangNode.Binary(op, left, parseRelational()), token);
            }
        }

        private MolangNode parseRelational() throws MolangSyntaxException {
            MolangNode left = parseAdditive();
            while (true) {
                int op = switch (peek().kind()) {
                    case LT -> MolangNode.Binary.LT;
                    case LE -> MolangNode.Binary.LE;
                    case GT -> MolangNode.Binary.GT;
                    case GE -> MolangNode.Binary.GE;
                    default -> -1;
                };
                if (op < 0) {
                    return left;
                }
                Token token = next();
                left = fold(new MolangNode.Binary(op, left, parseAdditive()), token);
            }
        }

        private MolangNode parseAdditive() throws MolangSyntaxException {
            MolangNode left = parseMultiplicative();
            while (true) {
                Kind kind = peek().kind();
                if (kind != Kind.PLUS && kind != Kind.MINUS) {
                    return left;
                }
                Token token = next();
                int op = kind == Kind.PLUS ? MolangNode.Binary.ADD : MolangNode.Binary.SUB;
                left = fold(new MolangNode.Binary(op, left, parseMultiplicative()), token);
            }
        }

        private MolangNode parseMultiplicative() throws MolangSyntaxException {
            MolangNode left = parseUnary();
            while (true) {
                Kind kind = peek().kind();
                if (kind != Kind.STAR && kind != Kind.SLASH) {
                    return left;
                }
                Token token = next();
                int op = kind == Kind.STAR ? MolangNode.Binary.MUL : MolangNode.Binary.DIV;
                left = fold(new MolangNode.Binary(op, left, parseUnary()), token);
            }
        }

        private MolangNode parseUnary() throws MolangSyntaxException {
            descend();
            try {
                return parseUnaryBody();
            } finally {
                depth--;
            }
        }

        private MolangNode parseUnaryBody() throws MolangSyntaxException {
            Token token = peek();
            switch (token.kind()) {
                case MINUS -> {
                    next();
                    return fold(new MolangNode.Negate(parseUnary()), token);
                }
                case BANG -> {
                    next();
                    return fold(new MolangNode.Not(parseUnary()), token);
                }
                case PLUS -> {
                    next();
                    return parseUnary();
                }
                default -> {
                    return parsePrimary();
                }
            }
        }

        private MolangNode parsePrimary() throws MolangSyntaxException {
            Token token = next();
            switch (token.kind()) {
                case NUMBER -> {
                    return new MolangNode.Const(token.number());
                }
                case LPAREN -> {
                    MolangNode inner = parseExpression();
                    expect(Kind.RPAREN, "')'");
                    return inner;
                }
                case LBRACE -> {
                    List<MolangNode> statements = new ArrayList<>();
                    parseStatements(statements, Kind.RBRACE);
                    expect(Kind.RBRACE, "'}'");
                    return statementList(statements);
                }
                case NAME -> {
                    return parseName(token);
                }
                case EOF -> throw error(token, "unexpected end of expression");
                default -> throw error(token, "unexpected '" + describe(token) + "'");
            }
        }

        private MolangNode parseName(Token token) throws MolangSyntaxException {
            String name = token.text();
            int dot = name.indexOf('.');
            String space = dot < 0 ? name : name.substring(0, dot);
            String member = dot < 0 ? "" : name.substring(dot + 1);
            switch (space) {
                case "true", "false" -> {
                    if (dot < 0) {
                        return new MolangNode.Const(space.equals("true") ? 1.0 : 0.0);
                    }
                    return ignoredCall();
                }
                case "return" -> throw error(token, "'return' must start a statement");
                case "loop", "for_each", "break", "continue" ->
                        throw error(token, "'" + space + "' is not supported");
                case "query", "q" -> {
                    if (member.isEmpty()) {
                        return ignoredCall();
                    }
                    if (peek().kind() == Kind.LPAREN) {
                        // Function-style queries with arguments read 0; q.name() is the plain query.
                        // 带参数的函数式查询读作 0；q.name() 等同于普通查询。
                        if (parseArguments().isEmpty()) {
                            return new MolangNode.QueryRead(member);
                        }
                        return MolangNode.Undefined.INSTANCE;
                    }
                    return new MolangNode.QueryRead(member);
                }
                case "variable", "v", "temp", "t" -> {
                    if (member.isEmpty()) {
                        return ignoredCall();
                    }
                    if (peek().kind() == Kind.LPAREN) {
                        throw error(peek(), "'" + name + "' is not a function");
                    }
                    return space.startsWith("v") ? new MolangNode.VariableRead(member) : new MolangNode.TempRead(member);
                }
                case "math" -> {
                    return parseMath(token, member);
                }
                default -> {
                    // context.*, c.*, this, ysm.*, geometry.*, and anything unknown read 0.
                    // context.*、c.*、this、ysm.*、geometry.* 及任何未知名字读作 0。
                    return ignoredCall();
                }
            }
        }

        private MolangNode parseMath(Token token, String member) throws MolangSyntaxException {
            MathFunction function = MATH.get(member);
            if (function == null) {
                return ignoredCall();
            }
            List<MolangNode> args;
            if (peek().kind() == Kind.LPAREN) {
                args = parseArguments();
            } else if (function.arity() == 0) {
                args = List.of();
            } else {
                throw error(token, "math." + member + " needs " + arguments(function.arity()));
            }
            if (args.size() != function.arity()) {
                throw error(token, "math." + member + " takes " + arguments(function.arity()) + ", got "
                        + args.size());
            }
            if (function.arity() == 0) {
                return new MolangNode.Const(Math.PI);
            }
            if (function.randomKind() >= 0) {
                boolean roll = args.size() == 3;
                return checked(new MolangNode.Random(function.randomKind(), roll ? args.get(0) : null,
                        args.get(roll ? 1 : 0), args.get(roll ? 2 : 1)), token);
            }
            MolangNode call = switch (function.arity()) {
                case 1 -> new MolangNode.Call1(function.f1(), args.get(0));
                case 2 -> new MolangNode.Call2(function.f2(), args.get(0), args.get(1));
                default -> new MolangNode.Call3(function.f3(), args.get(0), args.get(1), args.get(2));
            };
            return fold(call, token);
        }

        /** Skips an optional argument list; the value reads 0. / 跳过可选参数列表，值读作 0。 */
        private MolangNode ignoredCall() throws MolangSyntaxException {
            if (peek().kind() == Kind.LPAREN) {
                parseArguments();
            }
            return MolangNode.Undefined.INSTANCE;
        }

        private List<MolangNode> parseArguments() throws MolangSyntaxException {
            expect(Kind.LPAREN, "'('");
            List<MolangNode> args = new ArrayList<>();
            if (peek().kind() == Kind.RPAREN) {
                next();
                return args;
            }
            while (true) {
                args.add(parseExpression());
                Token token = next();
                if (token.kind() == Kind.RPAREN) {
                    return args;
                }
                if (token.kind() != Kind.COMMA) {
                    throw error(token, "expected ',' or ')' in the argument list");
                }
            }
        }

        /** Bounds parser recursion (nested parentheses, unary or ternary chains). / 限制解析递归深度。 */
        private void descend() throws MolangSyntaxException {
            if (++depth > MAX_TREE_HEIGHT) {
                depth--;
                throw error(peek(), "expression is nested too deeply");
            }
        }

        /** Folds a pure node with constant inputs; rejects trees deeper than {@link #MAX_TREE_HEIGHT}. */
        private MolangNode fold(MolangNode node, Token at) throws MolangSyntaxException {
            if (node.isConstant()) {
                return new MolangNode.Const(node.eval(null));
            }
            return checked(node, at);
        }

        private MolangNode checked(MolangNode node, Token at) throws MolangSyntaxException {
            if (node.height > MAX_TREE_HEIGHT) {
                throw error(at, "expression is nested too deeply");
            }
            return node;
        }

        private Token peek() {
            return tokens.get(index);
        }

        private Token next() {
            Token token = tokens.get(index);
            if (token.kind() != Kind.EOF) {
                index++;
            }
            return token;
        }

        private void expect(Kind kind, String what) throws MolangSyntaxException {
            Token token = next();
            if (token.kind() != kind) {
                throw error(token, token.kind() == Kind.EOF ? "expected " + what + " before the end"
                        : "expected " + what + " but found '" + describe(token) + "'");
            }
        }

        private MolangSyntaxException error(Token token, String detail) {
            return new MolangSyntaxException(source, token.start(), detail);
        }

        private String describe(Token token) {
            if (token.kind() == Kind.EOF) {
                return "end";
            }
            return source.substring(token.start(), Math.min(token.end(), token.start() + 24));
        }

        private static String arguments(int count) {
            return count == 1 ? "1 argument" : count + " arguments";
        }
    }
}
