# Starscript
Fast text formatting language for Java. This is Jinx's fork of [MeteorDevelopment/starscript](https://github.com/MeteorDevelopment/starscript), vendored as sources and built for Java 25 with Lombok.

- Lightweight with no dependencies
- Faster than `String.format` (See [Benchmark](https://github.com/MeteorDevelopment/starscript/blob/master/src/test/java/org/meteordev/starscript/Benchmark.java))
- Standard operators + - * / % ^
- Bitwise operators & | ~ << >> >>> ^^
- Ability to call functions defined in java
- Variables can be different each time they are used
- Conditional output (ternary operator)
- Variables can be maps

## Examples
- `Hello {name}!`
- `Number: {someNumber * 100}`
- `FPS: {round(fps)}`
- `Today is a {good ? 'good' : 'bad'} day`
- `Name: {player.name}`

## Usage
Java:
```java
// Parse
Parser.Result result = Parser.parse("Hello {name}!");

// Check for errors
if (result.hasErrors()) {
    for (Error error : result.errors) System.out.println(error);
    return;
}

// Compile
Script script = Compiler.compile(result);

// Create starscript instance
Starscript ss = new Starscript();
StandardLib.init(ss); // Adds a few default functions, not required

ss.set("name", "MineGame159");
// ss.set("name", () -> Value.string("MineGame159"));

// Run
System.out.println(ss.run(script)); // Hello MineGame159!
```

## Language
Operator precedence, lowest to highest:

| Level | Operators |
|---|---|
| 1 | `?:` |
| 2 | `or` |
| 3 | `and` |
| 4 | `==` `!=` |
| 5 | `>` `>=` `<` `<=` |
| 6 | `\|` |
| 7 | `^^` (xor) |
| 8 | `&` |
| 9 | `<<` `>>` `>>>` |
| 10 | `+` `-` |
| 11 | `*` `/` `%` |
| 12 | unary `!` `-` `~` |
| 13 | `^` (power, right-associative, right operand is a unary expression) |

So `2*3^2` is 18, `2^3^2` is 512, `-2^2` is -4, `2^-1` is 0.5.

- `-` is never part of a number literal: `{5-3}` is 2, `{-3}` is unary negation.
- In plain text `{{` produces a literal `{` and `##` a literal `#`.
- In string literals a backslash escapes the next character literally (`\'`, `\"`, `\\`). A trailing backslash is a parse error.
- Section indices (`#N`) and call argument counts are limited to 255; exceeding that is a parse error. Parsing never throws.
- Whole numbers within `long` range print without a decimal point (`3000000000`, `-0.0` prints `0`); others use `Double.toString`.
- A missing variable or field prints `null`: `{a}`, `{a.b}`, `{a.b.c}`.
- Any expression can be called: `{f()()}`.
- `Compiler.compile` throws `StarscriptError` when a script has more than 65536 distinct constants or a jump longer than 65535 bytes.

Upstream syntax documentation is on the [wiki](https://github.com/MeteorDevelopment/starscript/wiki).
