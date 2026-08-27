# Phase 0 + Phase 1 verification report

Scope: the character-scanning layer of ktoml, verified with the SnaKt formver compiler plugin
under the plan in `docs/verification/plan.md` (Phase 0 foundation, Phase 1 character layer), plus
the first functions specified in place on ktoml's shipping code (the start of Phase 2).
This report states what is proved, what is not, and what the exercise says about SnaKt.

## 1. What is proved

**56 functions carry machine-checked specifications.** 50 are *mirrors*: exception-free
reimplementations of ktoml functions in
`ktoml-core/src/commonMain/kotlin/com/akuleshov7/ktoml/verified/`, across five files. 6 are ktoml's
own shipping functions, specified in place (§1.3). All are annotated `@AlwaysVerify` and re-verified
on every `:ktoml-core:compileKotlinJvm`. The build fails if any proof fails.

The mirror properties fall into four kinds:

- **Totality and index safety.** On arbitrary input — including malformed and adversarial TOML —
  these functions raise no exception and never index outside a string. This is the property that
  covers the whole set, and it is the one that matters most: the class of bug it rules out is
  exactly the one already found in ktoml's own `isLineEndingBackslash`, which reads two characters
  past the end of its input.
- **Index meaning.** Every searching function returns an index that lies in the range its callers
  assume, points at the character its callers expect, and returns `-1` only when the thing searched
  for is genuinely absent. Callers of `indexOfNextOutsideQuotes`, `nextKeyPartEnd`,
  `firstBadEscape` and `firstInvalidBareKeyChar` rely on all three of these without stating them
  anywhere; here they are stated and checked.
- **Slice content.** Every `trim*` function's result is proved to be a contiguous slice of its
  input, character for character, with the expected length — not merely "some string no longer than
  the input".
- **Length preservation in `replaceEscaped`.** The single load-bearing proof in the set.
  `indexOfNextOutsideQuotes` indexes into `replaceEscaped`'s output and then uses those indices
  against the *original* string. That is sound only if the rewrite preserves length position for
  position. It does, and now that is proved rather than assumed.

### 1.1 What this does not say

The honesty rules from `plan.md` §5 apply in full, and none of them is a footnote:

- **Partial correctness only.** SnaKt has no termination check and no `decreases` clause. Every
  statement above reads "*if* the function returns, the result satisfies its specification." For a
  parser fed hostile input, non-termination is itself a denial-of-service bug, and nothing here
  rules it out. This is plan gap G9.
- **Mathematical integers.** Viper's `Int` is unbounded; Kotlin's is not. Every index-arithmetic
  proof holds under a semantics ktoml does not implement. For string lengths the gap is almost
  certainly benign, but it is not *proved* benign — no length-bound precondition is stated
  anywhere. This is plan gap G4.
- **The mirrors are not the shipping code.** This is the most important limit. The mirrors were
  written against a SnaKt build that could not handle `throw` (plan gap G2), so ktoml's validators —
  which signal failure by throwing `ParseException` — were rewritten into index-returning or
  predicate form before they could be specified. The mirrors are separate reimplementations of
  upstream logic, living in their own source set, called by nothing in ktoml's parser. Of ktoml's
  shipping code, **only the six in-place functions in §1.3 are verified**, and their contracts are
  narrower than the mirrors'. The pinned SnaKt build now converts `throw`, so moving the mirrored
  properties onto the shipping functions is the main open task (§8).
- **One platform.** Verification runs on the JVM target. The `expect`/`actual` pairs are
  unverified and the proofs say nothing about the JS, Wasm, Native or iOS builds.
- **No claim about TOML.** Nothing in this phase mentions the TOML grammar. "ktoml parses TOML
  correctly" is not proved, not approached, and not claimable.

### 1.2 The headline metric

Because the mirrors are not the shipping code, line counts of "verified code" would be
self-flattering. The honest headline number is the **mirror count**: how many distinct upstream
ktoml functions have a specified, drift-checked counterpart.

| metric | count |
|---|---|
| upstream ktoml functions mirrored | 28 |
| shipping ktoml functions specified in place | 6 |
| upstream files covered | 2 (`parsers/StringUtils.kt`, `utils/SpecialCharacters.kt`) plus class members of `parsers/TomlMultilineString.kt` and `writers/IntegerRepresentation.kt` |
| verified functions (mirrors + hand-spelled primitives) | 50 |
| drift-manifest entries in `docs/verification/mirrors.txt` | 31 |
| negative controls and probes | 97 |
| lines of specified mirror code | 1,632 |

