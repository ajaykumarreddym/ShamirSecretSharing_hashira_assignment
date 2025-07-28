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
import java.util.Set;

import org.json.JSONObject;

public class ShamirSecret {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java ShamirSecret <input.json>");
            System.exit(1);
        }

        // Read JSON input
        String content = Files.readString(Paths.get(args[0]));
        JSONObject root = new JSONObject(content);
        JSONObject keys = root.getJSONObject("keys");
        int n = keys.getInt("n");
        int k = keys.getInt("k");

        // Decode shares
        List<Point> shares = new ArrayList<>();
        for (String key : root.keySet()) {
            if (key.equals("keys")) continue;
            JSONObject entry = root.getJSONObject(key);
            int x = Integer.parseInt(key);
            int base = entry.getInt("base");
            String val = entry.getString("value");
            BigInteger y = new BigInteger(val, base);
            shares.add(new Point(BigInteger.valueOf(x), y));
        }

        // Compute all combinations of size k
        Map<BigInteger, Integer> counts = new HashMap<>();
        int[] totalCombos = {0};
        combine(shares, k, 0, new ArrayList<>(), counts, totalCombos);

        // Determine top secret
        var top = Collections.max(counts.entrySet(), Map.Entry.comparingByValue());
        BigInteger secret = top.getKey();
        int frequency = top.getValue();

        // Detect bad shares
        Set<BigInteger> goodXs = new HashSet<>();
        markGood(shares, k, secret, 0, new ArrayList<>(), goodXs);
        List<BigInteger> badXs = new ArrayList<>();
        for (var p : shares)
            if (!goodXs.contains(p.x)) badXs.add(p.x);
        Collections.sort(badXs);

        // Print results
        System.out.println("Total combinations tried: " + totalCombos[0]);
        System.out.println("Secret (constant term c): " + secret);
        System.out.print("Bad shares detected at x:");
        System.out.println(badXs.isEmpty() ? " None" : " " + badXs);
        System.out.println("Top secret frequency: " + frequency);
    }

    static class Point { BigInteger x, y; Point(BigInteger x, BigInteger y) { this.x=x; this.y=y; } }

    static void combine(List<Point> s, int k, int idx, List<Point> cur,
                        Map<BigInteger,Integer> cnt, int[] tot) {
        if (cur.size() == k) {
            tot[0]++;
            BigInteger c = interp(cur);
            cnt.put(c, cnt.getOrDefault(c,0)+1);
            return;
        }
        for (int i = idx; i <= s.size() - (k - cur.size()); i++) {
            cur.add(s.get(i));
            combine(s, k, i+1, cur, cnt, tot);
            cur.remove(cur.size()-1);
        }
    }

    static BigInteger interp(List<Point> pts) {
        BigInteger res = BigInteger.ZERO;
        int k = pts.size();
        for (int i=0; i<k; i++) {
            BigInteger xi = pts.get(i).x, yi = pts.get(i).y;
            BigInteger num = BigInteger.ONE, den = BigInteger.ONE;
            for (int j=0; j<k; j++) {
                if (j==i) continue;
                BigInteger xj = pts.get(j).x;
                num = num.multiply(xj.negate());
                den = den.multiply(xi.subtract(xj));
            }
            res = res.add(yi.multiply(num).divide(den));
        }
        return res;
    }

    static void markGood(List<Point> s, int k, BigInteger target,
                         int idx, List<Point> cur, Set<BigInteger> good) {
        if (cur.size() == k) {
            if (interp(cur).equals(target))
                for (var p : cur) good.add(p.x);
            return;
        }
        for (int i=idx; i <= s.size() - (k - cur.size()); i++) {
            cur.add(s.get(i));
            markGood(s, k, target, i+1, cur, good);
            cur.remove(cur.size()-1);
        }
    }
}