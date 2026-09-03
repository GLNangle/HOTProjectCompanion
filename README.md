# HOT Project Companion

An early, read-only JOSM plugin for carrying HOT Tasking Manager project context into the mapping workflow.

Maintainer: Gemma Louise Nangle

Project page: https://github.com/GLNangle/HOTProjectCompanion

## Current release

Version 1.3.2 provides:

- a dockable **HOT Project Companion** sidebar in JOSM with independently collapsible sections whose states persist across restarts;
- a dismissible first-use tip explaining that JOSM's pin icon can detach the companion into a separate, resizable window;
- automatic project and task detection from the JOSM task-boundary layer;
- separate project and task fields as a manual fallback, with no URL required;
- live read-only project instructions and per-task instructions;
- inline thumbnails for HTTPS images embedded in project or task instructions, with alt text and a full-size browser link;
- authorised imagery with extracted offset/alignment notes and cautious alignment guidance;
- previous task comments, invalidation warnings and recorded mapping issues;
- split-task recognition and 30-day, project-scoped recovery of source-task feedback when HOT omits it from a child task;
- changeset comment, hashtags and source/imagery details;
- an explicit reminder that only project-authorised imagery will be considered.
- a local **Ask about this task** field that answers with the conclusion first, quotes only the
  single best matching instruction and says plainly when the available wording does not answer the
  question;
- a local, automatic **Building check** for one selected closed outline, using a captured view of the currently visible project-authorised imagery;
- automatic measurements of roof consistency, contrast with the surroundings, visible boundary strength and directional shadow evidence;
- footprint shape shown only as a diagnostic, never as evidence that the imagery contains a building;
- comparison with task-instruction images when their captions clearly identify building or non-building examples;
- a 0–100 visual match score labelled Likely building, Uncertain or Unlikely building, with supporting evidence and cautions.
- read-only **Task building reconnaissance** inside the detected HOT boundary;
- separate **Scan entire task** and **Scan visible area** actions, with partial-scan counts limited
  to the displayed part of the task boundary;
- persistent **Conservative**, **Balanced (Recommended)** and **Exploratory** sensitivity modes,
  with concise in-plugin guidance about each mode and an explicit warning that Exploratory will
  include more non-buildings;
- denser candidate placement and an additional mid-elongated roof proportion in Exploratory mode,
  preventing clear buildings from being skipped merely because a coarse template grid missed their edges;
- a short explanation of the boundary, shadow, texture and contrast evidence that caused each
  candidate to be shown;
- reviewed-location exclusions that apply to every later scan of the same task, preventing accepted,
  rejected, mapped and confirmed mapped-building locations from being highlighted again;
- a neutral **Outside my area** outcome for boundary-cut or partial items, which hides the location
  from later scans without recording a positive or negative learning example;
- exact counts of downloaded closed building ways classified as rectangular/orthogonal, round or other;
- conservative estimates of possible unmapped rectangular, round, L-shaped and uncertain candidates from the rendered authorised imagery;
- a review checklist with annotated thumbnails and adaptive, centred close-ups calculated from
  each candidate's real map dimensions rather than the zoom used for the scan;
- persistent **Closer**, **Wider** and **Reset** review controls with seven fine-grained zoom positions;
- a temporary labelled candidate overlay that can be hidden and shown without changing the close-up;
- a temporary toggle for hiding and restoring mapped `building=*` outlines while checking the imagery beneath them;
- a separate, cautious shortlist of mapped building outlines with unusually weak visual evidence in the current authorised imagery;
- labelled close-up review for those mapped outlines, without deleting, retagging or treating the flag as proof of an error;
- a review workflow where rejected candidates leave the active list but remain recoverable, while accepted candidates wait for deliberate manual tracing;
- **Map this building** navigation and a read-only check for a completed closed `building=*` outline over the candidate;
- a button that returns to the complete task overview.
- persistent, local-only learning from candidates confirmed as mapped or rejected;
- a **Learn from buildings drawn since scan** action for genuine buildings the scan missed;
- cautious adaptive scoring where rejected examples immediately suppress similar detections;
- positive learning that activates only after both positive and negative examples exist, never
  bypasses the conservative imagery gates and cannot admit a baseline score below 56;
