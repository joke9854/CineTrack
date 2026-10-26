---
name: CineTrack
description: A dark private screening room for your watch history; artwork glows, controls are quiet frosted glass.
colors:
  background-0: "#1C1B22"
  background-1: "#2A2932"
  glass-surface: "#171B21"
  scrim: "#080A0C"
  text-primary: "#F8F9FB"
  text-secondary: "#D7DEE4"
  text-muted: "#A2AEB8"
  accent-blue: "#6AA8FF"
  accent-blue-light: "#9AC4FF"
  accent-green: "#2E7B29"
  accent-silver: "#D2DDE8"
  accent-champagne: "#BC9A66"
  info: "#4FC7E8"
  violet: "#9B7CF0"
  gold: "#F6B23E"
  error: "#FF7C83"
  status-watching: "#1572B1"
  status-planned: "#FFC75D"
  status-paused: "#D46F0F"
  status-completed: "#06804B"
  status-dropped: "#BA1600"
  success-bright: "#5DCAA5"
  danger-action: "#B3423F"
  danger-text: "#F09595"
  progress-track: "#4A4E57"
  glass-fill: "rgba(255,255,255,0.12)"
  glass-strong: "rgba(255,255,255,0.17)"
  glass-border: "rgba(255,255,255,0.20)"
typography:
  display:
    fontFamily: "Manrope, sans-serif"
    fontSize: "29px"
    letterSpacing: "-0.6px"
  headline:
    fontFamily: "Manrope, sans-serif"
    fontSize: "24px"
    letterSpacing: "-0.4px"
  title-large:
    fontFamily: "Manrope, sans-serif"
    fontSize: "22px"
    letterSpacing: "-0.3px"
  title-medium:
    fontFamily: "Manrope, sans-serif"
    fontSize: "18px"
    letterSpacing: "-0.2px"
  title-small:
    fontFamily: "Manrope, sans-serif"
    fontSize: "16px"
  body-large:
    fontFamily: "Manrope, sans-serif"
    fontSize: "16px"
    lineHeight: "24px"
  body:
    fontFamily: "Manrope, sans-serif"
    fontSize: "14px"
  body-small:
    fontFamily: "Manrope, sans-serif"
    fontSize: "13px"
  label:
    fontFamily: "Manrope, sans-serif"
    fontSize: "12px"
    fontWeight: 500
  label-small:
    fontFamily: "Manrope, sans-serif"
    fontSize: "10px"
  eyebrow:
    fontFamily: "Manrope, sans-serif"
    fontSize: "10px"
    letterSpacing: "1.6px"
rounded:
  compact: "8px"
  small: "12px"
  medium: "16px"
  large: "22px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  xxl: "24px"
  xxxl: "32px"
components:
  card-glass:
    backgroundColor: "{colors.glass-surface}"
    rounded: "{rounded.large}"
  icon-button-glass:
    backgroundColor: "{colors.glass-surface}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.pill}"
    size: "48px"
  status-pill:
    backgroundColor: "#121419"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.pill}"
    padding: "8px 18px 6px 14px"
    width: "260px"
  progress-card-poster:
    rounded: "{rounded.small}"
    width: "82px"
  button-danger:
    backgroundColor: "{colors.danger-action}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.pill}"
---

# Design System: CineTrack

Sizes are written in px for portability; on Android read every px as dp (and sp for text). Source of truth: `app/src/main/java/com/cinetrack/ui/theme/` (Palette, Theme, Type, Shapes, Spacing, Motion) and `ui/components/GlassStyle.kt` / `GlassModifiers.kt`.

## Overview

**Creative North Star: "The Private Screening Room"**

CineTrack is a dark room where the films supply the light. The page is a near-black, faintly violet ground; posters, backdrops and logos are the brightest, most saturated things on screen, and they stay crisp. Everything the user touches (tab bar, back button, filters, status pills, the watch check) is frosted glass floating over that art: low contrast at rest, lit softly along its top edge, springing under the finger.

The system is dark-only by design: the theme always builds a dark color scheme and light system bars. Colour outside the artwork is rationed: one user-chosen accent, a fixed set of status colours, and semantic success/danger tones. Density is comfortable rather than compact; rails, mosaics and progress lists let artwork carry the hierarchy, with type kept quiet and slightly tight.

Glass has two engines with one look. On Android 13+ controls and the bottom bar use Haze liquid glass refracting the page; below that, buttons fall back to a flat tinted fill and bars and sheets keep a Haze blur. Neither path may change layout.

