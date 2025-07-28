package com.example;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShamirSecret {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java com.example.ShamirSecret <input.json>");
            System.exit(1);
        }

        String json = Files.readString(Paths.get(args[0]));

        // --- parse n and k ---
        int n = extractInt(json, "\"keys\"\\s*:\\s*\\{[^}]*?\"n\"\\s*:\\s*(\\d+)");
        int k = extractInt(json, "\"keys\"\\s*:\\s*\\{[^}]*?\"k\"\\s*:\\s*(\\d+)");

        // --- parse shares: "x": {"base": B, "value": "V"} ---
        List<Point> shares = new ArrayList<>();
        Pattern pShare = Pattern.compile(
                "\"(\\d+)\"\\s*:\\s*\\{\\s*\"base\"\\s*:\\s*(\\d+)\\s*,\\s*\"value\"\\s*:\\s*\"([^\"]+)\"\\s*\\}",
                Pattern.DOTALL);
        Matcher m = pShare.matcher(json);
        while (m.find()) {
            String xStr = m.group(1);
            // Ensure we don't accidentally parse the "keys" object (it’s not numeric anyway)
            int base = Integer.parseInt(m.group(2));
            String val = m.group(3);
            BigInteger x = new BigInteger(xStr);
            BigInteger y = new BigInteger(val, base);
            shares.add(new Point(x, y));
        }

        if (shares.size() != n) {
            System.err.println("Warning: parsed shares (" + shares.size() + ") != n (" + n + ")");
        }

        // --- enumerate all k-combinations and vote on the constant term ---
        Map<Fraction, Integer> counts = new HashMap<>();
        int[] totalCombos = {0};
        combine(shares, k, 0, new ArrayList<>(), counts, totalCombos);

        Map.Entry<Fraction, Integer> top = counts.entrySet()
                .stream().max(Map.Entry.comparingByValue()).orElseThrow();

        Fraction secret = top.getKey();
        int frequency = top.getValue();

        // --- detect bad shares (those never appearing in any combo yielding the chosen secret) ---
        Set<BigInteger> goodXs = new HashSet<>();
        markGood(shares, k, secret, 0, new ArrayList<>(), goodXs);

        List<BigInteger> badXs = new ArrayList<>();
        for (Point pnt : shares) if (!goodXs.contains(pnt.x)) badXs.add(pnt.x);
        Collections.sort(badXs);

        // --- print ---
        System.out.println("Total combinations tried: " + totalCombos[0]);
        System.out.println("Secret (constant term c): " + (secret.isInteger() ? secret.toBigInteger() : secret));
        System.out.print("Bad shares detected at x:");
        System.out.println(badXs.isEmpty() ? " None" : " " + badXs);
        System.out.println("Top secret frequency: " + frequency);
    }

    // ---- helpers ----

    static int extractInt(String s, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(s);
        if (!m.find()) throw new IllegalArgumentException("Could not find pattern: " + regex);
        return Integer.parseInt(m.group(1));
    }

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

    /**
     * Lagrange interpolation to compute f(0) (constant term) exactly using rationals.
     */
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

    // ---- exact rational type ----
    static final class Fraction {
        static final Fraction ZERO = new Fraction(BigInteger.ZERO, BigInteger.ONE);
        static final Fraction ONE  = new Fraction(BigInteger.ONE,  BigInteger.ONE);

        final BigInteger num; // normalized
        final BigInteger den; // > 0

        Fraction(BigInteger n) { this(n, BigInteger.ONE); }

        Fraction(BigInteger n, BigInteger d) {
            if (d.equals(BigInteger.ZERO)) throw new ArithmeticException("denominator 0");
            // normalize sign
            if (d.signum() < 0) { n = n.negate(); d = d.negate(); }
            // reduce
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
