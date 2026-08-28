# Plan: fully verifying ktoml with SnaKt

Target artifact: a `komiputer/ktoml` fork of `akuleshov7/ktoml` (survey commit
`79f1bc307d866225a1cee7dbc70efa2de83f11af`) carrying SnaKt specifications in-tree, built and
proof-checked in CI, rebasable on upstream, and usable as the flagship SnaKt showcase.

Scope discipline up front. ktoml's common source is ~7.4k LOC across core/file/source. Of that,
roughly 1.3k LOC (`parsers/StringUtils.kt`, `utils/SpecialCharacters.kt`,
`parsers/TomlMultilineString.kt`, `writers/IntegerRepresentation.kt`, parts of
`parsers/TomlParser.kt`) is pure String/Int/Char/Boolean logic that SnaKt can reach today or with
near-term features. The remaining ~6k LOC is AST (`tree/nodes/**`, 2,042 LOC), the
kotlinx.serialization encoder/decoder pair (1,959 LOC), the emitter (465 LOC) and I/O plumbing.
"Full functional correctness" for the fork therefore means: **the parsing/lexing/escaping kernel and
the AST-shape layer proved against explicit specifications, with the serialization-framework
boundary specified but not proved.** Section 5 states exactly what is out of reach and why. Anything
else would be a claim the tooling cannot cash.

---

## 1. What can be verified with SnaKt as it stands

SnaKt today (per the capability inventory, 2026-08-27) reliably supports: `preconditions` /
`postconditions` / `loopInvariants` blocks, `@AlwaysVerify` / `@Pure`, `while` loops, `when`,
nullable flow, user classes with fields, interfaces and overrides, `List` index-bounds checking, and
single-variable `forAll`. It does not support `throw`, `for`-loop invariants, `Long`/unsigned, `Map`,
list *element* reasoning, generics soundly, or termination.

The prior study already landed a 389-line `Lexing.kt` kernel with 13 `@AlwaysVerify` functions,
mutation-tested with nine rejected mutants. That is the baseline; this plan extends it rather than
re-deriving it.

### 1.1 Already proved (baseline — port into the fork as-is)

From `parsers/StringUtils.kt` and `utils/SpecialCharacters.kt`, in index-returning form:
`nextKeyPartEnd`, `indexOfNextOutsideQuotes`, `firstInvalidBareKeyChar` (from `validateSymbols`),
`firstBadEscape` (from `convertSpecialCharacters`), `hasBalancedQuotes` (from `validateQuotes`),
`slice`, `countChar`, `isTomlWhitespace`, `isLetterOrDigit`, `hexDigitValue`, `escapingDisabled`,
`isLineEndingBackslash`. Properties: total (no exception, no OOB) on arbitrary input, plus
*usability* postconditions — the returned index lies in `[start, s.length]` or is `-1`, and the
character at that index is the one the caller expects; `-1` genuinely means absent.

### 1.2 Reachable now with no new SnaKt feature

Each of these is pure `String`/`Int`/`Char`/`Boolean`, loops are `while`-shaped or can be rewritten
as such, and the only rewrite needed is exception-elimination (throw → sentinel/predicate), which the
prior study already established as the house pattern.