- a per-task learning cap that prevents one unusual task from dominating the local profile;
- a **Local learning** summary and history window that remain available after the mapper leaves a task;
- **Sync validation outcomes**, which checks the saved project/task numbers against HOT without
  requiring the submitted task to be reopened;
- an off-by-default **Shared learning — controlled test** with explicit consent, a local queue,
  manual submission, retained withdrawal receipts and a plain-language disclosure of every field;
- anonymous contribution of project/task numbers, decision time, a one-way imagery identifier,
  building decision, shape, numeric visual evidence and selected geometry-correction flags;
- no shared imagery pixels, screenshots, candidate coordinates, geometry, comments, mapper names,
  OSM usernames, email addresses or login credentials;
- download of a thresholded multi-mapper aggregate which remains inactive until the service has
  enough validated examples from at least five contributors, and whose client-side influence is
  capped below the scanner's local hard safety gates.

It does **not** use an AI image classifier, claim a statistical probability, move imagery, modify
OSM features, upload OSM data, send captured map images, change task status, post comments or access
private projects. Shared statistical contribution is optional, disabled by default and manually
triggered during the controlled test.

Version 0.1.1 fixes the custom sidebar icon path for JOSM 19613. Version 0.1.2 corrects the local build signature for `MapFrame.addToggleDialog`. Version 0.1.3 recognises names such as `Boundary for task: 168 of TM Project #58879 – Do not edit or upload`. Version 0.1.4 loads public project and task context from the HOT Tasking Manager API. Version 0.1.5 preserves detailed feedback through the split-task workflow. Version 0.1.6 displays images referenced by project and task instructions.

Version 0.2.0 adds the guided, local-only Building check.

Version 0.2.1 recognises equivalent HOT and JOSM imagery names when HOT removes spaces from an identifier, such as `EsriWorldImagery` versus `Esri World Imagery`.

Version 0.2.2 corrects the JOSM 19613 selected-way API signature and converts future linkage incompatibilities into an in-panel warning instead of a JOSM error alert.

Version 0.3.0 replaces the five-question mapper checklist with one-click, local visual analysis. It measures the selected outline automatically and uses clearly labelled task images as additional evidence without treating ambiguous instruction images as positive examples.

Version 0.3.1 removes mapper-drawn footprint shape from the score, hides vector layers while capturing the analysis image, adds strongly weighted directional shadow evidence, reuses task images already loaded by the sidebar and reports example-image loading failures explicitly.

Version 0.4.0 adds Task building reconnaissance: mapped-footprint inventory, local candidate scanning and a clickable read-only review checklist within the HOT task boundary.

Version 0.4.1 recognises HOT task boundaries whose closing coordinate is represented by a different temporary node ID, fixing the false “boundary geometry could not be read” message.

Version 0.4.2 makes candidate review unambiguous: JOSM zooms to a padded close-up, draws a temporary labelled highlight over the candidate and can return directly to the complete task overview.

Version 0.4.3 adds a Hide candidate outline / Show candidate outline control, allowing mappers to compare the marked candidate with the unobstructed imagery while keeping the same close-up.

Version 0.4.4 adds mapper-controlled accept/reject decisions to each reconnaissance candidate. Decisions stay visible in the checklist, update the review totals and can be changed until the next scan, without creating or modifying OSM data.

Version 0.4.5 turns those decisions into a safer mapping workflow. Rejected candidates disappear from the active list but remain available in a collapsed recoverable section. Accepted candidates move to Awaiting manual mapping. Map this building opens an unobstructed close-up for normal JOSM tracing, and Check if mapped moves the candidate to Mapped only after detecting a complete closed `building=*` way over its centre.

Version 0.4.6 keeps the companion sidebar at its existing scroll position when a candidate is accepted, rejected, restored or marked as mapped. This prevents Swing focus transfer and checklist reconstruction from making the panel jump during review.

Version 0.4.7 anchors the next visible candidate to its current on-screen position when the preceding candidate leaves the active list. This prevents the remaining checklist rows from visibly leaping upward after an accept or reject decision.

Version 0.4.8 makes reconnaissance substantially more conservative. A candidate now needs a coherent directional shadow as well as minimum contrast and boundary evidence, strongly vegetation-coloured regions are excluded, the admission threshold is higher, and the shortlist is capped at 16 rather than 24 candidates.