**Key Characteristics:**
- Dark-only, near-black violet-grey ground; artwork is the light source.
- Frosted glass on overlays and controls only, never as a full-card layer over posters.
- One selectable accent (Blue default, Green, Silver, Champagne) plus fixed status colours.
- Manrope throughout, slightly negative tracking on titles, tracked uppercase eyebrows.
- Soft two-step lift and a top-lit hairline rim instead of Material elevation.
- Spring-driven motion: snappy selections, a bouncy watched check, gentle list movement.

## Colors

A cool near-black stage, translucent white glass, and colour that comes from the films, the accent and status.

### Primary
- **Projector Blue** (accent-blue): the default user accent. In the Material scheme the primary role is its lighter mix, **Projector Haze** (accent-blue-light, the accent lerped 32% toward white), used for selected tabs, progress, links and focus; the container role is the accent at 24% alpha.
- **Alternate accents**: **Ticket Green** (accent-green), **Screen Silver** (accent-silver), **Velvet Champagne** (accent-champagne). The user picks one in Appearance; it replaces Projector Blue everywhere the accent is read. Never hard-code the blue where the accent is meant.
- **Poster accent**: detail pages may take an accent from their poster (`LocalDetailAccent`); it is optional and null when turned off.

### Secondary
- **Marquee Cyan** (info): secondary role; informational highlights and air-time hints.

### Tertiary
- **Usher Violet** (violet): tertiary role; sparing secondary emphasis. **Award Gold** (gold): ratings and stars.

### Status
- **Watching** (status-watching), **Plan to watch** (status-planned), **Paused** (status-paused), **Completed** (status-completed), **Dropped** (status-dropped). One colour per status, used on library chips, pills and markers. **Bright success** (success-bright) is for dots, checks and all-clear cards; **error** (error) is the scheme error; destructive actions use **Danger Red** (danger-action) fills and **Danger Rose** (danger-text) text on dark.

### Neutral
- **Screening Room Black** (background-0): the opaque page background and the colour every blur is composited over.
- **Aisle Grey** (background-1): the Material surface role; opaque sheets and raised regions.
- **Glass Base** (glass-surface): the base of every glass material; cards use it at 30% with a faint cool lift, controls at 58%, plain content at 27%.
- **Scrim** (scrim): page fades and the dark fade behind artwork; used at whatever alpha the fade needs.
- **Text**: primary (text-primary) for titles and body, secondary (text-secondary) for supporting text and `onSurfaceVariant`, muted (text-muted) for metadata and timestamps.
- **Glass whites** (glass-fill, glass-strong, glass-border): translucent white for surface variants and outlines.

### Named Rules
**The Films Are the Light Rule.** Saturated colour on a screen comes from artwork, the accent or a status. Chromatic gradients stay on the page background; glass fills stay neutral so artwork colour passes through.

**The One Accent Rule.** Read the accent from the theme (`Accent` / `AccentLight` / `colorScheme.primary`); never pick a second brand colour.

## Typography

**Display Font:** Manrope (one variable file, weights 400–800, SIL OFL)
**Body Font:** Manrope
**Label Font:** Manrope

**Character:** A geometric-humanist sans that reads clean on near-black; titles tighten slightly (negative tracking) to feel cinematic, while labels stay open.

### Hierarchy
- **Display** (29px, -0.6px): title-page headings over artwork.
- **Headline** (24px, -0.4px): screen and section headers.
- **Title** (22 / 18 / 16px, -0.3 / -0.2 / 0px): card titles, sheet titles, list rows.
- **Body** (16 / 14 / 13px): overviews and descriptions (large), default text (14), supporting rows (13).
- **Label** (14 / 12 / 10px): buttons, pills, chips, metadata.
- **Eyebrow** (10px, 1.6px tracking, uppercase): section names and card overlines.

Weights and line heights are set by the semantic components, not the type scale; keep them there.

### Named Rules
**The Seven Sizes Rule.** Use the theme's scale (29, 24, 22, 18, 16, 14, 13, plus 12/10 labels). Don't introduce one-off font sizes.

## Layout

Edge-to-edge: the app draws behind both system bars (`decorFitsSystemWindows = false`), so artwork can run under the status bar and the glass bottom bar floats over content. Pages respect insets with padding, not by shrinking the canvas.

Spacing follows a 4px base (xs 4, sm 8, md 12, lg 16, xl 20, xxl 24, xxxl 32); 16 is the default page gutter and card padding. Posters are always 2:3. Content is organised into horizontal rails (Discover), an endless Mosaic grid, and vertical progress and library lists. Wider screens (tablets, foldables, landscape) use the extra room with more columns rather than wider cards.

