# Accessibility checklist

Status values: **Automated** (checked by `AccessibilityChecksIT` in headless Chrome on every
`-Psystem` run), **Not performed** (a manual check that nobody has done yet).

No manual accessibility review has been carried out. Nothing below should be read as a
conformance claim (for example WCAG).

## Automated checks

| Check | Pages | Status |
|---|---|---|
| `<html lang="en">` | sign-in, verification, voter home, ballot, results, results-not-available, rejection page, 403 and 404 pages, all administrator pages | Automated |
| Exactly one `<h1>` and a descriptive `<title>` | same | Automated |
| `<main id="main">` landmark and a "Skip to main content" link | same | Automated |
| Every visible form field has a `<label for>` or `aria-label` | same | Automated |
| Every button and link has an accessible name | same | Automated |
| Tables have a `<caption>` and header cells with `scope` | same | Automated |
| Field sets have a `<legend>`; no duplicate `id`; `aria-describedby` targets exist | same | Automated |
| Invalid form: message next to the field, `aria-invalid`, `aria-describedby`, summary with `role="alert"` | new election form | Automated |
| The skip link is the first Tab stop and moves into view when focused | verification page | Automated |
| Focus outline is visible (solid, at least 2 px) on the skip link, text fields and the submit button | sign-in page | Automated |
| Sign-in, candidate selection and "review" work with the keyboard only | sign-in, ballot | Automated |
| Phase badges and rejection messages carry text, not only colour | voter home, rejection page | Automated |

## Manual checks

| Check | Status |
|---|---|
| Screen reader pass (NVDA or VoiceOver) through sign-in, voting and verification | Not performed |
| Colour contrast measured with a contrast tool for every text and badge colour | Not performed (colours were chosen with dark text on light backgrounds, but no measurement was recorded) |
| Browser zoom at 200% and text-only zoom | Not performed |
| Complete keyboard-only pass through all administrator pages | Not performed |
| Phone-sized layout on a real device | Not performed (two phone-width screenshots exist in `docs/screenshots`) |
| Forced-colours / high-contrast mode | Not performed |
| Review of the wording by a second person | Not performed |