Version 0.4.9 adds **Hide mapped building outlines** to reconnaissance. It uses JOSM's filter model to hide `building=*` objects temporarily, preserves all existing mapper filters, and restores the outlines when requested, when the task context resets, when mapping begins or when the companion is hidden or destroyed.

Version 0.4.10 prevents the companion's action buttons from taking mouse focus and explicitly releases any checklist focus before rebuilding candidate rows. This removes the remaining first-click sidebar jump while preserving keyboard focus traversal.

Version 0.5.0 adds the first safe adaptive-learning foundation. Mapper-confirmed and rejected
candidate evidence is retained locally; newly drawn buildings can be collected as higher-value
missed positives. After at least four examples in each class, the local profile cautiously adjusts
candidate scores while the original shadow, contrast, boundary and vegetation requirements remain
mandatory. Learning history persists independently of the active task, and saved HOT task states
can be synchronised later. Object-level validator edits are not yet used: the current sync records
task outcomes only until uploaded OSM IDs can be matched reliably.

Version 0.5.1 fixes rejection learning and tightens candidate admission. A rejection now immediately
lowers the scores of visually similar proposals, even if the mapper has not confirmed four genuine
buildings. Repeated similar rejections strengthen the suppression. Positive learning remains capped
at four score points and cannot promote a proposal whose unlearned score is below 56. JOSM also
confirms in the review message whenever a rejection has been saved as a learning example.

Version 0.6.0 adds mapped-building review. During reconnaissance, up to ten downloaded mapped
building outlines with visual evidence below 45/100 are listed separately, weakest first. Each can
be opened as a labelled close-up and checked with mapped outlines visible or hidden. A weak score is
only a review flag: imagery age, obstruction, resolution and alignment may all explain it, so the
plugin never deletes, retags or automatically learns from a flagged existing building.

Version 0.6.1 adds a per-item **Show / hide review highlight** control to mapped-building review.
The mapper can remove the labelled flag while keeping the same close-up, inspect the authorised
imagery underneath, and restore the marker without changing the mapped building.

Version 0.6.2 adds explicit mapped-building review decisions. **Confirm building** records a
positive local learning example; **Not a building** records a negative example and places the item
in a recoverable manual-correction section without deleting or retagging OSM data. **Restore review**
returns the item to the active list and removes its learning contribution.

Version 0.7.0 moves persistent learning, task history and split-task feedback into JOSM's preferred
plugin-prefixed preferences system. A one-time migration retains data written by v0.6.2 and earlier,
after which JOSM preferences become the authoritative store. It also lets mappers record, in any
combination, that a reviewed mapped building or a newly mapped candidate was moved, rotated,
reshaped or resized. These geometry outcomes are stored separately from building/non-building
imagery evidence so a valid but corrected building never becomes a false-negative training example.
The plugin measures the confirmed before/after geometry and stores a bounded profile against a
one-way key for the authorised imagery description. After at least four position or size corrections
for that imagery, future candidate review markers receive a capped calibration: centre shifts are
limited to 12% of the candidate dimensions and width/height scaling to 85–118%. Rotation and shape
measurements are retained for later oriented-boundary work but do not rotate or reshape OSM data or
candidate geometry in this release.

Version 0.7.1 makes task reconnaissance substantially more conservative after field testing showed
too many obvious non-buildings. Rectangular candidates must now show continuous edge evidence around
all four sides rather than merely strong average edges, while round candidates require continuous
radial edge coverage. Both shapes use a stricter roof-surface texture gate, stronger contrast and
directional-shadow gates, a higher unlearned baseline, a smaller twelve-item shortlist and a minimum
candidate size. The scan uses a finer grid and an additional 4:3 footprint template so these stricter
checks do not reject a real roof simply because the search window landed a few pixels away from its
edge. Local positive learning still cannot bypass any of these hard gates.

Version 0.7.2 separates genuine validation outcomes from ordinary Tasking Manager status changes.
The status check lists the affected project and task, treats the first public status retrieval as a
baseline rather than a validation outcome, and only labels `VALIDATED` or `INVALIDATED` as an
outcome. It also keeps status messages to a stable sidebar width and guards the containing viewport
during its first mouse interaction to prevent the intermittent initial-click jump. Status checks now
run only when requested, avoiding an asynchronous startup reflow while the sidebar is settling.

