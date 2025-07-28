package com.example;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShamirSecret {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -cp bin com.example.ShamirSecret <input.json>");
            System.exit(1);
        }

        String json = Files.readString(Paths.get(args[0]));

        // --- parse n and k ---
        int n = extractInt(json, "\"keys\"\\s*:\\s*\\{[^}]*?\"n\"\\s*:\\s*(\\d+)");
        int k = extractInt(json, "\"keys\"\\s*:\\s*\\{[^}]*?\"k\"\\s*:\\s*(\\d+)");

        // --- parse shares robustly ---
        List<Point> shares = parseShares(json);

        if (shares.size() != n) {
            System.err.println("Warning: parsed shares (" + shares.size() + ") != n (" + n + ")");
        }
        if (shares.size() < k) {
            throw new IllegalArgumentException("Not enough shares to satisfy k. Parsed=" + shares.size() + ", k=" + k);
        }

        // --- enumerate all k-combinations and vote for constant term f(0) ---
        Map<Fraction, Integer> counts = new HashMap<>();
        int[] totalCombos = {0};
        combine(shares, k, 0, new ArrayList<>(), counts, totalCombos);

        Map.Entry<Fraction, Integer> top = counts.entrySet()
                .stream().max(Map.Entry.comparingByValue())
                .orElseThrow(() -> new IllegalStateException("No combinations produced a result"));

        Fraction secret = top.getKey();
        int frequency = top.getValue();

        // --- detect bad shares (never appear in combos yielding chosen secret) ---
        Set<BigInteger> goodXs = new HashSet<>();
        markGood(shares, k, secret, 0, new ArrayList<>(), goodXs);

        List<BigInteger> badXs = new ArrayList<>();
        for (Point p : shares) if (!goodXs.contains(p.x)) badXs.add(p.x);
        Collections.sort(badXs);

        // --- print ---
        System.out.println("Total combinations tried: " + totalCombos[0]);
        System.out.println("Secret (constant term c): " + (secret.isInteger() ? secret.toBigInteger() : secret));
        System.out.print("Bad shares detected at x:");
        System.out.println(badXs.isEmpty() ? " None" : " " + badXs);
        System.out.println("Top secret frequency: " + frequency);
    }

    // ---------------- Parsing ----------------

    /**
     * Parse all top-level numeric-keyed entries:  "1": { ... }, "2": { ... } ...
     * Accepts base and value in any order; base as number or quoted; value as quoted or bare token
     * and supports function values like sum(...), mul(...), gcd(...), lcm(...), pow(a,b).
     */
    static List<Point> parseShares(String json) {
        List<Point> shares = new ArrayList<>();

        // Grab pairs like:  "123": { <body> }
        Pattern entry = Pattern.compile("\"(\\d+)\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
        Matcher m = entry.matcher(json);
        while (m.find()) {
            String xKey = m.group(1);
            String body = m.group(2);

            // Extract base
            Integer base = null;
            Matcher mb = Pattern.compile("\"base\"\\s*:\\s*(?:\"(\\d+)\"|(\\d+))").matcher(body);
            if (mb.find()) {
                base = Integer.parseInt(mb.group(1) != null ? mb.group(1) : mb.group(2));
            }

            // Extract value (string or bare token)
            String valueRaw = null;
            Matcher mv = Pattern.compile("\"value\"\\s*:\\s*(?:\"([^\"]+)\"|([^,}\\s]+))").matcher(body);
            if (mv.find()) {
                valueRaw = mv.group(1) != null ? mv.group(1) : mv.group(2);
            }

            if (base == null || valueRaw == null) {
                // not a share entry
                continue;
            }

            BigInteger x = new BigInteger(xKey);
            BigInteger y;

            // If looks like a function e.g., sum(1,2,3) handle; else parse in given base.
            if (looksLikeFunction(valueRaw)) {
                y = evalFunction(valueRaw.trim());
            } else {
                y = new BigInteger(valueRaw.trim(), base);
            }

            shares.add(new Point(x, y));
        }

        return shares;
    }

    static boolean looksLikeFunction(String s) {
        return s.matches("[A-Za-z_][A-Za-z_0-9]*\\s*\\(.*\\)");
    }

    /**
     * Very small evaluator for functions:
     *  sum(...), add(...), mul(...), prod(...), gcd(...), lcm(...), pow(a,b)
     *  Numbers are parsed in base-10.
     */
    static BigInteger evalFunction(String expr) {
        Matcher mm = Pattern.compile("^\\s*([A-Za-z_][A-Za-z_0-9]*)\\s*\\((.*)\\)\\s*$", Pattern.DOTALL).matcher(expr);
        if (!mm.find()) throw new IllegalArgumentException("Bad function syntax: " + expr);
        String name = mm.group(1).toLowerCase(Locale.ROOT);
        String inside = mm.group(2);

        List<BigInteger> args = new ArrayList<>();
        // split by commas that are not nested (we assume no nested function args with commas in strings)
        for (String part : inside.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            // allow nested simple functions
            if (looksLikeFunction(t)) {
                args.add(evalFunction(t));
            } else {
                args.add(new BigInteger(t));
            }
        }

        switch (name) {
            case "sum":
            case "add":
                return args.stream().reduce(BigInteger.ZERO, BigInteger::add);
            case "mul":
            case "prod":
                return args.stream().reduce(BigInteger.ONE, BigInteger::multiply);
            case "gcd": {
                if (args.isEmpty()) return BigInteger.ZERO;
                BigInteger g = args.get(0).abs();
                for (int i = 1; i < args.size(); i++) g = g.gcd(args.get(i).abs());
                return g;
            }
            case "lcm": {
                if (args.isEmpty()) return BigInteger.ZERO;
                BigInteger l = args.get(0).abs();
                for (int i = 1; i < args.size(); i++) {
                    BigInteger a = l.abs(), b = args.get(i).abs();
                    if (a.equals(BigInteger.ZERO) || b.equals(BigInteger.ZERO)) {
                        l = BigInteger.ZERO;
                    } else {
                        l = a.divide(a.gcd(b)).multiply(b);
                    }
                }
                return l;
            }
            case "pow": {
                if (args.size() != 2) throw new IllegalArgumentException("pow expects 2 args, got " + args.size());
                BigInteger a = args.get(0);
                int b = args.get(1).intValueExact();
                if (b < 0) throw new IllegalArgumentException("pow exponent must be >= 0");
                return a.pow(b);
            }
            default:
                throw new IllegalArgumentException("Unsupported function: " + name);
        }
    }

    static int extractInt(String s, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(s);
        if (!m.find()) throw new IllegalArgumentException("Could not find pattern: " + regex);
        return Integer.parseInt(m.group(1));
    }

    // ---------------- Math / interpolation ----------------

    static class Point {
        final BigInteger x, y;
        Point(BigInteger x, BigInteger y) { this.x = x; this.y = y; }
    }

    static void combine(List<Point> s, int k, int idx, List<Point> cur,
                        Map<Fraction,Integer> cnt, int[] tot) {
        if (cur.size() == k) {
            tot[0]++;
            Fraction c = interpConstant(cur);
            cnt.put(c, cnt.getOrDefault(c, 0) + 1);
            return;
        }
        for (int i = idx; i <= s.size() - (k - cur.size()); i++) {
            cur.add(s.get(i));
            combine(s, k, i + 1, cur, cnt, tot);
            cur.remove(cur.size() - 1);
        }
    }

    /** Lagrange interpolation: compute f(0) exactly using rationals. */
    static Fraction interpConstant(List<Point> pts) {
        Fraction res = Fraction.ZERO;
        int k = pts.size();
        for (int i = 0; i < k; i++) {
            BigInteger xi = pts.get(i).x, yi = pts.get(i).y;

            Fraction li0 = Fraction.ONE; // L_i(0)
            for (int j = 0; j < k; j++) {
                if (j == i) continue;
                BigInteger xj = pts.get(j).x;
                // (0 - xj) / (xi - xj) = (-xj) / (xi - xj)
                li0 = li0.mul(new Fraction(xj.negate(), xi.subtract(xj)));
            }
            res = res.add(li0.mul(new Fraction(yi)));
        }
        return res;
    }

    static void markGood(List<Point> s, int k, Fraction target,
                         int idx, List<Point> cur, Set<BigInteger> good) {
        if (cur.size() == k) {
            if (interpConstant(cur).equals(target)) {
                for (Point p : cur) good.add(p.x);
            }
            return;
        }
        for (int i = idx; i <= s.size() - (k - cur.size()); i++) {
            cur.add(s.get(i));
            markGood(s, k, target, i + 1, cur, good);
            cur.remove(cur.size() - 1);
        }
    }

    // ---------------- exact rational type ----------------

    static final class Fraction {
        static final Fraction ZERO = new Fraction(BigInteger.ZERO, BigInteger.ONE);
        static final Fraction ONE  = new Fraction(BigInteger.ONE,  BigInteger.ONE);

        final BigInteger num; // normalized
        final BigInteger den; // > 0

        Fraction(BigInteger n) { this(n, BigInteger.ONE); }

        Fraction(BigInteger n, BigInteger d) {
            if (d.equals(BigInteger.ZERO)) throw new ArithmeticException("denominator 0");
            if (d.signum() < 0) { n = n.negate(); d = d.negate(); }
            BigInteger g = n.gcd(d);
            if (!g.equals(BigInteger.ONE)) { n = n.divide(g); d = d.divide(g); }
            this.num = n; this.den = d;
        }

        Fraction add(Fraction o) {
            return new Fraction(this.num.multiply(o.den).add(o.num.multiply(this.den)),
                                this.den.multiply(o.den));
        }

        Fraction mul(Fraction o) {
            return new Fraction(this.num.multiply(o.num), this.den.multiply(o.den));
        }

        boolean isInteger() { return den.equals(BigInteger.ONE); }
        BigInteger toBigInteger() {
            if (!isInteger()) throw new ArithmeticException("Not an integer: " + this);
            return num;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Fraction)) return false;
            Fraction f = (Fraction) o;
            return num.equals(f.num) && den.equals(f.den);
        }
        @Override public int hashCode() { return Objects.hash(num, den); }
        @Override public String toString() {
            return isInteger() ? num.toString() : (num + "/" + den);
        }
    }
}
