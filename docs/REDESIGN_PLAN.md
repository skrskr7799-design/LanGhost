# LanGhost — UI/UX Redesign Plan

> Status: Phase 5 active
> Branch: `work/data-architecture`
> Baseline: `4cc6d383df5ceda30f171072c93820ec4d7ce55d`

## Product experience

LanGhost should feel like a premium network observability and diagnostics tool: calm, precise, information-dense without being cluttered, and clearly trustworthy about what it has actually observed. It should not look like a generic settings app, a stack of identical cards, or a fictional hacker terminal.

The visual language is a **quiet network operations console**:
- Dark-first graphite/navy surfaces with subtle tonal separation, restrained cool-blue/cyan accent, and semantic green/amber/red reserved for actual state.
- Strong typography hierarchy: concise page titles, prominent key metrics, compact technical metadata, readable secondary explanations.
- Deliberate negative space and asymmetric composition; avoid wrapping every value in a card.
- Fine separators, restrained borders, consistent radii and a small elevation vocabulary.
- Small purposeful motion for state transitions, scan progress and navigation; no constant pulsing or decorative animation.
- Charts and topology should explain relationships and evidence, not merely decorate the screen.

A complete light theme remains a design decision after the dark system is established. Do not add external font or animation dependencies without a clear benefit.

## Information architecture

### Primary destinations
1. **Overview** — current network health, connected network identity, online/offline summary, scan status/coverage, recent meaningful changes, primary scan action.
2. **Devices** — searchable/filterable inventory, online state, device type, favorites/watched and clear confidence cues.
3. **Topology** — observed network relationships with explicit distinction between discovered facts and inferred links.
4. **Wi-Fi** — current connection and nearby access-point analysis, signal/channel/band/security.
5. **Tools** — grouped diagnostics and utilities: security audit, ping/DNS, public IP (explicit user action and disclosure), Wake-on-LAN, profiles, channel analysis and settings.

Device detail remains a contextual destination reachable from Devices and Topology. Settings should remain easy to reach from the app shell, not compete with primary operational destinations. Preserve all existing routes and functionality during migration; secondary destinations can live in categorized tool pages rather than disappearing.

### Adaptive shell
- Compact phones: persistent bottom navigation for the primary destinations, with secondary destinations grouped under Tools; preserve back-stack behavior and deep links.
- Expanded/tablet: navigation rail or compact side navigation plus a flexible content/detail layout. Avoid the current hard-coded 40/60 split as the only tablet composition.
- Large screens and landscape: use available width for useful multi-pane layouts, not simply stretched phone cards.
- Respect system insets, font scaling and touch target minimums.

## Design tokens

Establish a single source of truth for:
- Color roles: canvas, elevated surface levels, outline/divider, primary accent, text tiers, success, warning, danger and informational state.
- Spacing scale: 4/8/12/16/20/24/32/40 dp.
- Shape scale: compact controls, standard surfaces, prominent hero surfaces and fully rounded status indicators.
- Typography roles for display metrics, page title, section title, body, technical/monospaced metadata and compact labels.
- Motion durations/easing for navigation, expand/collapse, scan state and data refresh.
- Icon sizes and semantic usage.

Components should be semantic and reusable: page header, section heading, metric, status indicator, network identity header, evidence/coverage panel, action row, empty state, loading state, error/retry state, filter chip and device row. Avoid a generic component that accepts dozens of style flags.

## Screen composition

### Overview
A high-quality opening screen, not a menu:
- Header with product identity, current connection state and quick settings.
- Dominant network overview area with SSID/network scope, gateway/subnet and connection quality.
- A concise metric row for online devices, scan coverage and relevant security/evidence state.
- A clear scan control with progress and last-scan context.
- Recent changes/attention items only when real data exists.
- Secondary tools shown as compact grouped shortcuts, not eleven full-width buttons.

Never imply that zero devices or zero findings is known when no scan/evidence exists. Preserve unknown, stale, partial and truncated states.

### Devices and device detail
- Search and filters (online/offline, type, favorites/watched) with clear counts.
- Dense but readable device rows, meaningful visual identity by device class, online state and compact IP/vendor metadata.
- Detail page organized into overview, identity, observed services, history and actions.
- Clearly distinguish observed data from guesses (OS/type/vendor confidence).
- Privacy masking must be consistently applied to displayed, copied, shared and exported identifiers once implemented.