| ktoml function | File:line | Property provable today |
|---|---|---|
| `getCountOfOccurrencesOfSubstring` | `parsers/StringUtils.kt:236` | Reimplement `split`-free as a `while` scan; prove result ≥ 0, result = 0 iff no occurrence, and result ≤ `length / substring.length`. |
| `trimSymbols` (private) | `parsers/StringUtils.kt:396` | Total; result length = `length - prefix.length - suffix.length` when both present, else unchanged; result is a contiguous slice of the input (per-character content postcondition, via `forAll` over one index). |
| `trimSingleQuotes` / `trimQuotes` / `trimCurlyBraces` / `trimBrackets` / `trimDoubleBrackets` | `StringUtils.kt:63,116,168,176,192` | Each is a `trimSymbols` instantiation; inherit the slice/length postcondition. Cheap wins, good demo surface. |
| `trimMultilineQuotes(quotes)` | `StringUtils.kt:370` | Total; result is a slice; result never longer than input; idempotence on already-trimmed input. |
| `isNotQuoted` | `StringUtils.kt:368` | Total predicate; equals `!(first=='"' && last=='"' && length≥2)` — exposes the length<2 edge explicitly. |
| `isLetterOrDigit` | `StringUtils.kt:364` | Total; agrees with the explicit `CharRange` disjunction (hand-spelled, no stdlib dependence). |
| `startsWithIgnoreAllWhitespaces` | `StringUtils.kt:291` | Rewrite the `filterNot` HOF as a `while` two-pointer scan; prove total, and prove agreement with the whitespace-skipping characterisation. |
| `convertLineEndingBackslash` | `StringUtils.kt:82` | Currently uses `.split`; rewrite index-based. Prove total and that the result is a prefix of the input up to the trailing-backslash position. |
| `isLineEndingBackslash` | `StringUtils.kt:101` | **Already known-buggy** (reads two chars past end). Prove the fixed version total; state the precondition the original silently assumed. |
| `takeBeforeComment` / `trimComment` | `StringUtils.kt:203,222` | Both delegate to `indexOfNextOutsideQuotes` + `substring`. With `slice` in hand: total, result is a prefix of the input, result contains no unquoted `#`. |
| `replaceEscaped` | `StringUtils.kt:300` | Total; result length = input length (placeholder is 2 chars, replaces 2-char escapes) — a genuinely load-bearing invariant, since callers index the result and use those indices against the *original*. This is the highest-value single proof in the file. |
| `escapeControlChars` / `escapeBackslashes` / `escapeSpecialCharacters` | `utils/SpecialCharacters.kt:135,173,105` | Total; output contains no control character (a `forAll` over output indices); output length ≥ input length. `StringBuilder` accumulation must be rewritten as index-based string building (see gap G5 if that proves painful). |
| `Char.isControlChar` / `isMultilineControlChar` / `escapeControlChar` | `SpecialCharacters.kt:124,133,189` | Total; `CharCategory.CONTROL` must be hand-spelled as a codepoint range (`< 0x20 || == 0x7f`), same treatment as `isTomlWhitespace`. Prove the two predicates are related by exactly the `\n`/`\r` exclusion. |
| `appendEscapedUnicode` | `SpecialCharacters.kt:74` | The site of known defect #2 (`hexCode.toInt(16)` outside the `try`). Reimplement as `hexDigitValue`-driven accumulation; prove total, prove it rejects every non-hex input rather than throwing. |
| `hasTwoConsecutiveSymbolsIgnoreWhitespaces` | `parsers/TomlMultilineString.kt:147` | Total; `Boolean?` result — prove `null` is returned exactly on the "no symbol found" case, which is the distinction callers act on. Good nullable-flow showcase. |
| `isNextThreeQuotes` | `TomlMultilineString.kt:105` | Total, no OOB — its index arithmetic (`i`, `i+1`, `i+2`) is precisely the class of bug found in `isLineEndingBackslash`. |
| `IntegerRepresentation` prefix/radix consistency | `writers/IntegerRepresentation.kt` | Enum-shaped: prove `radix ≥ 2`, `prefix.length ∈ {0,2}`, and that `when`-dispatch over the five cases is exhaustive. Small, but a clean `when`-exhaustiveness demo. |

Estimated reach with zero new SnaKt features: **~500–600 LOC of ktoml logic, ~35–40 verified
functions**, covering essentially all of `StringUtils.kt` and `SpecialCharacters.kt` plus the pure
predicates of `TomlMultilineString.kt`. That is the whole character-level layer of the parser.

### 1.3 What that buys, stated honestly

