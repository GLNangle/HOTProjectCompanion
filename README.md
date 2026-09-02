# HOT Project Companion

An early, read-only JOSM plugin for carrying HOT Tasking Manager project context into the mapping workflow.

Maintainer: Gemma Louise Nangle

Project page: https://github.com/GLNangle/HOTProjectCompanion

## Current prototype

Version 0.8.1 provides:

- a dockable **HOT Project Companion** sidebar in JOSM with independently collapsible sections whose states persist across restarts;
- automatic project and task detection from the JOSM task-boundary layer;
- separate project and task fields as a manual fallback, with no URL required;
- live read-only project instructions and per-task instructions;
- inline thumbnails for HTTPS images embedded in project or task instructions, with alt text and a full-size browser link;
- authorised imagery with extracted offset/alignment notes and cautious alignment guidance;
- previous task comments, invalidation warnings and recorded mapping issues;
- split-task recognition and 30-day, project-scoped recovery of source-task feedback when HOT omits it from a child task;
- changeset comment, hashtags and source/imagery details;
- an explicit reminder that only project-authorised imagery will be considered.
- a local, automatic **Building check** for one selected closed outline, using a captured view of the currently visible project-authorised imagery;
- automatic measurements of roof consistency, contrast with the surroundings, visible boundary strength and directional shadow evidence;
- footprint shape shown only as a diagnostic, never as evidence that the imagery contains a building;
- comparison with task-instruction images when their captions clearly identify building or non-building examples;
- a 0–100 visual match score labelled Likely building, Uncertain or Unlikely building, with supporting evidence and cautions.
- read-only **Task building reconnaissance** inside the detected HOT boundary;
- exact counts of downloaded closed building ways classified as rectangular/orthogonal, round or other;
- conservative estimates of possible unmapped rectangular, round and uncertain candidates from the rendered authorised imagery;
- a review checklist with annotated thumbnails and buttons that zoom to a marked close-up of each candidate;
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
  requiring the submitted task to be reopened.

It does **not** use an AI image classifier, claim a statistical probability, move imagery, modify OSM features, upload data, send captured map images, change task status, post comments or access private projects.

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

## Building check

Load a HOT task, show only its authorised imagery, select exactly one complete closed outline and keep the whole outline visible. Press **Analyse selected outline**. JOSM captures the visible area, performs the measurements locally and displays the result without requiring a questionnaire.

The automatic score combines interior colour/texture consistency (10%), contrast with the surrounding imagery (20%), image-boundary strength along the outline (20%) and coherent directional shadow evidence beside it (30%). When task-image captions clearly identify building or non-building examples, local image similarity supplies the remaining 20%. If no task image is clearly labelled, the four direct imagery measurements are reweighted to 100% rather than inventing example evidence. Footprint geometry is displayed only as a diagnostic and contributes nothing to the score. Scores of 70–100 are **Likely building**, 45–69 **Uncertain**, and 0–44 **Unlikely building**.

The result is an explainable visual match score derived from image measurements, not a trained model or a statistical probability that the object is a building. The plugin refuses to analyse when the project provides no authorised imagery value, no imagery is visible, or any visible imagery layer cannot be matched conservatively to the project-authorised imagery. It temporarily hides visible non-imagery layers while capturing the rendered JOSM map view, restores them immediately afterwards and keeps the image in memory; nothing is transmitted or saved. Instruction images are loaded through the same restricted HTTPS loader used for their display, and the cached decoded image is reused when available.

## Task building reconnaissance

Keep the complete HOT task boundary visible and large enough to inspect, then press **Scan task for buildings**. The companion reads the locked task-boundary geometry, inventories downloaded closed ways tagged `building=*`, temporarily captures only the visible authorised imagery and proposes roof-like regions for review.

Mapped footprints are classified as rectangular/orthogonal, round or other using their tags and geometry. Possible unmapped candidates are found using local measurements of roof consistency, contrast, image boundaries, directional shadow and whether those signals form a rectangular or circular region. Candidates overlapping mapped building footprints are removed, and overlapping detections are merged.

The results separate high-confidence rectangular and round candidates from uncertain candidates. Up to 16 candidates are shown as annotated thumbnails. Pressing a review button zooms JOSM to a padded close-up and draws a temporary labelled highlight over the candidate. **Hide candidate outline** removes only that temporary marker while preserving the close-up; **Show candidate outline** restores it. **Hide mapped building outlines** temporarily hides existing `building=*` objects through JOSM's filter model so the mapper can inspect the imagery underneath; **Show mapped building outlines** restores them and recalculates any filters the mapper already had enabled.

Each entry under **Mapped buildings to review** also has **Show / hide review highlight**. This
independently removes or restores the labelled review marker while preserving the close-up, so the
mapper can inspect both the existing outline and the imagery without the flag obscuring the roof.

After inspecting the mapped object, **Confirm building** removes it from the active caution list and
uses its visual evidence as a positive local example. **Not a building** removes it from the active
list, uses it as a negative local example and keeps it in a recoverable section labelled for manual
OSM correction. The plugin does not delete the existing object. Restoring either decision reverses
the learning observation and returns the item to active review.

After review, **Reject** removes a false detection from the active list. Rejected candidates remain behind **Show rejected candidates** and can be restored. **Accept** moves a candidate to **Accepted — awaiting manual mapping**. **Map this building** returns to its close-up with the marker hidden so the mapper can use JOSM's normal Draw tool and trace the actual roof. **Check if mapped** looks for a complete closed way tagged `building=*` over the candidate centre and, when found, moves it to **Mapped during this review**. A mapped or rejected candidate can be restored if the classification was mistaken.

Review decisions last until the task is rescanned or replaced. Navigation and checking do not create, reshape, tag, delete or upload an OSM object; all actual tracing remains a deliberate mapper action.

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

The **Local learning** section is independent of the current HOT task. Its history stores project and
task numbers, decision counts, last-seen HOT task status and aggregate image measurements on the
mapper's computer. **Sync validation outcomes** checks those public task endpoints, so the mapper
does not have to reopen a submitted task. Version 0.5.0 does not infer training labels from validator
edits yet; that requires reliable association with uploaded OSM object IDs. The intended weighting is
3 for unambiguous validator additions/replacements/reshapes, 1.5 negative for an unambiguous deletion
with no replacement, 0.5 for an unchanged object and 0 for ambiguous edits.

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
- Do not poll the Tasking Manager continuously.

## Licence

GPL-3.0-or-later. See `LICENSE.md`.