### Topology
- Visual hierarchy centered on gateway/router and discovered devices.
- Show only evidence-backed edges; inferred relationships must be visually distinct and explained.
- Provide list fallback, selection details, zoom/fit and accessibility semantics.
- Avoid claiming physical topology from simple subnet membership.

### Wi-Fi, Security and Tools
- Use comparison and data visualization where it clarifies signal/channel/security evidence.
- Security overview must explain score availability, freshness, coverage and why a score is withheld.
- Tools should be grouped by task, with explicit user action before external lookups or network operations.
- Each screen needs designed empty/loading/partial/error states, not generic blank areas.

## Implementation sequence

1. **Foundation** — create semantic color/spacing/shape/motion tokens and reusable Compose primitives. Keep current app compiling.
2. **Application shell** — responsive phone bottom navigation and tablet navigation rail/adaptive content shell; preserve every existing route.
3. **Overview vertical slice** — build the visual language in the most visible screen, including unknown/partial/scanning/complete states.
4. **Devices** — list, search/filter interaction and device detail.
5. **Topology** — evidence-aware graph/list presentation.
6. **Wi-Fi and Security** — analysis-oriented layouts, charts and clear evidence status.
7. **Tools, Profiles and Settings** — consistent task grouping and forms.
8. **Polish** — motion, haptics where useful, accessibility, localization, font scaling, performance and full regression testing.

Each slice must pass Lint, unit tests and Debug APK build; full CI (including Release APK) at major checkpoints. Preserve existing behavior unless a change is explicitly documented and tested. Do not merge to main or publish a release without approval.

## Acceptance criteria

- A consistent, recognizable LanGhost visual identity across every screen.
- The overview is a useful live network dashboard, not a navigation menu.
- Primary navigation is persistent and adapts to phone/tablet/landscape.
- Every existing feature remains reachable.
- Loading, empty, permission-denied, partial-coverage, stale, error and success states are intentional and truthful.
- Charts/topology are legible, evidence-aware and performant.
- Touch targets, contrast, TalkBack semantics and system font scaling are reviewed.
- No sensitive identifiers leak through masking, screenshots, clipboard, reports or exports.
- CI remains green and the app remains usable on supported Android API levels.


## Implementation log — premium visual pass

### 2026-11-16 — visual foundation and first screen-wide pass

- [x] Replaced the generic dark palette with a graphite / arctic-blue / signal-cyan identity and restrained semantic status colors.
- [x] Rebuilt the typography scale with expressive editorial headings and a dedicated monospace style for network identifiers and technical values.
- [x] Added a coherent Material shape scale and upgraded shared page headings, metrics, status indicators, panels and empty states.
- [x] Created the LanGhost network-orbit signature graphic and introduced it into the overview and expanded navigation identity.
- [x] Reworked the overview network hero with the signature graphic, ambient background treatment and clearer technical hierarchy.
- [x] Applied the visual language to Devices, Device Detail, Topology, Nearby Wi-Fi, Security, Channel Analysis, Public IP, Network Profiles, Wake-on-LAN, Utilities and Settings.
- [x] Added a technical grid to the topology visualization while retaining the explicit logical-grouping disclaimer.
- [x] Added an animated evidence-aware security score dial; an unavailable score remains visually unavailable rather than being replaced with a guessed value.

### 2026-11-16 — grouped Tools hub

- [x] Added a dedicated Tools destination that groups security, Wi-Fi analysis, diagnostics, public IP, Wake-on-LAN, profiles and settings by operational task.
- [x] Preserved the existing individual routes and added a dashboard entry point; no existing tool was removed or hidden.
- [ ] Replace the remaining repeated dashboard tool shortcuts with a more compact overview action group after the new hub has passed CI and interaction review.

### Remaining visual acceptance work

- [ ] Complete a composition pass on each screen; avoid relying on color and shape changes alone.
- [ ] Build adaptive large-screen layouts with useful multi-pane content and verify phone / tablet / landscape separately.
- [ ] Unify permission, loading, stale, partial, empty and error states with consistent hierarchy and retry affordances.
- [ ] Implement end-to-end privacy masking for identifiers across display, copy, share and export.
- [ ] Add purposeful transitions and scan feedback; keep motion restrained and respect reduced-motion / system animation settings where available.
- [ ] Review accessibility semantics, touch targets, contrast, TalkBack and system font scaling.
- [ ] Capture and review real screenshots on phone and tablet; visual sign-off is required before the redesign phase is considered complete.