Proving all of §1.2 gives: **the character-scanning layer of ktoml cannot crash, cannot read out of
bounds, and returns indices that mean what callers assume they mean, on arbitrary input including
adversarial/malformed TOML.** It does not give "ktoml parses TOML correctly" — no statement about the
TOML grammar is made anywhere in this phase.

---

## 2. SnaKt feature gaps, prioritized by ktoml unlocked

Each gap: what is missing, the ktoml code it blocks, and roughly how much of ktoml it unlocks.

### G1 — List element model (`List<T>.get` is `@Pure` and axiomatised) — **unlocks ~2,000 LOC**

Today `List<T>.get` havocs; only bounds are checked. Every AST node in
`tree/nodes/**` (2,042 LOC) holds `children: MutableList<TomlNode>`; `TomlNode.kt:*` traversal
(`findTableInAstByName`, child insertion, `getNeighbourNodes`), `TomlTable.kt`'s
`fullTableKey`/`tablesList`, and `StringUtils.splitKeyToTokens` (`StringUtils.kt:20`, returns
`List<String>`) all need to say something about *elements*, not just size. Without this the entire AST
layer is unreachable and `splitKeyToTokens` — the single most-cited candidate in the survey — can only
be given a length postcondition, not the "the tokens concatenate back to the input" property that
makes it interesting.

Needed specifically: `get` as a pure function of the list and index, a `size` accessor usable in
specs, and enough of an axiomatisation that `forAll<Int> { i -> ... l[i] ... }` with an index trigger
is usable. Sequence/`Iterator` support (`TomlParser.trimEmptyLines`, `TomlParser.kt:137-204`) is a
separate, lower-priority extension of the same gap.

**Highest priority.** Nothing else unlocks comparable volume, and without it the fork is permanently
a lexer showcase rather than a parser showcase.

### G2 — `throw` support (`visitThrowExpression`) — **unlocks ~800 LOC and removes all rewriting**

81 `throw` sites in common source. Today every validator must be rewritten into an index-returning
form, which means **the fork's specs describe code that is not the code that ships**. That is the
single biggest credibility problem with the showcase. Concrete blockers:
`StringUtils.validateSpaces/validateQuotes/validateSymbols` (`:317,329,342`), `parseKeyName`
(`:146`), `convertSpecialCharacters` (`SpecialCharacters.kt:31`), and every AST constructor in
`tree/nodes/pairs/values/*` that throws `ParseException` on malformed input.

Needed: (a) `throw` as a terminating statement with a specifiable exceptional postcondition, ideally
`postconditions` + a `throwsWhen`/exceptional-postcondition form; (b) at minimum, `throw` as
"diverges", which alone makes total-correctness-modulo-exceptions proofs possible on unmodified
ktoml source. (b) is a much smaller change and buys most of the value — recommend pushing for (b)
first.

**Second priority**, and arguably first for showcase quality: it is what lets specs sit on upstream
code verbatim.

### G3 — `Long` and unsigned types — **unlocks ~400 LOC**

45 `Long`/`ULong`/`UInt` mentions. `tree/nodes/pairs/values/TomlLong.kt` (parsing integers with
prefix dispatch over `IntegerRepresentation`, `:35-50`), `TomlUnsignedLong.kt`, and the
decoder's `toLong`/`toULong` coercions are entirely blocked. TOML's integer type *is* 64-bit signed;
without `Long`, ktoml's number handling cannot be specified at all, correctly or otherwise.

Note this interacts with the overflow gap (§5): a `Long` mapped to unbounded Viper `Int` would let us
verify ktoml's integer parser while silently missing exactly the overflow bugs a TOML integer parser
is most likely to have. `Long` support without a bounded-integer model is a trap here, not a win.

### G4 — Bounded-integer / overflow semantics — **unlocks correctness (not volume) everywhere**