Version 0.7.3 fixes an intermittent first-analysis capture mismatch. The Building check now allows
JOSM's pending map repaint to complete before capture and forces a fresh, non-double-buffered render
of the authorised imagery. The task reconnaissance capture uses the same fresh-render approach, so
the preview and score should no longer be based on the previous map frame.

Version 0.7.4 extends split-task feedback retention from 24 hours to 30 days. It retains a small
project-scoped history and binds each child task to the first eligible source comment it inherits, so
later work in the same project cannot silently replace that child task's feedback. The cache remains
local to the mapper's JOSM installation and never writes comments back to HOT.

Version 0.7.5 fixes an off-screen map capture crash in the Building check and Task building
reconnaissance. It supplies the clipping rectangle required by JOSM's layer renderer and turns any
temporary capture failure into a normal retry message instead of a JOSM exception dialogue.

Version 0.8.0 makes all seven major sidebar sections independently collapsible and remembers each
choice in JOSM preferences. On first use, What to map, Required imagery and Previous feedback start
open; the optional learning, analysis, reconnaissance and upload sections start collapsed. Hidden
sections continue receiving live updates and show the latest information when reopened.

Version 0.8.1 safely clamps visual-evidence sampling at every image edge. A newly drawn outline that
touches or slightly crosses the captured imagery boundary can no longer cause an array-bounds error
when using Learn from buildings drawn since scan; one unusable outline is skipped without preventing
the remaining new buildings from being considered.

Version 0.9.0 adds cautious project-specific questions and visible-area reconnaissance. **Ask about
this task** searches only the loaded project instructions, required-imagery guidance and task
feedback, displays the supporting wording and explicitly declines to infer an answer when the match
is incomplete. Reconnaissance now offers **Scan entire task** and **Scan visible area**. The latter
analyses only the current viewport where it intersects the task boundary and limits both mapped
inventory and candidate results to that subsection, giving roofs more useful rendered detail without
requiring the complete task boundary to fit on screen.

Version 0.9.1 makes the new visible-area scan scale-aware. Candidate template sizes are derived from
JOSM's metres-per-pixel scale, preventing deep zoom from turning tiny ground variations into
physically plausible building proposals. If local geometry learning moves or resizes a review
marker, the adjusted location must retain comparable visual evidence; otherwise the original
evidence-aligned marker is kept.

Version 0.10.1 adds three reconnaissance sensitivity modes. Conservative is the default and applies
the strongest boundary, texture, contrast and directional-shadow gates with a maximum of eight
candidates; Balanced and Exploratory progressively widen the review net. Candidate rows now explain
the visual evidence that caused them to be retained. **Rescan after review** restores the previous
scan extent and suppresses locations already accepted, rejected or mapped in the current task
session, while newly mapped building outlines are also excluded through the normal mapped inventory.
A normal **Scan entire task** or **Scan visible area** starts a fresh review session and includes
those locations again.

Version 0.11.0 adds the first opt-in client for privacy-preserving shared learning. Sharing is off by
default. After reading an explicit disclosure, a mapper can allow new confirmed, rejected and
scan-missed building examples to queue locally, review the queue count and press **Send queued
examples** manually. The service receives only project/task numbers, decision time, a one-way hash
of the authorised-imagery description, building/not-building and rectangular/round decisions, five
numeric visual measurements and moved/rotated/reshaped/resized flags. Imagery pixels, screenshots,
candidate coordinates, outlines, comments and identity are never included. Service receipts remain
on the mapper's computer so sent examples can be withdrawn from JOSM.

The controlled service keeps submissions quarantined while it looks for dated public Tasking
Manager validation evidence. The plugin can download only a thresholded aggregate, never individual
examples. An aggregate remains inactive until it reports at least five eligible contributors, 20
balanced validated samples and both building and not-building evidence. Even then, its score change
is capped to a three-point suppression or 1.5-point uplift and cannot bypass the original boundary,
texture, contrast, directional-shadow, vegetation, physical-size or unlearned-baseline gates. Local
learning continues independently and works without shared learning or network access.

Version 0.11.1 constrains the shared-learning consent message to a fixed-width, word-wrapped panel,
preventing the confirmation dialogue from extending beyond the edge of the screen.

Version 1.0.0 is the first stable public release. It promotes the tested v0.11.1 feature set without
changing scanner or learning behaviour: task guidance, local visual checks, task and visible-area
reconnaissance, mapped-building review, reversible mapper decisions, persistent local learning and
the off-by-default shared-learning controlled test are now documented as the supported baseline.