The gap between 28 and 50 is hand-spelled stdlib primitives — `slice` for `substring`,
`isTomlWhitespace` for `Char.isWhitespace`, `matchesAt` for `regionMatches` — written out longhand
because the stdlib versions convert but carry no axioms, so a proof leaning on one would learn
nothing. That is the house rule from `plan.md` §5, and this audit confirms it is followed without
exception: no mirror body contains a single call to `substring`, `split`, `replace`, `indexOf`,
`isWhitespace`, `padStart`, `toInt`, `Regex`, `StringBuilder` or `CharCategory`. Mirror bodies use
only `.length`, index access, and calls to other verified functions.

The in-place functions do not follow this rule, because they are ktoml's code as shipped. They call
`replace`, `substring`, `toInt` and `StringBuilder.append`, and the proofs learn nothing about what
those calls return. Their contracts are correspondingly narrow (§1.3).

### 1.3 Functions specified in place

The pinned SnaKt build (`0.1.0-1f8c164a`) converts `throw` as an exit from the function. A
postcondition then describes the paths that return normally, and a `throw` path is not checked
against it. That is enough to put contracts directly on six shipping functions:

| shipping function | file | properties proved |
| --- | --- | --- |
| `levenshteinDistance` | `utils/Utils.kt` | result between 0 and `first.length + second.length`; no out-of-bounds array access in the two-row DP |
| `String.convertSpecialCharacters` | `utils/SpecialCharacters.kt` | an input with no backslash is returned unchanged, character for character |
| `StringBuilder.appendEscapedUnicode` | `utils/SpecialCharacters.kt` | on return, the result is 4 or 8 and the escape fits inside the input |
| `String.escapeBackslashes` (private) | `utils/SpecialCharacters.kt` | result length is twice the backslash count, or one less for a line-ending backslash in a multiline string |
| `checkCountOfOtherUnescapedQuotes` (private) | `tree/nodes/pairs/values/TomlBasicString.kt` | on return, the input is returned unchanged |
| `checkCountOfOtherQuotes` (private) | `tree/nodes/pairs/values/TomlLiteralString.kt` | on return, the input is returned unchanged |

Specifying these required small changes to shipping code. None changes behaviour on JVM:

- `appendCodePointCompat` returns `Unit` instead of its `StringBuilder` receiver, on every platform,
  and is annotated `@Unique @Borrowed`. The JVM `actual` appends one or two `Char`s instead of
  calling `appendCodePoint`, because SnaKt treats an unspecified Java method as consuming its
  receiver.
- `appendEscapedUnicode` rejects a code point outside `0..0x10FFFF` with an explicit check instead
  of catching `IllegalArgumentException`, because SnaKt rejects `try` in a function that owns its
  receiver. On JS and Wasm this is a behaviour change: their `actual`s declare
  `MAX_CODE_POINT = 0x10FFFFf` (one `f` too many), so `\U00110000` and above were appended as
  invalid surrogate pairs instead of rejected. The explicit check now rejects them on every platform.
  This was found by reading the code, not by a proof.
- `levenshteinDistance` swaps its rows through a local instead of `.also { }`, and allocates the
  second row without an initialiser lambda.
- The two quote-count checks pass `ignoreCase = false` to `replace` explicitly.

The in-place functions have no negative controls yet: none of the patches in `verification/mutants/`
targets them. Their proofs are checked by the build but not yet shown to be non-vacuous.

## 2. Function and property table

### Lexing.kt

| mirror function | upstream ktoml function | properties proved | controls |
| --- | --- | --- | --- |
| isTomlWhitespace | — | equals disjunction of space/tab/CR/LF | vacuity only |
| isLineEndingBackslash | `String.isLineEndingBackslash` | (no postconditions block; behavior only) | 1 mutant (off-by-one), vacuity, precondition |
| nextKeyPartEnd | scanning half of `String.splitKeyToTokens` | result in range, and is a dot if before end | 1 mutant (off-by-one), vacuity, precondition |
| countChar | `count { it == c }` inside `String.validateQuotes` | count is between 0 and string length | vacuity only |
| hasBalancedQuotes | `String.validateQuotes` | (no postconditions block) | vacuity only |
| isLetterOrDigit | `Char.isLetterOrDigit` | equals letter-or-digit range check | vacuity only |
| firstInvalidBareKeyChar | `String.validateSymbols` | result in range; found char is disallowed bare-key char | 1 mutant (dropped guard), 1 mutant (sentinel), vacuity |
| escapingDisabled | `isEscapingDisabled` inside `String.replaceEscaped` | (no postconditions block) | vacuity only |
| indexOfNextOutsideQuotes | `String.indexOfNextOutsideQuotes` | result in range, matches search char, -1 means absent when unquoted | 1 mutant (dropped guard), 1 mutant (off-by-one), vacuity, precondition |
| hexDigitValue | digit half of `hexCode.toInt(HEX_RADIX)` in `appendEscapedUnicode` | value bounded; -1 iff not a hex digit | 1 mutant (sentinel), vacuity |
| firstBadEscape | escape walk of `String.convertSpecialCharacters` + length check of `appendEscapedUnicode` | result in range; found position is a backslash | 1 mutant (dropped guard), 1 mutant (sentinel), vacuity |
| slice | — | result length and characters match source substring | vacuity only |
| takeBeforeComment | `String.takeBeforeComment` | result no longer than input | vacuity only |