Currently `Int` is mathematical. All index arithmetic in the verified kernel is therefore proved
under an assumption ktoml does not enjoy. For the lexer this is low-risk (strings are far from
2^31), but for `TomlLong.parse` (`TomlLong.kt:35`) and `IntegerRepresentation` radix conversion it is
the whole point. Prerequisite for any honest claim about ktoml's number path.

### G5 — `StringBuilder` / string-append model — **unlocks ~300 LOC, removes rewrites**

`SpecialCharacters.appendEscapedUnicode` (`:74`), `escapeControlChars` (`:135`),
`escapeBackslashes` (`:173`), and `writers/TomlStringEmitter.kt` all build strings via
`StringBuilder`. Today each must be rewritten to index-based construction. A modelled
`StringBuilder` (append char/string, `length`, `toString` with a content axiom) would let the
escaping layer be specified in place.

### G6 — `for`-loop invariants — **unlocks ~150 LOC, large ergonomics win**

6 `for` loops in common source; more importantly, `for` is what idiomatic Kotlin ports use, and
`loopInvariants{}` inside a `for` currently establishes nothing *silently* — an active soundness
hazard in a showcase repo where contributors will reach for `for` by reflex. Even a diagnostic
("loopInvariants ignored in a for loop") is worth landing before the fork accepts outside
contributions.

### G7 — Interface/abstract-method contract inheritance — **unlocks ~465 LOC (emitter)**

`writers/TomlEmitter.kt` (366 LOC) is an abstract class whose ~25 `emit*` methods call
`protected abstract emit(fragment)`. Verifying it requires contracts on abstract members that
subclasses must refine. SnaKt has interface support with overrides but no documented
contract-inheritance/behavioural-subtyping rule. Without it the emitter is unreachable.

### G8 — Generics soundness (`ClassTypeEmbedding.kt:18`) — **unlocks the decoder layer, but see §5**

29 generic sites. Needed for anything touching `KSerializer<T>` / `SerialDescriptor`. Low priority:
even with sound generics, the encoder/decoder correctness statement depends on the
kotlinx.serialization contract, which is out of scope (§5).

### G9 — Termination / `decreases` — **unlocks nothing new; upgrades every existing proof**

No volume unlocked, but every claim in this plan is currently partial correctness. For a *showcase*
this matters: "we proved it cannot crash" is weaker than a reader assumes when loops are unbounded.
Worth landing for the lexer's `while` scans, where the decreasing measure (`s.length - i`) is trivial.

### Priority order

`G1 > G2(b) > G6 > G5 > G4 > G3 > G9 > G7 > G2(a) > G8`.

G6 is placed high not for volume but because it is a live unsoundness in a repo intended for others
to read and imitate.

---

## 3. Phased roadmap

Every phase has the same non-negotiable gate, stated once:

- `@AlwaysVerify` on every spec-bearing function; no reliance on default target selection.
- `formver { verificationErrorSeverity("error") }` so unproved claims fail the build.
- `kotlin.incremental=false` in the mutation harness.
- `unsupported_feature_behaviour=THROW_EXCEPTION`, never `ASSUME_UNREACHABLE` — the latter can make
  the whole suite pass vacuously.
- Every phase ships its **negative controls** (see per-phase list) as a separate CI job that must
  *fail* verification. A phase is not done until its mutants are rejected.

### Phase 0 — Foundation (no new SnaKt features)

**ktoml scope**: none yet. Fork created, build wired (§4), the prior study's `Lexing.kt` kernel
imported as `ktoml-core/src/commonMain/kotlin/.../verified/` or as spec-annotated copies (decision in
§4.2). The two known upstream defects filed as issues/PRs against `akuleshov7/ktoml`.

**Properties proved**: the 13 baseline functions re-verified in the fork's own build — proof that the
harness works before any new spec is written.

**Negative controls**: replay the nine existing mutants; confirm all nine still fail. If any passes,
the harness is broken and Phase 1 does not start.

