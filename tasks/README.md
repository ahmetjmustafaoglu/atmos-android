# Tasks: turning a real bug into a fair, checkable task (Android)

The method used for coding exercises, hiring tests and AI-agent evaluations: take a **real,
hard bug**, describe it as a task, and grade answers with tests that accept **any correct
solution** and reject **hard-coded** ones. The same method exists in the
[React Native](https://github.com/ahmetjmustafaoglu/atmos-react-native/blob/task/dst-midnight-day/tasks/README.md),
[iOS](https://github.com/ahmetjmustafaoglu/atmos-ios/tree/task/dst-missing-day/tasks) and
[Flutter](https://github.com/ahmetjmustafaoglu/atmos-flutter/tree/task/arabic-time-digits/tasks)
repositories, each with a real bug of its own.

```
tasks/
├── grade.sh                          ← tests the tests (CI: "Task grader")
└── 001-value-order/
    ├── ISSUE.md                      ← what the solver gets: symptom, background, specification
    ├── hidden/ValueOrderHiddenTest.kt ← the grading tests, with their oracle
    └── candidates/
        ├── buggy.kt                  ← the code as reported (no isolation)        → must FAIL
        ├── hardcoded.kt              ← a "fix" for the reported sentence          → must FAIL
        ├── reference.kt              ← the fix now on main (isolates)             → must PASS
        └── alternative.kt            ← a correct fix with another rule (FSI)       → must PASS
```

This branch is **the repository as the solver receives it**: `WeatherFormatter` isolates
nothing, and the two tests on `main` that check for specific characters are not here.

## The bug
Found in the Arabic screenshot during step 07 (commit `fd39a94`): "Feels like 11°" showed as
`°11`, and "25 km/h" as `km/h 25`. Inside right-to-left text, the Unicode Bidirectional
Algorithm moves characters that have no direction of their own (`°`, `%`, spaces).

## The oracle: the Unicode Bidirectional Algorithm itself
The hidden tests never look for particular characters. They put each formatted value into the
app's **real Arabic strings** (read from `values-ar/strings.xml`), compute what the screen
shows with `java.text.Bidi` (which knows isolates since JDK 21), and check that each value
looks exactly as it does on its own. So:
- `reference.kt` (left-to-right isolates for values, first-strong isolates for times) passes;
- `alternative.kt` (first-strong isolates for everything) passes too: a different rule, same
  result on screen;
- a fix with direction marks (LRM) or a different sentence structure would pass as well.

## Hard-coding fails, and the tests show why it is wrong
`hardcoded.kt` writes the degree sign first in Arabic (`°11`), so the reported sentence *looks*
right on screen. It still fails:
- the value's own text is now wrong (`°15`), which screen readers, copied text and any
  left-to-right context show; criterion 3 catches it;
- wind (`km/h 25`) and percentages are still broken: 400 random values in six sentences,
  with the seed printed (`TASK_SEED=<n>` replays a failure).

## Test behaviour, not implementation
`main`'s own tests check that a value **starts with** U+2066. That pinned the fix to one
implementation: they would reject `alternative.kt`, which is also correct. Grading tests must
check what users see. Implementation checks belong to the project's own unit tests, not to a
task's grader.

## Run it
```bash
tasks/grade.sh        # GRADLE_FLAGS=--offline tasks/grade.sh without network
# ok      buggy: hidden tests fail, as they must
# ok      hardcoded: hidden tests fail, as they must
# ok      reference: hidden tests pass, as they must
# ok      alternative: hidden tests pass, as they must
```

## In a real evaluation
Keep `hidden/` and `candidates/` outside the repository the solver works in, give them
`ISSUE.md` and this branch, and grade with the hidden tests plus the project's own checks.
