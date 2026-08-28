# SnaKt (formver) capability inventory — as of 2026-08-27

Sources: SPECIFICATIONS.md, README.md, docs/, `formver.annotations` source, `formver.compiler-plugin` core source, testData under `formver.compiler-plugin/testData/diagnostics` and `formver.compiler-plugin/locality/testData`, `git log --since=2026-08-01`.

## Contract/annotation syntax

Kotlin DSL functions, not annotations, for specs:
- `preconditions { ... }`, `postconditions<T> { result -> ... }` — `formver.annotations/src/.../Builtins.kt:20-24`. Example: `testData/diagnostics/verification/user_invariants/factorial.kt:6-11`.
- `loopInvariants { ... }` inside a `while` body — `verification/user_invariants/loops.kt:14-17`, including nested loops (:38-46) and a `break`-guarded loop (:57-60). Rules (entry/maintenance/exit, post-loop negated-condition assumption): SPECIFICATIONS.md:70-75.
- `@Pure` (function-only) — `Annotations.kt:27`; violation diagnostics in `verification/purity/wrongly_annotated.kt:8`.
- `@AlwaysVerify`, `@NeverVerify`, `@NeverConvert` — SPECIFICATIONS.md:19-22.
- `forAll<T> { i -> ... }` with `triggers(...)` — only single-expression triggers; `ForAllEmbedding.kt:13` TODO says only a single bound variable is supported today (multi-variable forall not implemented despite doc wording at SPECIFICATIONS.md:111 which only flags the trigger-shape limit).
- `old(...)`, `acc(path, permission)`, `fold`/`unfold`, `read()`/`write()` permission annotations, `@Unique`/`@Borrowed`/`@Manual` — separation-logic-style, source-only (`Builtins.kt:31-104`, `Annotations.kt:8-36`), undocumented in SPECIFICATIONS.md.
- No `requires`/`ensures`/class-level `invariant`/`decreases` keywords exist. **No termination/decreases clause exists at all** — confirmed by `verification/control_flow/recursion.kt`, which contains an infinitely-recursive function (`fun recursive(): Unit = recursive()`) that converts/passes with no termination check.
- No lemma or ghost-function concept anywhere (`grep -i lemma\|ghost` empty).

## Type model

- **Int**: mapped directly to Viper's native `Int` (unbounded mathematical integer) — `PretypeEmbedding.kt:53-55`. No overflow semantics anywhere (`grep overflow/MAX_VALUE/MIN_VALUE` empty). **Overflow is silently unsound**: any spec relying on 32-bit wraparound will be verified against math-integer semantics instead.
- **String/Char**: basic stdlib tests exist (`testData/diagnostics/stdlib/string/{strings,chars}.kt`); no documented semantics beyond conversion-level tests.
- **Nullable types**: well-exercised — safe-call operator (`verification/operators/safe_call.kt`), contract tests `contracts/positive/returns_null.kt`, `contracts/negative/returns_not_null.kt`, and structures like `linked_list.kt`, `binary_tree.kt`.
- **Collections**: only `List` has real coverage — indexing/bounds (`POSSIBLE_INDEX_OUT_OF_BOUND`, `verification/contracts/negative/list.kt:8,14,19,25`), `add`, `subList` range checks (`:30`). **No Map testData exists anywhere in the tree** — Map support is unclear/effectively absent for verification purposes.
- **User classes/fields, interfaces, overrides**: solid coverage — `verification/classes/interfaces.kt` has abstract var/val properties (:7-10), default property getters, diamond multiple-interface inheritance with override (`Impl(override val number: Int) : First, Second`, :27).
- **Generics**: test exists (`verification/types/generics.kt`, generic class `Box<T>` and generic top-level fn :20) but the embedding layer has an open TODO — `ClassTypeEmbedding.kt:18`: "incorporate generic parameters" — meaning generic reasoning is incomplete/uncertain, not proven sound.

## Control flow