### Phase 1 — The character layer (no new SnaKt features)

**ktoml scope**: all of §1.2 — `parsers/StringUtils.kt` (except `splitKeyToTokens`),
`utils/SpecialCharacters.kt`, the pure predicates of `parsers/TomlMultilineString.kt`,
`writers/IntegerRepresentation.kt`.

**Properties proved**: totality (no exception, no OOB) on arbitrary input for ~35–40 functions;
index-validity and index-meaning postconditions; the `replaceEscaped` length-preservation invariant;
slice content-equality for every `trim*`; "output contains no control char" for the escapers.

**SnaKt features required**: none. This phase is executable today.

**Negative controls (minimum 2 per function class)**:
- Off-by-one mutants: `<` → `<=` in every scan bound; each must break the OOB proof.
- Sentinel mutants: return `0` instead of `-1` from each searcher; must break the "-1 means absent"
  postcondition.
- Content mutants: `trimSymbols` drops one extra char; must break the slice postcondition.
- **Vacuity probes**: for each function, insert `verify(false)` at the end of the body; it must fail.
  A function where `verify(false)` passes has an unreachable body or a contradictory precondition and
  its proof means nothing. This probe is cheap and catches the failure mode mutation testing misses.
- Precondition-strength probe: delete each precondition; the proof must break. A precondition whose
  removal changes nothing was doing no work and should be removed from the shipped spec too.

### Phase 2 — Escaping and multiline in place (needs G5, G2b)

**ktoml scope**: `SpecialCharacters.appendEscapedUnicode` and the `escape*` family specified on
their `StringBuilder`-using originals rather than rewrites; `TomlMultilineString.trackMultilineString`
(`:87`), `isEndOfMultilineValue` (`:116`), `getMultilineType` (`:178`) — the multiline state machine.

**Properties proved**: the escaping round-trip — `escapeSpecialCharacters` followed by
`convertSpecialCharacters` is the identity on inputs containing no bad escapes (the first *semantic*
property in the plan, not merely a safety property). For the state machine: the quote-count invariant
(`isInMultilineBasic` and `isInMultilineLiteral` are never both true) as a class invariant enforced at
every mutation site.

**SnaKt features required**: G5 (StringBuilder), G2b (`throw` as divergence, so
`convertSpecialCharacters` can be specified on the real signature). G6 helps but is not blocking.

**Negative controls**: break the round-trip by dropping one escape case from either direction — must
fail. Flip one boolean in `trackMultilineString` — the class invariant must fail. Plus the Phase-1
probe set.

### Phase 3 — Key parsing and token structure (needs G1)

**ktoml scope**: `StringUtils.splitKeyToTokens` (`:20`), `parseKeyName` (`:146`), the validators
`validateSpaces`/`validateQuotes`/`validateSymbols` (`:317-362`) on their real throwing signatures,
and `tree/nodes/pairs/keys/**`.

**Properties proved**: the token-partition property — the returned `List<String>`, joined with `.`
outside quotes, reconstructs the input key; every token is non-empty; every token satisfies the
bare-key or quoted-key predicate. For the validators: **throws iff the documented invariant is
violated** — both directions, which is what the prior study could only approximate with predicates.

**SnaKt features required**: G1 (list elements) is hard-blocking; G2 (throw, preferably (a) for the
iff direction).

**Negative controls**: mutants that drop the last token, that accept an empty token, that accept an
unbalanced quote — each must fail. Critically, a **completeness control**: a hand-written table of
malformed keys from the TOML spec, each asserted to be rejected. If the verifier proves "throws iff
invariant violated" but the invariant is written too weakly, this table catches it. This is the phase
where a vacuously-weak spec is most likely, so budget for it.

### Phase 4 — AST shape (needs G1, G2, G7)