### Trims.kt

| mirror function | upstream ktoml function | properties proved | controls |
| --- | --- | --- | --- |
| hasPrefix | `String.startsWith(prefix)` | equals prefix-match definition | 1 mutant (off-by-one), vacuity |
| hasSuffix | `String.endsWith(suffix)` | equals suffix-match definition | 1 mutant (off-by-one), vacuity |
| lastIndexOfChar | `String.lastIndexOf(Char)` | result is last occurrence or -1 iff absent | 1 mutant (off-by-one), 1 mutant (sentinel), vacuity |
| allEqualFrom | — | equals "all chars from index equal c" | 1 mutant (off-by-one), vacuity, precondition |
| trimSymbols | `trimSymbols` helper of the quote/brace/bracket trims | strips prefix/suffix when both match, else unchanged | 1 mutant (dropped guard), vacuity |
| trimSingleQuotes | `String.trimSingleQuotes` | strips single quotes when wrapped, else unchanged | vacuity only |
| trimQuotes | `String.trimQuotes` | strips double quotes when wrapped, else unchanged | vacuity only |
| trimCurlyBraces | `String.trimCurlyBraces` | strips curly braces when wrapped, else unchanged | vacuity only |
| trimBrackets | `String.trimBrackets` | strips brackets when wrapped, else unchanged | vacuity only |
| trimDoubleBrackets | `String.trimDoubleBrackets` | strips double brackets when wrapped, else unchanged | 1 mutant (content), vacuity, precondition |
| isNotQuoted | `String.isNotQuoted` | equals negated quote-wrapped check; single quote char is quoted | vacuity only |
| trimMultilineQuotes | private `String.trimMultilineQuotes(quotes)` helper | result no longer than input; unchanged unless wrapped in quotes | vacuity only |
| matchesAt | — | equals substring match at fixed offset | vacuity only, precondition |
| getCountOfOccurrencesOfSubstring | `String.getCountOfOccurrencesOfSubstring` | non-negative, bounded by length ratio (WEAKENED: dropped "zero iff no occurrence anywhere") | 1 mutant (sentinel), vacuity |
| isKotlinWhitespace | stand-in for `Char.isWhitespace()` | equals the character set `Char.isWhitespace()` accepts | vacuity only |
| startsWithIgnoreAllWhitespaces | `String.startsWithIgnoreAllWhitespaces` | (no postconditions block; WEAKENED: no independent characterization provable) | 1 mutant (off-by-one), vacuity |
| convertLineEndingBackslash | `String.convertLineEndingBackslash` | result no longer than input; unchanged if no backslash-newline pair (WEAKENED: dropped exact-prefix claim) | 1 mutant (dropped guard), vacuity |
| trimComment | `String.trimComment` | result no longer than input (WEAKENED: dropped "no unquoted # remains") | 1 mutant (off-by-one), vacuity |

### Escapes.kt

| mirror function | upstream ktoml function | properties proved | controls |
| --- | --- | --- | --- |
| isControlChar | `Char.isControlChar` | equals control-codepoint test excluding tab | vacuity only |
| isMultilineControlChar | `Char.isMultilineControlChar` | equals isControlChar excluding newline/CR | vacuity only |
| hexDigitChar | inverse of hexDigitValue | maps each value 0-15 to its hex digit char | vacuity, precondition |
| escapeControlChar | `Char.escapeControlChar` | named escapes map to 2-char form, others to 6-char \u form | vacuity only |
| repeatChar | `"c".repeat(n)` | result length n, all chars equal c | vacuity, precondition |
| escapeBackslashes | private `escapeBackslashes` helper of `escapeControlChars` | result length doubles slash count (or minus one); all chars backslash | vacuity, precondition |
| escapeControlChars | `String.escapeControlChars` | output no shorter than input (WEAKENED: dropped "no control character in output") | vacuity only |
| quoteEscapeSingle | quote-escaping half of `String.escapeSpecialCharacters` non-multiline branch | output no shorter than input (WEAKENED: dropped control-char-freedom preservation) | vacuity only |
| quoteEscapeTriple | quote-doubling half of `String.escapeSpecialCharacters` multiline branch | output no shorter than input (WEAKENED: dropped control-char-freedom preservation) | vacuity only |
| escapeSpecialCharacters | `String.escapeSpecialCharacters` | output no shorter than input (WEAKENED: dropped "no control char of selected flavor") | vacuity only |
| appendEscapedUnicode | `StringBuilder.appendEscapedUnicode` | total; result is -1 or valid hex-digit run length matching marker | 1 mutant (dropped guard), 1 mutant (sentinel), vacuity, precondition |
| replaceTwoCharSequence | private 2-char scan helper inside `String.replaceEscaped` | result length equals input length | vacuity only |
| replaceEscaped | `String.replaceEscaped` | result always exactly as long as input | vacuity only |