Version 1.0.1 forces a complete map refresh after off-screen imagery capture, preventing a stale
black capture-sized rectangle from remaining over the map until the mapper next interacts with it.

Version 1.0.2 replaced Swing print-mode imagery capture with normal direct painting and controlled
double buffering. This reduced capture interaction with Swing, although some full-task scans could
still disturb the live map because the complete component was painted while layer visibility was
temporarily changed.

Version 1.0.3 paints only the visible authorised imagery layers into the temporary scan image. It
does not hide or restore live layers, paint the complete map component, touch its display buffers or
request a capture-related repaint, removing the remaining paths that could leave most of the map black.

Version 1.0.4 makes task-question answers shorter and more direct. Yes/no answers begin with the
conclusion and the exact matching instruction, other questions return the single best passage, and
unanswered questions use a brief “Not specified” response with at most one related passage.

Version 1.0.5 recognises broad overview questions such as “What am I mapping?” and returns up to two
of the clearest mapping instructions. The question box now uses JOSM's own text field, which disables
the editor's global key detector while focused, plus a narrow safeguard against the map reclaiming
focus immediately after a keystroke.

Version 1.0.6 broadens that safeguard to cover shortcut-triggered focus changes to any JOSM component,
while leaving deliberate mouse clicks and Tab traversal alone. It retries focus restoration briefly
when Swing does not accept the first request, preventing the question box from dropping out mid-word.

Version 1.0.7 preserves and restores the exact caret position during that automatic focus recovery.
This prevents macOS from selecting the question text after JOSM returns focus and stops the next
character from overwriting what the mapper has already typed.

Version 1.0.8 adds a dismissible first-use tip explaining that JOSM's pin icon can detach the
companion into a separate, resizable window when the mapper needs more map space. The dismissal is
remembered in JOSM preferences.

Version 1.0.9 adds guidance beneath the reconnaissance sensitivity selector. It explains the
trade-off for every mode, marks Balanced as recommended for most tasks and warns that Exploratory
deliberately includes weaker candidates, so more non-buildings should be expected.

Version 1.1.0 adds adaptive candidate review zoom. Candidate close-ups are calculated when opened,
using the candidate's projected map size and the current map-view aspect ratio, so their framing no
longer depends on the zoom used for the original scan. Closer and Wider provide seven fine-grained
positions, Reset returns to the recommended automatic view, and the selected level persists in JOSM
preferences. The same controls work for possible unmapped candidates and flagged mapped buildings.

Versions 1.1.1 through 1.1.6 were local test iterations of a general Fine map zoom control. Live
testing on JOSM 19613 showed that its supported navigation paths either retained imagery native-scale
snapping, ignored intermediate requests or reduced synthetic input to the mapper's normal whole zoom
step. Version 1.1.5 also exposed a development-stub compatibility error, corrected in 1.1.6.

Version 1.1.7 removes that unsuccessful experimental section rather than relying on unsupported JOSM
internals. The independently adjustable Closer, Wider and Reset controls for candidate-review
close-ups remain available and unchanged.

Version 1.1.8 adds an optional sharper Building check preview for the selected outline. The mapper
can switch instantly between the processed and original capture. Sharpening is local and visual only:
it does not alter JOSM imagery, recover missing resolution, feed the scanner or change the analysis
score, and the panel explicitly warns that strong edges may gain halos.

Version 1.1.9 applies the stronger sharpening pass after the captured image has been reduced to its
final preview size, preventing downscaling from smoothing the visible difference away. It also moves
the candidate-review Closer, Wider and Reset controls into a clearly labelled Selected building view
block above all mapped-building and candidate rows, so they are available as soon as review begins.

Version 1.2.0 keeps reviewed locations excluded from every later scan of the same task, including
confirmed and rejected mapped-building reviews. It also adds a neutral **Outside my area** outcome
for a feature cut by the task or visible-area boundary. That outcome suppresses the location but does
not label it as a building or non-building for local or shared learning.

Version 1.3.0 removes the sharper Building check preview after live testing showed that it did not
produce a useful visible improvement. Reconnaissance now also recognises conservative axis-aligned
L-shaped roof candidates. An L must have two consistent connected wings, a contrasting unoccupied
corner, a coherent six-edge outline and supporting shadow evidence. Its thumbnail and temporary map
highlight preserve the detected orientation. Rectangular and round detection remain unchanged.