**ktoml scope**: `tree/nodes/TomlNode.kt` (312), `TomlTable.kt` (286), `TomlInlineTable.kt` (324),
`TomlKeyValue.kt` (177), `TomlArray.kt` (163).

**Properties proved**: structural invariants only — a `TomlTable`'s `fullTableKey` equals the
concatenation of its ancestors' names; `findTableInAstByName` returns a node whose full key equals
the query or null, and never a node from a different subtree; child insertion preserves the parent
back-pointer. Note the AST is walked iteratively via loops and HOFs (no self-recursion found), which
helps: `while`-shaped traversal is SnaKt's supported case.

**SnaKt features required**: G1, G2, G7. This phase does not start before all three land.

**Negative controls**: return the wrong subtree from `findTableInAstByName`; drop a parent
back-pointer assignment; each must fail. Plus a **shape-oracle control**: differential-test the
verified traversal against ktoml's own test corpus and require agreement — a proof about a traversal
that disagrees with the test suite means the spec is describing the wrong function.

### Phase 5 — Numbers (needs G3 + G4 together)

**ktoml scope**: `tree/nodes/pairs/values/TomlLong.kt`, `TomlUnsignedLong.kt`, `TomlDouble.kt`
(safety only, no float semantics), `writers/IntegerRepresentation.kt` radix conversion.

**Properties proved**: integer parsing is total on arbitrary input and returns a value iff the input
matches the TOML integer grammar for the detected radix; round-trip with the emitter for every
representation. **Only meaningful with G4** — without bounded integers, "parses correctly" is proved
against a semantics ktoml does not implement, which is worse than not proving it.

**Negative controls**: overflow mutants (inputs at ±2^63 boundaries) that must be rejected by the
spec; radix-confusion mutants (`0x` parsed base 8).

**If G4 does not land, Phase 5 is cancelled, not weakened.** Say so in the fork's README.

### Phase 6 — Emitter (needs G7, G5)

**ktoml scope**: `writers/TomlEmitter.kt`, `TomlStringEmitter.kt`.

**Properties proved**: emitted output is well-formed at the character level — every emitted string is
quoted-and-escaped such that re-lexing it with the Phase-1 kernel yields the original. This is the
strongest available end-to-end claim: **writer output is re-readable by the verified reader**, for the
value types covered.

**Negative controls**: drop one escape in the emitter; the re-lex round-trip must fail.

### Not a phase: encoders/decoders

`decoders/*` (1,026 LOC) and `encoders/*` (933 LOC) are out of scope permanently. See §5.

### Sequencing summary

Phase 0 and 1 are executable now and should be finished before any SnaKt feature work is scheduled —
they produce the showcase's first honest artifact and calibrate the harness. Phases 2, 3, 4 are gated
on G5/G2, G1/G2, G1/G2/G7 respectively. Phase 5 is gated on the G3+G4 pair and may never happen.
Phase 6 is last because its value depends on Phase 1 being solid.

---

## 4. Fork strategy

### 4.1 Repository shape

Fork `akuleshov7/ktoml` → `komiputer/ktoml`. Branches:

- `upstream-main` — a pristine mirror of `akuleshov7/ktoml:main`, never committed to directly.
- `verified` — the showcase branch, rebased onto `upstream-main`.
- `spec/<phase-N>-<area>` — working branches merged into `verified`.
- `fix/*` — defect fixes intended for upstream PRs, kept minimal and rebasable so they can be sent
  upstream independently (the existing `jesyspa/ktoml` branches `fix/is-line-ending-backslash-bounds`
  and `fix/unicode-escape-hex-validation` are cherry-picked here and filed upstream in Phase 0).

### 4.2 Where specs live — in-place, with a fallback

**Preferred (target state): specs inline in upstream files.** `preconditions {}` / `postconditions {}`
blocks are ordinary Kotlin function bodies, so they appear as added lines inside
`ktoml-core/src/commonMain/kotlin/com/akuleshov7/ktoml/parsers/StringUtils.kt` etc. This is the whole
point of the showcase: the specification annotates the shipping code. It also keeps the diff against
upstream small and localised — one hunk per specified function — which is what makes rebasing cheap.