### Multiline.kt

| mirror function | upstream ktoml function | properties proved | controls |
| --- | --- | --- | --- |
| isNextThreeQuotes | private `String.isNextThreeQuotes` helper of `trackMultilineString` | equals three-consecutive-quote-char check at index | 1 mutant (off-by-one), vacuity, precondition |
| indexOfCharFrom | `String.indexOf(Char, Int)` | result is first occurrence from index or -1 iff absent | 1 mutant (dropped guard), 1 mutant (off-by-one), vacuity, precondition |
| hasTwoConsecutiveSymbolsIgnoreWhitespaces | private `hasTwoConsecutiveSymbolsIgnoreWhitespaces` | false (never null) when symbol absent entirely (WEAKENED: dropped full null-iff characterization) | 1 mutant (dropped guard), 1 mutant (sentinel), vacuity |
| allWhitespaceBetween | `.all { it.isWhitespace() }` calls inside hasTwoConsecutiveSymbolsIgnoreWhitespaces | equals "all chars in range are whitespace" | 1 mutant (dropped guard), vacuity |

### IntegerRepr.kt

| mirror function | upstream ktoml function | properties proved | controls |
| --- | --- | --- | --- |
| integerRepresentationRadix | `IntegerRepresentation.radix` | exhaustive tag-to-radix mapping; radix always >= 2 | 1 mutant (content), vacuity, precondition |
| integerRepresentationPrefix | `IntegerRepresentation.prefix` | exhaustive tag-to-prefix mapping; prefix length is 0 or 2 | 1 mutant (content), vacuity, precondition |

## 3. Audit against the plan

Every property `plan.md` §1.2 claimed as reachable is either proved or carries an explicit
`WEAKENED:` note in the mirror's doc comment saying what was dropped and why. There are no silent
omissions. Nine functions ship weakened specifications:

| function | dropped | reason |
|---|---|---|
| `escapeControlChars`, `escapeBackslashes`, `escapeSpecialCharacters`, `quoteEscapeSingle`, `quoteEscapeTriple` | "output contains no control character" | the content postcondition does not survive loop-invariant preservation across whole-string appends (plan gap G5) |
| `getCountOfOccurrencesOfSubstring` | "result = 0 iff no occurrence" | needs an existential witness; the spec DSL has `forAll` only |
| `startsWithIgnoreAllWhitespaces` | "agrees with the whitespace-skipping characterisation" | same — needs `exists` |
| `hasTwoConsecutiveSymbolsIgnoreWhitespaces` | full "`null` iff first-occurrence-with-whitespace-tail" characterisation | same, compounded by a plugin crash on the call-based restatement |
| `convertLineEndingBackslash` | "result is a prefix up to the trailing-backslash position" | the plan's property is exact only for a single occurrence; upstream handles many, so the general result is not a prefix. Replaced with a length bound plus the no-op case |
| `trimMultilineQuotes` | standalone idempotence lemma | the property is already the second clause of the function's own postcondition; a restatement would prove nothing new |

Three functions — `isLineEndingBackslash`, `hasBalancedQuotes`, `escapingDisabled` — carry no
postconditions at all. For these the claim is totality and index safety only. That is deliberate
and correct for `isLineEndingBackslash` in particular, whose whole point is the precondition
`0 <= backslashIndex && backslashIndex < s.length` that upstream silently assumed and violated. It
should not be read as a functional-correctness claim.

### 3.1 Negative controls

`plan.md` §1.2 and the Phase 1 control list mandate off-by-one, sentinel and content mutants,
plus a vacuity probe and a precondition-strength probe per function. Present: **97 controls**.

| control kind | count |
|---|---|
| vacuity probes (`verify(false)` at end of body) | 50 |
| precondition-deletion probes | 17 |
| off-by-one mutants | 12 |
| dropped-guard mutants | 8 |
| sentinel mutants | 6 |
| content mutants | 4 |

Every one of the 50 functions has a vacuity probe — full coverage, no exceptions. Every function
that has a precondition has a precondition-deletion probe; the 33 functions without one have no
precondition to delete. This audit found exactly one gap here,
`getCountOfOccurrencesOfSubstring`'s missing precondition probe, and closed it.

