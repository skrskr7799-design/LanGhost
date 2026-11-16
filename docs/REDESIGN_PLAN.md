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
- [x] Replaced repeated dashboard tool shortcuts with a compact workspace group for Devices, Topology, Security and Tools.

### Remaining visual acceptance work

- [x] Complete a composition pass on the Tools hub, Ping/DNS and Public IP; avoid relying on color and shape changes alone.
- [ ] Build adaptive large-screen layouts with useful multi-pane content and verify phone / tablet / landscape separately.
- [ ] Unify permission, loading, stale, partial, empty and error states with consistent hierarchy and retry affordances.
- [x] Added a persistent privacy preference and centralized masking for IP, MAC and BSSID across dashboard, device inventory/detail, topology list, Wi-Fi, security findings/diffs, network profiles, WoL profile display, public IP, DNS/Ping results, clipboard, TXT/CSV exports and shared reports.
- [ ] Add purposeful transitions and scan feedback; keep motion restrained and respect reduced-motion / system animation settings where available.
- [ ] Review accessibility semantics, touch targets, contrast, TalkBack and system font scaling.
- [ ] Capture and review real screenshots on phone and tablet; visual sign-off is required before the redesign phase is considered complete.


### 2026-11-16 — adaptive workspaces and privacy coverage

- [x] Added a tablet device workspace with inventory on the left and a selected-device preview on the right; full device detail remains a separate destination.
- [x] Added a tablet topology workspace with the logical graph and complete discovered-node list side by side; the graph remains explicitly evidence-aware.
- [x] Added persistent identifier masking preference (off by default) and centralized IP/MAC/BSSID formatting.
- [x] Applied masking to the remaining identifier surfaces discovered in the audit: public IP, DNS/Ping results, security finding details, network profiles and Wake-on-LAN profiles.
- [x] Kept raw identifiers for network operations and profile actions; masking is presentation/export-only.
- [x] Added unit coverage for IPv4, IPv6, MAC/BSSID and disabled masking.

The adaptive and privacy changes passed full CI on commit `575663b19da6f76093e0d61e01b2571abf29818c` (Lint, unit tests, Debug APK and Release APK). Device-level visual review remains outstanding.


### 2026-11-16 — adaptive profiles, Wake-on-LAN and settings

- [x] Rebuilt Network Profiles as a two-pane tablet workspace: current network context and saved-profile inventory on the left, selected profile details and explicit activation/removal actions on the right.
- [x] Kept the compact phone flow focused on the profile list and direct activation; profile creation remains bound to the currently observed SSID/BSSID.
- [x] Rebuilt Wake-on-LAN as a device operations workspace with a saved-device inventory, a selected-device detail panel and a clear Magic Packet action on expanded screens.
- [x] Added an explicit explanation that successful packet transmission is not proof that the target device powered on.
- [x] Reworked Settings into a two-column tablet control workspace and a focused single-column phone flow; grouped monitoring/scanning separately from privacy/access/report/about.
- [x] Preserved notification permission handling, background watch worker controls, biometric availability checks, identifier masking, and report sharing.

These screens now have distinct information hierarchies instead of being simple vertical lists of interchangeable cards. They still require compile/CI verification and real-device visual review.


### 2026-11-16 — operations hub and diagnostic workspaces

- [x] Replaced the repeated two-column tool-card grid with an adaptive operations center: a featured core-diagnostics area, focused security/radio actions and separate device-management/configuration groups.
- [x] Rebuilt Ping/DNS around a target-node workflow and a dedicated result workspace; tablet uses a two-pane composition while phone keeps a single-column flow.
- [x] Made the operation type and result state explicit (awaiting input, in progress, response, attention); retained the existing TCP 80/443 probe semantics and system DNS resolver.
- [x] Rebuilt Public IP as an explicit external lookup workspace with clear disclosure, source/fallback explanation, responsive request/result panes and privacy-aware IP presentation.
- [x] Preserved all existing tool routes and callback behavior; no new network request is made automatically.
- [ ] Verify all new screen compositions through full CI and real-device review.