**Fallback for Phase 1 only: a parallel `verified/` source set.** Phase 1 needs exception-elimination
rewrites (throw → sentinel) that change ktoml's behaviour, and those must not ship. Until G2 lands,
put rewritten functions in
`ktoml-core/src/commonMain/kotlin/com/akuleshov7/ktoml/verified/` with a header comment naming the
upstream function each mirrors, and a CI check (`agent-scripts/check-mirror.sh` equivalent) asserting
the mirrored upstream function's source hash has not changed since the mirror was written. When
upstream edits a mirrored function the check fails and the spec is revisited. **This drift check is
mandatory** — an unwatched mirror is a spec for code that no longer exists, which is a worse failure
than no spec.

Each phase migrates functions out of `verified/` and into place as the enabling feature lands. Phase 2
retires the escaping mirrors; Phase 3 retires the validator mirrors. The fork's README carries a
mirror-count badge; driving it to zero is the fork's headline metric.

### 4.3 Build integration

Add to the root `build.gradle.kts`:

```kotlin
plugins { id("org.jetbrains.kotlin.formver") version "<pinned release>" }
formver {
    verificationErrorSeverity("error")
}
```

plus the compiler-plugin option `unsupported_feature_behaviour=throw_exception` (its default;
`assume_unreachable` must never be set — it makes unsupported constructs assume `false`, so
surrounding proofs pass vacuously). `verificationErrorSeverity` is the Gradle-extension form added by
upstream PR #227; confirm the exact DSL spelling against the plugin version pinned, since the only
documented option surface today is the compiler-plugin option list in SnaKt's README.

Applied to `ktoml-core` only; `ktoml-file` and `ktoml-source` are unverified I/O plumbing and stay
plugin-free so their build is unaffected.

Constraints from the prior study's tooling friction, baked in rather than rediscovered:

- `gradle.properties`: `kotlin.incremental=false` for the verification and mutation configurations
  (a stale incremental compile reports mutated source as verified).
- `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=2g` — the shaded plugin jar is ~100 MB and 1 GB
  metaspace was insufficient in at least one prior study.
- Pin the plugin, never `mavenLocal` — the prior study had `mavenLocal` republished underneath a
  running exercise. SnaKt currently publishes only `0.1.0-SNAPSHOT` (README.md:50), so until a real
  release exists the fork must vendor the plugin jar with a recorded SHA-256 and check that hash in
  CI. **Getting SnaKt to cut a versioned release is a prerequisite for the fork being reproducible by
  anyone else**, which is most of what a showcase is for; treat it as a G-level dependency.
- CI: three jobs — `build` (upstream's own tests, must stay green, proving the fork does not break
  ktoml), `verify` (`gradle verify` with severity=error), `mutants` (each negative control must
  fail verification; the job passes only if every mutant is rejected).

Verification runs on JVM target only. Multiplatform targets build unverified; `expect`/`actual`
functions (`newLineChar`, `appendCodePointCompat`) are out of scope (§5).

### 4.4 Staying rebasable

- Rebase `verified` onto `upstream-main` weekly, automated, with the three CI jobs as the gate.
- Keep every spec hunk minimal and adjacent to the function it specifies — no reformatting, no
  reordering, no unrelated cleanups. Rebase pain is proportional to diff surface, and a showcase fork
  that falls behind upstream stops being a showcase.
- No renames, no file moves, no package restructuring in `verified`. The `verified/` mirror directory
  is new-files-only, so it never conflicts.
- Upstream-worthy fixes go upstream as PRs rather than living in the fork; every merged upstream PR
  is one less hunk to rebase. The two known defects are the first two.
