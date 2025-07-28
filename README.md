# Hashira Assessment — Shamir Secret Recovery

Recover the **secret** (the polynomial constant term `c = f(0)`) from Shamir-style shares.  
This solution is a **single Java file** with a **tolerant JSON reader** (no external libraries) and **exact rational** Lagrange interpolation. It also detects **bad shares**.

---

## 1) Problem Statement

You are given:

- `n`: total number of provided shares.
- `k`: threshold; the hidden polynomial degree is `m = k - 1`.
- `n` share entries where each top-level **numeric key** is the `x` coordinate and the object contains:
  - `base`: radix of the encoded value (e.g., 2, 8, 10, 16).
  - `value`: either a number encoded in the given `base`, or a small **function expression** (see below).

**Goal**

1. Parse all shares `(x, y)`.
2. Consider **all** `k`-sized combinations of shares.
3. For each combination, reconstruct `f(0)` using **Lagrange interpolation** with exact rational arithmetic.
4. Choose the **most frequent** `f(0)` value as the **secret**.
5. Mark **bad shares**: shares that are **never** part of any combination yielding the chosen secret.

**Output format**

Total combinations tried: <C>
Secret (constant term c): <secret>
Bad shares detected at x: <list or 'None'>
Top secret frequency: <F>



---

## 2) Project Structure


├── src/com/example/ShamirSecret.java # Single, self-contained source file
├── input.json # Test case 1
├── input2.json # Test case 2
└── bin/ # Compiled classes (created by javac)



---

## 3) Build

Requires **JDK 17+**.

```bash
javac -d bin src/com/example/ShamirSecret.java


Check Java availability:

java -version
javac -version



4) Run

With input.json
java -cp bin com.example.ShamirSecret input.json

With input2.json
java -cp bin com.example.ShamirSecret input2.json

-----

5) Input Format
{
  "keys": { "n": <int>, "k": <int> },

  "<x1>": { "base": <radix>, "value": "<numberOrFunction>" },
  "<x2>": { "base": <radix>, "value": "<numberOrFunction>" }

  // ... n total entries
}
Top-level numeric keys ("1", "2", …) are the x coordinates.

The parser is order-insensitive and tolerant of spacing and quotes.

base may be quoted or unquoted; value may be quoted or a bare token.

Supported value functions
If value looks like a function (name(args)), it is evaluated with big integers, with all numbers interpreted in base‑10:

sum(a, b, c, ...) / add(...)

mul(a, b, c, ...) / prod(...)

gcd(a, b, ...)

lcm(a, b, ...)

pow(a, b) — exponent b >= 0

Functions can be nested, e.g. sum(10, mul(2,3)).

If value is not a function, it is parsed using the provided base.


------


6) Sample Runs & Results
These are the exact results observed on the provided test files.

✅ Test Case 1 — input.json
Command:
java -cp bin com.example.ShamirSecret input.json
Output:


Total combinations tried: 4
Secret (constant term c): 3
Bad shares detected at x: None
Top secret frequency: 4
Explanation: nCk = 4 combinations and all agree on c = 3. No bad shares.



✅ Test Case 2 — input2.json
Command:

java -cp bin com.example.ShamirSecret input2.json
Output:

Total combinations tried: 120
Secret (constant term c): 79836264049851
Bad shares detected at x: [8]
Top secret frequency: 36



7) Method & Rationale
Parsing: Lightweight, regex-based reader (no external org.json). Tolerant of order, whitespace, and quoting.

Function evaluation: Simple evaluator for basic arithmetic functions with BigInteger.

Interpolation: Exact rational arithmetic (Fraction type) prevents rounding errors:
 
Consensus: Count frequency of each f(0). Choose the mode (most frequent value).

Bad shares: A share is “good” if it occurs in any combination producing the chosen secret; else it is reported as bad.