Version 1.3.1 improves recall in Exploratory mode without lowering the scanner's hard imagery gates.
It samples candidate placement as precisely as Conservative, adds a missing 1.7:1 rectangular roof
template, prevents small high-scoring fragments from suppressing a correctly sized whole-building
proposal, and excludes implausibly tiny round and L-shaped fragments.

Version 1.3.2 keeps reviewed locations anchored to the original scan image, so changing the live map
view no longer causes confirmed or rejected candidates to return during a same-task rescan. In
Exploratory mode, L-shaped detection now tests rectangular footprints and unequal wing depths as
well as the original square 50/50 template, while retaining stronger evidence requirements for
these more flexible shapes.

## Building check

Load a HOT task, show only its authorised imagery, select exactly one complete closed outline and keep the whole outline visible. Press **Analyse selected outline**. JOSM captures the visible area, performs the measurements locally and displays the result without requiring a questionnaire.

The automatic score combines interior colour/texture consistency (10%), contrast with the surrounding imagery (20%), image-boundary strength along the outline (20%) and coherent directional shadow evidence beside it (30%). When task-image captions clearly identify building or non-building examples, local image similarity supplies the remaining 20%. If no task image is clearly labelled, the four direct imagery measurements are reweighted to 100% rather than inventing example evidence. Footprint geometry is displayed only as a diagnostic and contributes nothing to the score. Scores of 70–100 are **Likely building**, 45–69 **Uncertain**, and 0–44 **Unlikely building**.

The result is an explainable visual match score derived from image measurements, not a trained model or a statistical probability that the object is a building. The plugin refuses to analyse when the project provides no authorised imagery value, no imagery is visible, or any visible imagery layer cannot be matched conservatively to the project-authorised imagery. It paints only the visible authorised imagery layers into a separate in-memory image without changing live layer visibility or painting the complete map component; nothing is transmitted or saved. Instruction images are loaded through the same restricted HTTPS loader used for their display, and the cached decoded image is reused when available.

## Ask about this task

After a task loads, expand **Ask about this task**, enter a specific question and press **Ask**. The
companion compares the question locally with the project and task instructions, required-imagery
guidance and previous task feedback. A yes/no answer starts with **Yes** or **No** and immediately
shows the exact matching instruction. Other questions return the single best matching passage. If
the guidance does not answer the question, the plugin says **Not specified** and shows no more than
one closest related passage. No question or task text is sent to an AI service.

For example, a generic instruction to “map all buildings” is not treated as a project-specific answer
to “Should I map buildings under construction?” unless the loaded guidance also mentions
construction. The feature is an instruction finder rather than an authoritative general OSM advice
service.

Broad questions including “What am I mapping?”, “What should I map?” and “What do I need to map?”
return up to two concise, explicit instructions from What to map. The question field uses JOSM's
shortcut-safe text component so ordinary typing is not interpreted as map commands.

## Task building reconnaissance

Use **Scan entire task** after fitting the complete HOT boundary in the map view, or zoom to a useful
subsection and press **Scan visible area**. The visible-area action clips the current viewport to the
locked task polygon, excludes everything outside the task and inventories only downloaded buildings
whose centres fall within that subsection. Its candidate list and mapped counts are therefore
explicitly partial rather than totals for the whole task. Both modes temporarily capture only the
visible authorised imagery and propose roof-like regions for review.

Choose a sensitivity before scanning. **Conservative** shows no more than eight of the strongest
candidates and reduces false detections, but can miss subtle buildings. **Balanced (Recommended)**
allows up to twelve moderately supported possibilities and is suitable for most tasks.
**Exploratory** allows up to eighteen weaker, uncertain possibilities, so mappers should expect more
non-buildings and review every candidate carefully. A highlight never confirms that a building
exists. The selected mode is stored in JOSM preferences, and the trade-off for the selected mode is
shown directly beneath the selector.

Mapped footprints are classified as rectangular/orthogonal, round or other using their tags and geometry. Possible unmapped candidates are found using local measurements of roof consistency, contrast, image boundaries, directional shadow and whether those signals form a rectangular or circular region. Candidates overlapping mapped building footprints are removed, and overlapping detections are merged.