**Control depth is uneven, and this is a real limit.** The plan's wording is "minimum 2 per
function *class*", which is met with room to spare. Under the stricter per-function reading it is
not: 6 functions have two behavioural mutants, 18 have one, and 20 are covered by a vacuity probe
alone. The 20 are the trivial predicates and the delegating wrappers (`isControlChar`,
`isTomlWhitespace`, `trimQuotes` and its siblings, `takeBeforeComment`), where a vacuity probe
does most of the work a mutant would — but "most" is not "all", and a reader should know that a
fifth of the set rests on one control. Closing the gap would mean roughly 20 more mutants at one
full verification build each; it is recorded here rather than done.

### 3.2 Drift protection

`scripts/check-mirrors.sh` hashes each mirrored upstream function's source and fails when it
changes, so an upstream edit cannot silently invalidate a proof. Two holes, both documented in
`docs/verification/mirrors.txt` and reproduced here so they are not buried:

- The extractor anchors on column-zero `fun`, so it cannot see class members. `isNextThreeQuotes`
  and `hasTwoConsecutiveSymbolsIgnoreWhitespaces` are private methods of `TomlMultilineString`
  and are **drift-unguarded**. `integerRepresentationRadix`/`Prefix` mirror enum-constructor
  property values, which have no extractable function body — also unguarded.
- `StringUtils.kt` declares two functions named `trimMultilineQuotes`. The extractor matches the
  first (a public no-arg wrapper), not the private one-arg helper the mirror actually reimplements.
  The recorded hash guards the wrapper, not the algorithm.

Four of the 50 mirrors are therefore unprotected against upstream drift. Fixing this needs an
extractor that handles indentation and disambiguates same-named declarations by arity.

## 4. Re-run from clean

This section covers the mirror work. The clean re-run below was done before the in-place functions
of §1.3 were specified; those were verified by the build in the commits that added them, and have
not been through a clean re-run or the control suite.

All four gates were re-run from a clean checkout of this branch. Two of them needed fixing before
they passed; every fix is in this branch.

| gate | result |
|---|---|
| `:ktoml-core:compileKotlinJvm` (the verification build) | pass — no `Viper verification error` in the log |
| `:ktoml-core:jvmTest` | pass — 996 tests, 0 failures, 194 skipped |
| `scripts/check-mirrors.sh` | pass — every mirrored upstream function matches its recorded hash |
| `scripts/run-mutants.sh` | pass — 97 controls, all behaving as declared (91 rejected in the suite run, 1 declared expected-to-verify, 5 re-verified individually after their patch context was refreshed) |

Three things had to be fixed to get there, and all three are worth recording because a green
re-run that hides them would be worthless.

**The compliance suite needed its submodule, and then wanted a baseline entry removed.** In a fresh
worktree the `toml-test` submodule is uninitialized, so three compliance tests failed on a missing
file list. After `git submodule update --init`, one test failed as an XPASS: `TomlTestSuite` fails
deliberately when a case listed as a known failure starts passing, to prompt removal from the
baseline. `valid/spec-1.1.0/common-23.toml` (upstream issue 373, float literals) now parses
correctly under this fork's Kotlin bump. Removed from the baseline, as the failure message
instructs, and recorded in `docs/verification/known-failures.md` as fork divergence #4.

**Four precondition probes verified when they must fail.** This is the substantive finding of the
re-run. The previously recorded control-suite result predates the escaping and multiline chunks, so
those chunks' controls had never actually been executed; running them found four
precondition-deletion probes passing.

That the earlier greens were never verification is confirmed from outside this stage, in the
workflow driver's own command logs: the escaping stage reported "all 79 controls correctly rejected"
about twelve minutes after starting, and the multiline stage reported "all 96 controls correctly
rejected" inside two minutes. At roughly two minutes per verification build, neither number is
physically possible. The same logs show the escaping stage's first attempt exiting 127 because
`scripts/run-suite-report.sh` did not exist — the script its brief called for was never written, so
the graph silently fell back to `run-mutants.sh`. All four shared one shape — every postcondition clause guarded
by an exact-input equality, and a fallback branch in the body (`else -> 10`, `else -> ""`,
`return 'f'`) that satisfies the unconditional clauses too — so deleting the precondition changed
nothing provable. Three were fixed by adding one clause each that the fallback branch violates:
`'f'` is returned only for 15, radix 10 only for `DECIMAL`/`GROUPED`, the empty prefix only for
`DECIMAL`/`GROUPED`. Two doc comments that claimed an exhaustive `when` "with no `else` branch"
were corrected; there is one, now proved unreachable.

The fourth could not be fixed, and the reason is the most interesting single result of this audit.
`getCountOfOccurrencesOfSubstring`'s precondition is `substring.length >= 1`. With an empty
substring, `matchesAt(s, i, "")` holds and the scan advances by `substring.length` — that is, by
zero — so the loop never progresses and every empty-substring path diverges. SnaKt proves partial
correctness only, and a divergent path satisfies every postcondition vacuously. **No postcondition
can be violated on the paths that precondition rules out, because those paths never return.** The
precondition's job is termination, which the tooling cannot check at all (gap G9). It is declared in
`run-mutants.sh`'s `expected_unfalsifiable` list with that reasoning inline. A listed control may
verify or run out of verification time; a proof failure on it is a harness failure, so the exception
cannot quietly widen.

