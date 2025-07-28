# Shamir Secret (Hashira Assessment)

A small Java program to recover the **secret** (constant term `c = f(0)`) of a polynomial from its Shamir shares.  
No external JSON libraries are used — a tolerant regex parser reads the input file.

---

## Requirements

- JDK 17+ (tested on 17/21)
- macOS / Linux / Windows shell

Project layout:

.
├── src/com/example/ShamirSecret.java
├── input.json
├── input2.json
└── bin/ # created on first compile

yaml
Copy
Edit

---

## Build

```bash
javac -d bin src/com/example/ShamirSecret.java
If javac is not found, ensure JDK is installed and on PATH.
Check versions:

bash
Copy
Edit
java -version
javac -version
Run
bash
Copy
Edit
java -cp bin com.example.ShamirSecret input.json
# or
java -cp bin com.example.ShamirSecret input2.json
Example output
yaml
Copy
Edit
Total combinations tried: 6
Secret (constant term c): 42
Bad shares detected at x: None
Top secret frequency: 6
If some shares are inconsistent, you may see a rational:

less
Copy
Edit
Secret (constant term c): 123456789/2
Bad shares detected at x: [3, 7]
This means most valid k‑combinations agree on that fractional result.
(If your task requires the secret to be an integer, treat such cases as data errors, or filter to integer results only.)

Input format
The input is a JSON object with:

keys.n – total provided shares

keys.k – threshold (degree m = k-1)

Then n entries where each top‑level numeric key is the share’s x, and its value is an object:

json
Copy
Edit
{
  "keys": { "n": 4, "k": 2 },
  "1": { "base": 10, "value": "47" },
  "2": { "base": 10, "value": "52" },
  "3": { "base": 10, "value": "57" },
  "4": { "base": 10, "value": "62" }
}
The parser is tolerant about spacing and the order of fields.
base may be quoted or not. value may be quoted or a bare token.

Function values
value may also be an expression. Supported functions:

sum(a, b, c, ...) / add(...)

mul(a, b, c, ...) / prod(...)

gcd(a, b, ...)

lcm(a, b, ...)

pow(a, b) (non‑negative integer b)

Functions can be nested, e.g. sum(10, mul(2,3))

All numbers in functions are parsed as base‑10.
If value is not a function, it is parsed in the specified base.

Example:

json
Copy
Edit
{
  "keys": { "n": 3, "k": 2 },
  "1": { "base": 10, "value": "sum(40,7)" },
  "2": { "base": 10, "value": "52" },
  "3": { "base": 16, "value": "39" }   // 0x39 = 57
}
What the program prints
Total combinations tried — number of k-sized combinations tested.

Secret (constant term c) — f(0) derived via exact rational Lagrange interpolation.

Bad shares detected at x — share x positions that never participate in any combination producing the chosen secret.

Top secret frequency — how many combinations yielded the printed secret (useful to see consensus strength).

Algorithm (high level)
Parse n, k, and all shares (x, y).

Iterate over all size‑k combinations of shares.

For each combination, compute f(0) with Lagrange interpolation:

𝑓
(
0
)
=
∑
𝑖
=
0
𝑘
−
1
𝑦
𝑖
∏
𝑗
≠
𝑖
0
−
𝑥
𝑗
𝑥
𝑖
−
𝑥
𝑗
f(0)= 
i=0
∑
k−1
​
 y 
i
​
  
j

=i
∏
​
  
x 
i
​
 −x 
j
​
 
0−x 
j
​
 
​
 
This implementation uses an exact rational Fraction type to avoid precision loss.

Count frequencies of resulting f(0) values, choose the most frequent.

Mark any share that appears in at least one agreeing combination as good; the rest are bad.

Complexity:

(
𝑛
𝑘
)
( 
k
n
​
 )
combinations; each interpolation is 
𝑂
(
𝑘
2
)
O(k 
2
 ). Works well for the small n typical of assessments.

Troubleshooting
“Warning: parsed shares (0) != n (…)"
The parser didn’t recognize your shares. Check that top-level keys are numeric ("1", "2", …) and each has base and value. If you use different function names or nested structures, share a sample and adapt the parser.

IllegalArgumentException: Could not find pattern
keys.n or keys.k missing or malformed.

Fractional secret but you expect integer
Input likely contains one or more wrong shares. You can post‑filter to integer results only, or increase k consensus logic. Ask if you want an “integer-only” mode.

VS Code Run Config (optional)
Create .vscode/launch.json:

json
Copy
Edit
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Run ShamirSecret (input.json)",
      "request": "launch",
      "mainClass": "com.example.ShamirSecret",
      "classPaths": ["bin"],
      "args": "input.json",
      "preLaunchTask": "javac build"
    }
  ]
}
And .vscode/tasks.json:

json
Copy
Edit
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "javac build",
      "type": "shell",
      "command": "javac -d bin src/com/example/ShamirSecret.java",
      "problemMatcher": ["$javac"]
    }
  ]
}
Then Run and Debug → Run ShamirSecret (input.json).