The results separate high-confidence rectangular and round candidates from uncertain candidates.
Each annotated thumbnail states why the proposal survived, such as coherent roof boundary,
directional shadow, consistent interior texture or clear separation from its surroundings. Pressing
a review button calculates a centred close-up from the candidate's real map dimensions and the
current map-view shape, then draws a temporary labelled highlight over the candidate. Use
**Closer** or **Wider** for fine-grained adjustment, or **Reset** to restore the recommended automatic
framing. The selected review zoom is remembered in JOSM preferences and is shared by possible
unmapped candidates and flagged mapped buildings. **Hide candidate outline** removes only that
temporary marker while preserving the close-up; **Show candidate outline** restores it. **Hide mapped building outlines** temporarily hides
existing `building=*` objects through JOSM's filter model so the mapper can inspect the imagery
underneath; **Show mapped building outlines** restores them and recalculates any filters the mapper
already had enabled.

Each entry under **Mapped buildings to review** also has **Show / hide review highlight**. This
independently removes or restores the labelled review marker while preserving the close-up, so the
mapper can inspect both the existing outline and the imagery without the flag obscuring the roof.

After inspecting the mapped object, **Confirm building** removes it from the active caution list and
uses its visual evidence as a positive local example. **Not a building** removes it from the active
list, uses it as a negative local example and keeps it in a recoverable section labelled for manual
OSM correction. The plugin does not delete the existing object. Restoring either decision reverses
the learning observation and returns the item to active review.

If too little of a mapped object or candidate lies inside the scanned area to make a responsible
decision, choose **Outside my area**. The item moves to a recoverable outside-area section and its
location is omitted from later scans of the same task. This is deliberately neutral: it does not
add a positive or negative learning example. Restoring it makes the location eligible again.

After review, **Reject** removes a false detection from the active list. Rejected candidates remain behind **Show rejected candidates** and can be restored. **Accept** moves a candidate to **Accepted — awaiting manual mapping**. **Map this building** returns to its close-up with the marker hidden so the mapper can use JOSM's normal Draw tool and trace the actual roof. **Check if mapped** looks for a complete closed way tagged `building=*` over the candidate centre and, when found, moves it to **Mapped during this review**. A mapped or rejected candidate can be restored if the classification was mistaken.

After any review decision, **Rescan after review** returns to the same full-task or visible-area
extent and omits those reviewed locations. Ordinary full-task and visible-area scans now use the
same exclusions, preventing the same or slightly shifted proposal from reappearing. The exclusions
also cover confirmed and rejected mapped-building reviews. Restoring a rejected or outside-area
decision makes that location eligible again; a mapped candidate remains excluded by the
downloaded/current building inventory. The reviewed-location list is retained while the same task
is open or reloaded and is cleared when a different task is loaded. Navigation and checking do not
create, reshape, tag, delete or upload an OSM object; all actual tracing remains a deliberate mapper
action.

Confirmed mappings and rejections are also recorded as local learning examples. If a mapper draws a
building that did not appear in the candidate list, **Learn from buildings drawn since scan** compares
the current building ways with the inventory captured at scan time and records eligible new outlines
as missed positive examples. Confirmed candidates have weight 1, missed positives weight 1.5. The
first rejection can immediately lower the scores of visually similar proposals, and repeated
rejections strengthen that suppression. Positive learning requires at least four positive and four
negative examples, is capped at a four-point uplift and cannot admit a proposal whose baseline score
is below 56. Neither class can rescue a proposal that fails the hard shadow, contrast, boundary,
geometry or vegetation checks. At most 20 positive and 20 negative examples from one task contribute
to the profile.

Candidate search sizes are scale-aware. At close zoom the scanner stops testing tiny pixel patches
that would represent implausibly small structures on the ground, while retaining larger templates
for normal buildings. Any learned position or size adjustment is re-analysed before display so the
highlight cannot be shifted from a scored roof-like region onto an unrelated neighbouring patch.

The **Learning** section is independent of the current HOT task. Its local history stores project and
task numbers, decision counts, last-seen HOT task status and aggregate image measurements on the
mapper's computer. **Sync validation outcomes** checks those public task endpoints, so the mapper
does not have to reopen a submitted task. Local learning does not infer object-level labels from
validator edits until uploaded OSM object IDs can be associated reliably.