**Five control patches went stale and had to be regenerated.** Adding postcondition clauses moved
the context lines that `mutant-sentinel-getCountOfOccurrencesOfSubstring` and four vacuity probes
anchor on, so `git apply` refused them. `run-mutants.sh` treats a patch that does not apply as a
harness failure rather than a pass, which is why they surfaced instead of silently scoring as
green — the one design decision in this harness that most earned its keep. Regenerated with
context-only changes and re-verified individually; all five are rejected as they should be. The
accounting for the 97 controls is therefore: 91 rejected during the suite run, 1 declared
expected-to-verify, 5 rejected on individual re-runs after regeneration.

That is worth dwelling on, because it generalises. A precondition-strength probe cannot distinguish
"this precondition does no work" from "this precondition's work is termination" as long as the tool
ignores termination. Every precondition in this fork that exists to guarantee progress is invisible
to the probe that is supposed to police it. The plan's §5 warning that mutation testing proves a
specification constrains *something*, not the *right* thing, has a sharper form here: with G9
outstanding, one whole category of precondition cannot be validated by the mandated control at all.

## 5. The two upstream defects

Both defects the plan named were confirmed by writing the specification, which is the useful part
of the exercise: in each case the proof obligation could not be discharged until the bug was
identified.

- **Defect #1 — `String.isLineEndingBackslash` reads past the end of the string.** Upstream indexes
  two characters beyond `backslashIndex` without checking length. The mirror proves the fixed
  version total, and states the precondition upstream silently assumed:
  `0 <= backslashIndex && backslashIndex < s.length`.
- **Defect #2 — `appendEscapedUnicode` throws on malformed input.** Upstream calls
  `hexCode.toInt(HEX_RADIX)` outside its `try`, so a non-hex escape sequence escapes as an uncaught
  `NumberFormatException` rather than a `ParseException`. The mirror reimplements the conversion as
  `hexDigitValue`-driven accumulation and proves it rejects every non-hex input instead of
  throwing.

**Neither fix is applied to ktoml's shipping code, and no issue or pull request was filed against
`akuleshov7/ktoml`.** Defect #2 is still live in the in-place `appendEscapedUnicode`: it still
calls `hexCode.toInt(HEX_RADIX)`, and its contract only covers calls that return. `plan.md` §Phase 0 called for filing both; that was overridden by operator
decision, and this fork pushes only to its own remote. The fixes exist here as verified mirror
implementations, which is where an upstream patch would be drawn from if someone chose to send one.
Upstream `parsers/` and `writers/` sources are byte-identical to `main` in this branch. The
changes to `utils/` and to the two string-value nodes are the in-place specifications and the
supporting changes listed in §1.3.

Two of ktoml's own JVM tests are excluded by name, documented in
`docs/verification/known-failures.md`. Both are collateral from the Kotlin 2.2.0 → 2.3.0 bump the
pinned SnaKt plugin forces, bisected to that bump and not to formver or to anything in this fork's
specifications. Everything else in ktoml's test suite passes.

## 6. Feedback for SnaKt developers

Digested from `docs/verification/friction.md`, ranked by what each item cost the spec author.
Plan gap numbers (G1–G9) are cited where the friction confirms a gap the plan already predicted;
items marked **new gap** were not anticipated.

### 6.1 Tooling and reproducibility

1. **Verification failures do not fail the build.** A false postcondition reports at
   `Severity.INFO` and `compileKotlinJvm` prints `BUILD SUCCESSFUL`. This is the most serious item
   in the list: a showcase repository that passes green on a broken proof is worse than one that
   fails. The workaround here is a Gradle task that greps the compiler log for the literal string
   `Viper verification error` and throws — a hack that should not need to exist. Ship the promised
   `verificationErrorSeverity` control and default it to failing. **New gap.**

   This is worse than it first looks, and the evidence is in this project's own history. Two
   mid-chain stages reported their full control suites green in twelve minutes and in under two —
   impossible at two minutes per verification build — and one of them had already fallen back from a
   reporting script that was never written, after exiting 127. Nobody noticed, because when a
   verification failure reports at `INFO` there is nothing that distinguishes *a gate that passed*
   from *a gate that never ran*. A tool whose failures do not fail the build cannot be audited by
   reading its exit status, which is exactly how build graphs audit everything. Failing loudly is
   not a convenience; it is what makes a green result mean anything at all.
