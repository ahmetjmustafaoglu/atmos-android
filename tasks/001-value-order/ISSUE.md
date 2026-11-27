# Task 001 — Arabic shows "°11" instead of "11°"

> This is the task **as a solver receives it**: the symptom, how to see it, what "done" means.
> It does not say where the bug is or how to fix it.

## Reported
> "With the phone in Arabic, Home says **الإحساس °11**: the degree sign is on the wrong side."
> (from the Arabic screenshot)

## Reproduce
Format a temperature with `WeatherFormatter` for Arabic and put it into the Arabic
`home_feels_like` string. Display that sentence right to left (or run it through
`java.text.Bidi`): the value shows as `°11`.

## Background you need
Arabic text is shown right to left. Characters like `°`, `%`, `/`, `:` and spaces have no
direction of their own, so the Unicode Bidirectional Algorithm decides where they appear from
the text around them. Inside an Arabic sentence, a left-to-right value can come out reordered.

## Acceptance criteria (the specification the hidden tests check)
1. In every Arabic string that carries a value (feels like, humidity, chance of rain, wind,
   low/high, sunrise/sunset), each value is **displayed exactly as it looks on its own**.
2. "On its own" means in its own direction: right to left if its first letter is Arabic (an
   Arabic 12-hour time such as `2 م`), otherwise left to right (`11°`, `25 km/h`, `69%`,
   `14:00`).
3. The visible characters of a value do not change: `15°` stays `15°`. Invisible direction
   marks are allowed; screen readers and copied text ignore them.

## Constraints
- No new dependency. Keep `WeatherFormatter`'s API: other screens call it.

## Done when
- The hidden tests pass. They check what is displayed, for thousands of generated values in
  all of these sentences, not which characters your code adds.
- The existing checks pass: `./gradlew spotlessCheck checkLocaleSafeCasing lint test`.