### 2026-11-16 — Wi-Fi environment and channel intelligence

- [x] Rebuilt Nearby Wi-Fi as an adaptive radio-environment workspace: discovered network inventory on expanded layouts, with band summaries and strongest-signal context alongside it.
- [x] Preserved scan permissions, freshness semantics, filters, sorting and privacy-aware BSSID display.
- [x] Rebuilt Channel Analysis around a selectable band spectrum map, explicit current/recommended channel markers, a channel inventory and an evidence/limitations panel.
- [x] Added explicit scan feedback and stale-data messaging; empty scan results are not represented as proof of an empty radio environment.
- [x] Kept channel recommendations grounded in the existing ChannelAnalyzer model; no regulatory support or real interference measurement is claimed.
- [ ] Verify all updated workspaces through full CI and real-device review.

### 2026-11-16 — Security Center redesign

- [x] Reframed the former findings list as an evidence-first Security Center with a clear posture summary and current network context.
- [x] Added a compact evidence-quality view for device discovery, TCP service auditing and nearby Wi-Fi observations.
- [x] Promoted the two meaningful next actions: refresh device/service evidence and scan the nearby Wi-Fi environment.
- [x] Made scan coverage and service-audit outcomes visible together, including filtered/time-out and unknown results.
- [x] Prioritized findings by severity and retained the distinction between a configuration difference and proof of an attack.
- [x] Preserved network-scope/freshness checks, privacy masking, scan permission handling and the rule that insufficient evidence withholds the score.
- [ ] Run full CI and inspect the new composition on a phone and expanded/tablet layout.

### 2026-11-16 — contextual permission and scan entry states

- [x] Removed the dashboard's automatic Wi-Fi permission prompt on first composition; permission is now requested only after an explicit user action.
- [x] Kept the permission notice visible with a direct, understandable action when access is missing.
- [x] Made the primary scan control request the required permission when needed, and start discovery only after permission is granted.
- [x] Corrected the LanGhost header network-orbit indicator to reflect the actual connection state instead of always appearing connected.
- [ ] Verify first-run, permission-denied, permission-granted and scan-start flows on Android; run full CI.


### 2026-11-16 — shared operation-state foundation

- [x] Added reusable loading, retryable error and data-quality notice primitives with the LanGhost visual language.
- [x] Added explicit informational/warning tone handling for stale or partial evidence messaging.
- [x] Kept the existing empty-state component and introduced focused primitives rather than a generic component with many style flags.
- [x] Applied the shared retryable error and data-quality notices to Wi-Fi scanning, including a clear distinction between never-scanned and completed-with-no-results states.
- [x] Added a five-minute freshness warning to the device inventory while preserving the existing never-scanned and filter-empty states.
- [x] Added an explicit stale-snapshot notice when nearby Wi-Fi evidence is older than the five-minute freshness window.
- [ ] Apply the shared primitives to dashboard, device inventory, Security and diagnostic workflows.
- [ ] Audit every operation for loading, permission-denied, partial, stale, empty, recoverable-error and success states.
- [ ] Add focused UI/state tests where the current test setup supports deterministic Compose coverage.

- [x] Dashboard now distinguishes disconnected network state from an active connection and flags device inventory older than five minutes.
- [x] Public IP lookup now uses shared error and partial-data notices; existing request controls remain the truthful retry path.
- [ ] Continue the same state audit across diagnostic tools, topology, device details and network profiles.


### 2026-11-16 — topology evidence and device port-scan states

- [x] Added explicit topology notices for never-scanned, stale (older than five minutes) and truncated discovery evidence; retained the distinction between logical grouping and physically verified links.
- [x] Device port scanning now distinguishes never-run from a completed scan with no detected open ports.
- [x] Port-scan failures retain the last known result and expose a truthful shared error state with a working retry action.
- [x] Active port checks use the shared loading treatment rather than an ambiguous empty-state message.