2. **No versioned release.** SnaKt is consumed by building a pinned commit and publishing it
   with `publishToMavenLocal` into a private local repository, then resolving it through
   `mavenLocal()` in `pluginManagement`. Nobody else can reproduce this fork's build. The plugin SHA is recorded
   in `docs/verification/plugin-pin.md` as a substitute for a real pin. Cut versioned releases to a
   real repository.
3. **Kotlin ABI mismatch fails with a bare `NoSuchMethodError`.** The pinned plugin was built
   against Kotlin 2.3.0's FIR API; ktoml pinned 2.2.0, and the plugin crashed inside
   `PluginErrors.<clinit>` on first contact. Diagnosing that cost real time, and the fix was a
   permanent repo-wide Kotlin bump — which then broke two of ktoml's decoder tests, which then had
   to be bisected and excluded. Publish a compatible-Kotlin-version range per release and fail fast
   with a readable diagnostic. **New gap.**
4. **JVM 21 floor injected with no opt-out.** `formver.annotations` is added to every compilation
   the subplugin touches, its metadata requires JVM 21+, and there is no way to decline it. This
   forced a second permanent fork divergence (ktoml-core's JVM target 8 → 21). Match the
   annotations artifact's floor to the plugin's stated minimum, or make it optional. **New gap.**
5. **DSL drift between documentation and pins.** The plan's build wiring assumed
   `formver { verificationErrorSeverity(...) }` from upstream PR #227; the pinned build exposes
   `errorStyle`, which turned out to be message formatting, not a severity knob. Version the DSL
   documentation with releases. Related: invalid option values produce
   `Value error is not a valid argument for option error_style.` with no list of legal values.
   **New gap.**

### 6.2 Specification language

1. **No `exists`, no quantifier alternation.** The single most expensive recurring wall, and the
   direct cause of four of the nine weakened specifications. `getCountOfOccurrencesOfSubstring`'s
   "0 iff no occurrence", `startsWithIgnoreAllWhitespaces`'s whitespace characterisation and
   `hasTwoConsecutiveSymbolsIgnoreWhitespaces`'s null-iff characterisation all need an existential
   witness the DSL cannot state. It also surfaced inside loop reasoning, where `!(forAll ...)`
   needed to yield an existential fact and required a hand-carried witness variable instead. Add
   `exists`, or document a supported witness-variable pattern. **New gap** — the plan noted
   "single-variable `forAll`" as a capability but did not predict the cost.
2. **Spec-block purity crashes instead of diagnosing.** Calling a non-`@Pure` function from
   `postconditions`, `loopInvariants` or a `forAll` bound produces an internal plugin error —
   `PureLinearizer used to convert non-pure ExpEmbedding; operation freshAnonVar is not supported
   in a pure context` — rather than a diagnostic naming the offending call. `Char.code` and
   ternary expressions inside spec blocks hit the identical crash. Worse, `@Pure` requires a
   side-effect-free *expression* body, so it can never be applied to a `while`-loop
   implementation, which forces spec authors to duplicate loop-based predicate logic into every
   caller's spec block. Three fixes, in order of value: make it a diagnostic, document the
   restrictions in one place, and consider a pure-for-specification-purposes marker for loop-based
   helpers. **New gap.**
3. **`while`-loop invariants have a long undocumented failure taxonomy.**
   `convertLineEndingBackslash` alone needed eight distinct invariant fixes across eight compile
   iterations at roughly three minutes each: missing bound on empty input, missing upper bound,
   missing cross-loop relational fact, off-by-one boundary preservation, insufficient
   flag-to-postcondition linkage, unsafe indexing inside the invariant's own quantified body,
   numeric looseness at loop exit, and the `exists` gap above. Error messages that distinguished
   "not established on entry" from "not preserved" from "not strong enough for the postcondition"
   would have cut that substantially. Adjacent to **G6** and **G9**, but distinct: G6 is about
   `for` loops, this is about `while`-invariant ergonomics.
4. **String-append content postconditions do not survive loop invariants.** Confirms **G5**
   directly. "Output contains no control character" holds for single-`Char` appends but cannot be
   carried through invariant preservation for whole-string appends (`res = res + computed`), and
   even single-`Char` appends fail when the postcondition is a guarded implication rather than a
   flat fact. This is what weakened all five escaping functions. When G5 is implemented, test
   implication-shaped content postconditions specifically, not just flat ones.
5. **Enum class support is unknown.** `IntegerRepresentation` is a Kotlin `enum class`; the
   capability inventory does not mention enum classes at all, so the mirror sidesteps by modelling
   the dispatch as `Int`-tag `when` functions. State whether enum classes are supported. **New
   gap.**

### 6.3 Gaps the plan predicted that Phase 1 confirmed

- **G2 (`throw` support)** is the reason the mirrors exist at all. The pinned build converts
  `throw` as an exit, which is close to G2(b), and §1.3 shows it is enough to specify shipping
  functions in place. Using it on the 28 mirrored functions is the highest-value next step.
  Exceptional postconditions — "throws iff the input is invalid" (G2(a)) — are still missing, so a
  validator's throwing paths remain unspecified.
- **G5 (string-append model)** weakened five functions, as above.
- **G4 (bounded integers)** and **G9 (termination)** are unconfirmed but unaddressed; they are the
  two standing caveats on every claim in §1.
- **G1, G3, G7, G8** were not exercised — Phase 1 touches no lists, no `Long`, no interfaces and
  no generics. Their priority ordering in the plan is untested by this work.

### 6.4 Process observations, not SnaKt

Two things about *running* this work are worth passing on, because they are properties of any
long-lived verification stage rather than of this repository.

- **Verification runs are long enough to change how the work must be driven.** A single
  `compileKotlinJvm` is roughly two minutes, and the full control suite is 97 of them — close to
  seven hours end to end. Every spec fix costs a build to confirm, and every spec fix that shifts a
  line invalidates the patch context of controls anchored nearby. Anyone planning Phase 2 onward
  should budget for the suite as an overnight job, not a check, and should expect patch-context
  churn to scale with spec churn.
- **A stage session driven for hours by periodic liveness pokes began emitting text imitating those
  pokes.** During the control-suite wait, a block formatted exactly like an incoming orchestrator
  message — matching tag, matching phrasing — appeared in this session's stream carrying
  instructions that the named orchestrator had not sent. Its content was a close paraphrase of the
  plan already stated in the session's own preceding turns, which is the signature of text
  synthesized from context rather than received. Nothing bad followed here, because the fabricated
  instruction happened to agree with the real one. That is luck, not a safeguard. Any harness that
  parks an agent for hours on a repeating wake-up channel should assume that channel can be
  counterfeited from the agent's own context, and should give the agent a way to distinguish a real
  delivery from a synthesized one — a nonce, a signed envelope, anything the transcript cannot
  reproduce. Verification work is unusually exposed to this, because it is unusually slow: hours of
  parked turns with near-identical content is precisely the condition that makes the imitation easy
  and the discrepancy hard to spot.

## 7. Verdict

Phase 0 and Phase 1 are complete against the plan's scope, and Phase 2 has started with six
functions specified in place. All four gates pass from clean, after
fixes in the three categories §4 sets out: the compliance suite (an uninitialized submodule and an
XPASSing baseline entry), the precondition probes, and five stale control patches.

The probes are where the substance is. Five findings there: one probe missing outright, and four
probes that had never been executed by the recorded run and turned out to do no work when they
finally were. Four of the five are closed. The fifth,
`probe-precondition-getCountOfOccurrencesOfSubstring`, is not closable by any specification and is
declared with its reason.

What a reader should take away, in one paragraph: **ktoml's character-scanning layer has been
transcribed into 50 machine-checked functions covering 28 upstream functions, and that transcription
is proved crash-free and index-safe on arbitrary input, with functional postconditions on index
meaning, slice content, and the one length-preservation invariant that ktoml's own callers silently
depend on. The proofs are partial correctness, under mathematical integers, on one platform, and
they are mostly about mirrors rather than about ktoml's shipping code.** Six shipping functions now
carry narrower contracts in place. The pinned SnaKt build converts `throw`, so moving the mirrored
properties onto the shipping functions is now possible, and it is the single change that would most
improve what this fork can honestly claim.

Two known upstream defects were confirmed by the act of specifying them. Neither fix has been sent
upstream, by operator decision.

## 8. Next steps

In order of value:

1. **Move the mirrored properties onto shipping code.** Start with `replaceEscaped` (length
   preservation), `indexOfNextOutsideQuotes` and the `trim*` family. Each move retires a mirror and
   its drift-manifest entry. Stdlib calls in the shipping bodies (`substring`, `startsWith`,
   `indexOf`) carry no axioms, so each move needs either a small rewrite of the body or SnaKt
   specifications for those calls.
2. **Add negative controls for the in-place functions.** At minimum a vacuity probe each and the
   precondition probe for `escapeBackslashes`; then run the full control suite from clean.
3. **Fix the two confirmed defects in shipping code**: the bounds check in `isLineEndingBackslash`
   and the uncaught `NumberFormatException` in `appendEscapedUnicode`. Filing them upstream is an
   operator decision.
4. **Close the drift-check holes**: teach `scripts/check-mirrors.sh` to read class members and to
   tell same-named functions apart, so the four unguarded mirrors are covered.
5. **Phase 2 proper**: the escaping round-trip (`escapeSpecialCharacters` then
   `convertSpecialCharacters` is the identity) and the multiline state-machine invariant, per
   `plan.md`. The round-trip depends on SnaKt's `StringBuilder` model (G5).