## Elevation & Depth

Depth is a hybrid of translucency and a soft painted lift, never Material tonal elevation or hardware shadows on cards.

### Shadow Vocabulary
- **Card lift**: two painted offsets behind the shape (2px at 7.2% black, 7px at 2.6% black). Painted manually because hardware shadow layers produced pale rectangles on some Samsung / Android 16 renderers.
- **Icon control lift**: 8px shadow around small circular glass buttons, with 6px of padding so the shadow isn't compressed into the circle.
- **Floating pill**: 16px black ambient + spot shadow under status pills.

### Rims
- **Card rim**: 0.9px vertical gradient (white 20% at top → 8.5% → 5.5% → 9% at bottom): light catches the top edge, a little bounce at the bottom.
- **Control rim**: the same shape one step quieter (13% → 5.5% → 3.6% → 5.9%), 0.55–0.6px, for controls, the nav bar and popups.

### Blur
Navigation and floating sheets blur the page (16px radius, Screening Room Black at 58% tint, 3% noise), with a 90% opaque fallback where blur is off.

### Named Rules
**The Lit Rim Rule.** Depth comes from a top-lit hairline and a faint lift. No heavy elevation, no grey rings around small buttons.

**The Glass on Controls Rule.** Glass goes on overlays and controls (pills, progress indicators, buttons, bars, sheets), never as a full-card layer over poster art.

## Shapes

Gently rounded throughout: compact 8px (small tags, thumbnails), small 12px (posters, inputs), medium 16px (sheets, grouped rows), large 22px (glass cards, the default), pill 999px (status pills, buttons, segmented controls, the tab selection). Small icon controls are circles. Hairline gradient rims, never solid strokes, define edges.

## Components

### Buttons
- **Feel:** quiet, tactile glass.
- **Icon buttons:** circular glass control (Glass Base at 58%, control rim, icon lift), 48px touch target. Back, search, filter, refresh and bulk edit all use it.
- **Pressed:** cards and tappable surfaces show a 1.7px accent rim that fades in over 180ms; controls shrink and spring back on the press spring.
- **Destructive:** Danger Red fill with primary text, and a confirmation before data is removed.

### Chips / Status
- One colour per library status; one name per status across the app.
- Sort and view pills use the same frosted glass as the bottom bar.

### Cards / Containers
- **Corner Style:** large (22px).
- **Background:** card glass (Glass Base at 30% with a 7.5% cool lift).
- **Shadow Strategy:** card lift + card rim (see Elevation).
- **Internal Padding:** 16px.

### Navigation
- Floating glass bottom bar (liquid glass on Android 13+, blur below), with a clearer glass tab selection. Tab and pushed-page transitions use one emphasized tween (420ms).

### Status Pill (signature)
A floating pill (260–360px wide, near-opaque #121419 at 94%, control rim, 16px shadow) that announces sync and update state as a polite live region. A spinner turns into a check or cross on the bouncy spring; its tint animates to Completed green or Dropped red; an optional progress line fills on the gentle spring. It slides down from the top.

### Progress Card (signature, approved 2026-10-11)
Art behind a dark fade, an 82px poster, remaining time beside the title, a ✓ watch button, and a progress line along the bottom edge. Swipe left to mark watched; the confirmation holds 720ms. This layout is fixed: change it only when the user asks.

### Motion
Durations: quick 90, short 180, medium 300, long 420, extended 480ms, on Material 3 emphasized and standard easings. Springs: **snappy** (selections, toggles), **bouncy** (the watched check), **press** (pressed controls), **gentle** (list items, expanding sections).

## Do's and Don'ts

### Do:
- **Do** read colours from the theme and `SurfacePalette`; add a named token rather than an inline hex.
- **Do** keep poster and backdrop art crisp and at 2:3 for posters.
- **Do** put glass on controls and overlays, with the card or control rim.
- **Do** keep the liquid-glass and blur fallbacks visually equivalent and layout-identical.
- **Do** use the Motion springs and durations instead of ad-hoc animation specs.
- **Do** show design changes on the phone before committing them.

### Don't:
- **Don't** add a light theme or light system bars.
- **Don't** lay glass over an entire poster card or blur the artwork itself.
- **Don't** use hardware shadows on cards; use the painted lift.
- **Don't** put grey rings around small glass buttons.
- **Don't** introduce a second brand colour or hard-code the default blue where the user's accent belongs.
- **Don't** change the approved Progress card layout unless the user asks.