## Shared learning controlled test

Shared learning is separate from the local profile and is disabled by default. Enabling it displays
a confirmation listing the data fields before any example can be queued. During the controlled test,
examples are not transmitted automatically: the mapper must press **Send queued examples**. Sent
examples remain quarantined and are listed locally as withdrawable. **Withdraw sent examples** uses
a device-local random withdrawal token; the service stores only a keyed hash of that token.

The service uses a random installation identifier to limit any one installation's influence and
enforce submission limits. This identifier is not an OSM or HOT identity. The authorised-imagery
description is converted locally into a short one-way SHA-256 identifier, preventing a custom URL or
token from being included. No raw imagery, screenshot, geographic candidate location, mapped
outline, task comment, instruction text, OSM username, email or authentication credential is sent.

**Refresh shared profile** downloads the anonymous aggregate. An `insufficient_data` profile has no
effect. An active schema-v1 profile can only make a small capped adjustment after local scoring;
the scanner still rejects anything that fails its hard visual and scale gates. Disabling consent
stops new examples from entering the local queue. It does not pretend that previously sent examples
were erased; those remain visible for explicit withdrawal.

The mapped inventory is exact only for complete, downloaded closed building ways whose centres fall inside the task boundary. Multipolygon buildings and data not currently downloaded are not included. The imagery result is an estimate from the currently rendered resolution, not a claim about the true number of buildings. Small, rotated, obscured or low-contrast roofs can be missed, while tanks, trees, bare ground and other bounded features can still be proposed for review.

## Instruction images

The companion recognises HTML `<img>` elements and Markdown image syntax in general and task-specific instructions. Up to six unique HTTPS images are loaded in the background and shown as scaled thumbnails under **What to map**. Each image keeps its alt text or title where available and includes an **Open full-size image** button.

Image downloads are limited by timeout, file size, pixel count and display size. Plain HTTP and local addresses are not loaded. If an image is unavailable or its format is not supported by Java, the written instructions remain visible and the full-size browser button remains available.

## Split-task feedback

HOT marks newly created child tasks with a `SPLIT` history event. The companion recognises that event and warns that earlier feedback may refer to the larger source boundary. If HOT does not return the original detailed comment on the child, the companion can restore feedback it saw on a source task during the previous 30 days. Restored text is clearly labelled as inherited, and mappers are told to apply only the points relevant inside the current child boundary.

The cache stores up to ten recent source-feedback entries per project on the mapper's computer. Once a child task inherits an entry, it remains bound to that same source feedback for the rest of the 30-day retention period even if newer comments are later seen in the project. Feedback never crosses between projects. The cache does not send text anywhere or write it back to HOT.

## Build against JOSM

1. Install a Java Development Kit and Ant.
2. Download `josm-tested.jar` from the official JOSM website.
3. Run:

   ```bash
   ant -Djosm.jar=/path/to/josm-tested.jar clean dist
   ```

The plugin is created at `dist/hotprojectcompanion.jar`.

`build-local.sh` exists only to compile and test the prototype in a constrained development environment. Its JOSM stubs are not packaged in the plugin.

## Install for local testing

Copy `dist/hotprojectcompanion.jar` to the JOSM plugins directory:

- macOS: `~/Library/JOSM/plugins/`
- Linux: `~/.local/share/JOSM/plugins/`
- Windows: `%APPDATA%\\JOSM\\plugins\\`

Restart JOSM and enable **HOT Project Companion** in the plugin preferences if needed.

## API behaviour

The plugin retrieves the public project and individual task endpoints in the background. Original task comments, authors and dates remain visible; the plugin does not send information back to HOT.

## Safety rules

- Consider only imagery explicitly authorised by the project instructions.
- Never move imagery or mapped features automatically.
- Keep original comments, authors and dates visible.
- Treat generated guidance as a review aid, not an authoritative mapping decision.
- Treat the Building check as automated visual guidance for human review, not authoritative feature classification.
- Treat reconnaissance candidates as a checklist of places to inspect, not objects to map automatically or a definitive building total.
- Keep shared learning off unless the mapper has read and accepted the field-level disclosure.
- Never let a shared aggregate bypass local hard scanner gates or expose individual contributions.
- Do not poll the Tasking Manager continuously.

## Licence

GPL-3.0-or-later. See `LICENSE.md`.
