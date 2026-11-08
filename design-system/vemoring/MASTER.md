# VEMORING native UI

## Direction

Calm, spacious vehicle companion. UI/UX Pro Max was consulted for visual hierarchy, touch, contrast, spacing, and performance. Its automatic automotive/dashboard search suggested web marketing patterns, so those patterns, imported web fonts, and motion-heavy recommendations were not applied. This is a native Compose adaptation of the applicable guidance.

## Foundations

- Primary blue `#087BBF`; ink `#182F43`; navy `#173B54`; mint `#147D72`; warm accent `#B76120`.
- App background `#F0F4F8`; white surfaces; border `#DCE5ED`; hero background `#E2F0FA`; secondary text `#526678`.
- `GarageColors` is the common Material color scheme. Keep native system fonts for Korean coverage and zero font downloads.
- App title 40sp; page title 38sp; section title 28sp; body 21–24sp; supporting text 18–20sp. Let text wrap rather than scaling it down.
- Card radius 24dp, padding 24dp, inter-card gap 24dp; outer horizontal padding 40dp; wide columns separated by 28dp.
- Controls retain Compose ripple and semantics. Large primary actions are at least 64dp high. Calendar cells are 76dp high. Navigation uses consistent native line icons with visible text.

## Layout

- Content max width 1440dp within the actual app window. At content widths below 980dp, the two-column layout stacks into a single scrollable column.
- Home: center the character and its state message, with the fuel/battery/cleanliness cards directly below. Keep model and plate as small supporting text under the character. Do not show a separate nickname/edit card; profile editing belongs in My Car. Latest care and the one-at-a-time nearby banner may share a lower auxiliary row on wide windows. Do not infer vehicle health from unavailable data. Remove the static example weather block from the primary dashboard.
- Standard vehicle home (available window at least 980×900dp, font scale up to 1.15): fit the full dashboard without vertical scrolling. Use a 500dp character, 350dp stage, inline model/plate, compact card padding, 160dp status cards and a compact nearby banner. Smaller windows and accessibility font sizes retain scrolling to prevent inaccessible content.
- Care history: calendar and labelled legend on the left; records and a helpful empty state on the right. Preserve date validation, add/edit actions and all/recent modes.
- My car: identity and profile fields on the left; notification example status and custom thresholds on the right.
- Onboarding retains its upper-aligned heading and large spread-out selections. Existing app-icon/character assets remain unchanged by this UI redesign; character expression variants are a separate task.

## Resource constraints and validation

No new UI libraries, shaders, blur, automatic animations, fonts, location polling or background services. Home date summaries are cached per records snapshot. Navigation search and saved data behavior stay intact. Keep the official Pleos AVD profile, Cold Boot and 4GB RAM.

Build/unit checks and 6 functional UI regression tests passed; 2 additional layout tests verified wide columns and a narrow window with font scale 1.3. Screenshots reviewed on Pleos for home, calendar and profile. The application currently explicitly uses a light theme; dark-theme support is not claimed.