- When a rebase conflicts inside a specified function, the resolution rule is: take upstream's code,
  re-run verification, and fix the spec — never adjust upstream's logic to keep a proof green. A fork
  that edits the code to fit the spec is no longer verifying ktoml.

---

## 5. Honest limits — what "full functional correctness" cannot cover

**Termination.** SnaKt has no `decreases` clause and no termination check at all; an
infinitely-recursive function converts and passes (`verification/control_flow/recursion.kt`).
Everything in this plan is partial correctness: "if it returns, the result satisfies the spec."
For a parser fed adversarial input, non-termination *is* a denial-of-service bug, and this plan
cannot rule it out. G9 would fix this for the lexer's simple `while` scans and should be pursued;
until then the fork's README must say "partial correctness" in plain words, not in a footnote.

**Integer overflow.** Viper `Int` is unbounded; ktoml's `Int` and `Long` are not. Every index-arithmetic
proof holds under a semantics ktoml does not implement. For the lexer the gap is benign (strings do not
approach 2^31), but it is not *proved* benign — a length-bound precondition would be needed and none
is currently stated. For TOML integer parsing (`TomlLong.kt`) the gap is fatal, which is why Phase 5
is conditional on G4.

**Platform code.** The `expect`/`actual` pairs (`newLineChar`, `appendCodePointCompat`, ~18 `actual`
implementations across nine target directories) are unverified. Verification runs on one target; the
proofs say nothing about the JS, Wasm, Native, or iOS builds beyond what the common code guarantees,
and `newLineChar()` differing per platform is precisely the kind of thing the emitter's round-trip
property depends on. State this explicitly wherever the round-trip claim appears.

**The serialization framework boundary.** `decoders/*` and `encoders/*` (1,959 LOC, 76
Encoder/Decoder overrides, 40 `SerialDescriptor` mentions) implement kotlinx.serialization's
`Encoder`/`Decoder`/`CompositeEncoder`/`CompositeDecoder` protocols. Their correctness is defined
*relative to a contract that lives in another library and is not formally specified anywhere*. Even
with perfect generics support, verifying `TomlAbstractDecoder` would mean first writing a formal
model of kotlinx.serialization's protocol — a larger project than verifying ktoml. This plan does not
attempt it, and no claim about `Toml.decodeFromString` end-to-end is available. The strongest honest
end-to-end statement the fork can make is Phase 6's: *writer output is re-readable by the verified
reader*, for the covered value types.

**Stdlib functions that convert but constrain nothing.** `String.substring`, `Char.isWhitespace`,
`List.get`, `split`, `replace`, `Regex` — these convert successfully and contribute no axioms. A proof
that leans on one learns less than it appears to. The house rule, inherited from the prior study, is
to hand-spell the primitive (`slice`, `isTomlWhitespace`) rather than trust the stdlib call. Every
place in the fork where this rule is not followed must carry a comment saying so.

**Floats.** `TomlDouble.kt` gets safety properties only. No floating-point semantics exist in the
tooling and none is planned; TOML float parsing correctness is out of reach.

**Vacuity in general.** Negative controls make vacuity *unlikely*, not impossible. Mutation testing
proves the spec constrains *something*; it does not prove the spec constrains the *right* thing.
The `verify(false)` probe and the Phase-3 completeness table are the two mitigations that catch what
mutation misses, and both should be treated as mandatory rather than nice-to-have. A green fork with
no mutant job is not evidence of anything.

**What is actually claimable at the end of the roadmap, all phases landed:** ktoml's character-scanning,
escaping, key-parsing, AST-shape and emitting layers are proved crash-free and index-safe on arbitrary
input, with functional postconditions covering key tokenisation, escape round-tripping, AST structural
invariants, and writer/reader round-tripping — under partial correctness, on one platform, with the
serialization boundary and float handling excluded. That is a strong and unusual claim for a
production Kotlin library. It is not "ktoml is correct," and the fork should never say that.