- **while loops**: fully supported with `loopInvariants{}`, the tested/primary looping construct. No `for`-loop-invariant syntax found — `for` loops are effectively unsupported for verification (likely desugared/rejected, not invariant-checkable).
- **try/catch/throw**: converts, but **only tested under `@NeverConvert`/negative/exclusion paths** — no positive (`@AlwaysVerify`) try/catch verification test exists. Exceptions are checked at most for locality/uniqueness (`locality/testData/uniqueness_checker/{try_catch,throw}.kt`), not verified. One documented failure: passing a caught exception object to another function fails verification — its contents aren't reasoned about (`verification/negative/try_catch.kt`, `useException` test → `VIPER_VERIFICATION_ERROR`).
- **recursion**: converts and can carry pre/postconditions (factorial.kt), but with **no termination checking** (see above) — an infinite-recursion test is accepted.
- **when-expressions**: supported and tested (`verification/control_flow/when.kt`, `locality/testData/uniqueness_checker/when_expression.kt`).
- **early return**: no dedicated test found; not separately documented — status unclear, likely handled as general control flow but unverified as a distinct case.

## Explicit unsupported-construct markers (source, not testData)

Central gating mechanism: `UnsupportedFeatureBehaviour` enum (`THROW_EXCEPTION` default / `ASSUME_UNREACHABLE`), plugin option `unsupported_feature_behaviour` (README.md:105, SPECIFICATIONS.md:119) — used throughout `ProgramConverter.kt`/`StmtConversionVisitor.kt` to gate unhandled constructs generically rather than via an enumerated list. **`ASSUME_UNREACHABLE` mode is a silent-unsoundness escape hatch**: any construct hit under it makes the verifier assume `false`/unreachable at that point, which can make surrounding proofs pass vacuously.

Point markers found in main source:
- `StmtConversionVisitor.kt:103` — literal constant-expression kinds "not yet implemented."
- `StmtConversionVisitor.kt:117` — some expression kinds not supported inside string concatenation.
- `StmtConversionVisitor.kt:201` — some equality-operator variants "not yet implemented."
- `StmtConversionVisitor.kt:287` — only plain function calls supported as callables (`NotImplementedError` otherwise).
- `StmtConversionVisitor.kt:455` — "Unsupported receiver expression type."
- `ProgramConverter.kt:673` — embedding for some types "not yet implemented" (`NotImplementedError`).
- `ClassTypeEmbedding.kt:18` — generics parameters not fully incorporated.
- `ForAllEmbedding.kt:13` — quantifiers limited to a single bound variable.
- `LinearizationVisitor.kt:603` — literal `TODO` for function-object creation with counters.

README.md:7-8 states plainly, with no itemized checklist: "The plugin is still in early development and large parts of Kotlin syntax are not supported."

## Recent changes (git log --since=2026-08-01)

All infrastructure/docs, **no new verification capability landed in August 2026**:
09e85da4 (annotations dep), ba13d25c (per-checkout test dump dir), 310af6f8 (README fix), b94b6c92 (test.sh coverage report), 6a065b1f (README contact), plus doc-splitting and test-generation/golden cleanup commits.

## Verified-sound vs. silently-uninterpreted — summary

**Reasonably solid** (positive tests, `@AlwaysVerify`, no open TODO): while-loops with invariants, pre/post via `preconditions`/`postconditions`, `@Pure`, `when`, interfaces/overrides, nullable-type flow, List bounds/index checks, single-variable `forAll`.

**Silently unsound or uninterpreted** — plan around these explicitly:
1. Int overflow — modeled as unbounded math int, not machine 32-bit.
2. Termination — none checked; recursion and loops can diverge without detection.
3. Exceptions — try/catch converts but isn't part of the verified positive suite; caught-exception contents are opaque.
4. Map — no test coverage; treat as unsupported until proven otherwise.
5. Generics — embedding-layer TODO open; treat generic-heavy code as unverified even if it converts.
6. `for` loops — no invariant mechanism found.
7. `ASSUME_UNREACHABLE` mode — can silently make unsupported-construct paths verify vacuously; must confirm which mode is active in any deployment before trusting results.
