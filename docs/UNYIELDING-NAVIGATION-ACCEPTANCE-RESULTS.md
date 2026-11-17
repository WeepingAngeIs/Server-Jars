# Independent party and quest navigation acceptance

This is one bounded follow-up to the implementation-only navigation entry in the [acceptance index](UNYIELDING-ACCEPTANCE-INDEX.md). Earlier confirmation and progress checks were inspected first and are not relabeled as roster/search/filter UI evidence. Work is isolated on `codex/unyielding-navigation-acceptance`, starting at the config-audit report checkpoint `cd909503789660c4d7c54d71194d9b359287ee25`.

## Candidate and existing contract

The input is the unchanged Unyielding **1.2.0** artifact from [config-audit run 44043593748](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44043593748), artifact `14311265884`, SHA-256 `216de93ca87cc9e0a54f0f0991c675a1058ee95de3247c8efbebffc664f0c504`. Production source and assembler are unchanged, so this pass reuses that JAR. It does not repeat the production build or 69-unit suite; that existing unit result remains scoped to meeting-provider run `44031631588`.

Roster search trims whitespace, compares names without case sensitivity, and resets pagination when Find applies a query. Quest status selection cycles All, LOCKED, AVAILABLE, STARTED, DONE and CLAIM; Next navigates matching visible quests. Search/Next work across chapters and wrap. Status selection alone changes the filter rather than opening a result.

Any member of a PARTY can pin or unpin its informational quest marker. No officer-only rule is introduced. A goal does not share progress, complete a task or claim a reward. Its detail uses the viewer's personal progress, and a quest hidden behind that viewer's dependencies remains unavailable to that viewer.

## Disposable native scope

Two actual public test clients, Dev and Visitor, run on GitHub-hosted displays at 1024 by 768 pixels, GUI scale 2 (512 by 384 GUI units). Declared synthetic preconditions create one 14-member party: these two online players and twelve offline roster entries. Two chapters contain eight synthetic quests with separate personal counters, completed quests, claimed/unclaimed rewards and an actor-specific hidden prerequisite. Offline entries are not additional connected clients.

The test-only client opens the production screens through the normal OpenScreen client handler. It then types into actual focused fields and clicks actual rendered widgets or immediate-mode hit regions through native mouse input. Reflection observes internal state and rendered hit bounds; it does not invoke button actions or private search/filter methods. Production Pin/Unpin requests travel as actual TeamAction packets. One explicitly declared hidden-for-actor packet probes server refusal after the UI checks.

Ordered test-only Barrier, server processed marker and client Receipt precede each acknowledgment. Each requested screenshot must exist and be nonempty before acknowledgment. Screenshots are native framebuffer captures, not reconstructed mockups. Their final readability review is separate from automated state assertions; roster assertions calculate the expected visible slice and do not perform OCR.

After each acknowledged action and completed case, the fixture compares native QuestData, including personal tasks/counters/keys/claims/epochs, DailyLogin data including balances and debit/credit history, inventory/components and XP, and TeamData. Only the tested party's `pinnedQuest` field is excluded from equality where marker changes are expected; other teams and all membership/settings remain protected. Sharing remains disabled, with distinct personal progress records. No claim, task completion or payout button is pressed.

## Verification record

Verified 2026-11-17 UTC: test source `68658ef16d547dd884bc82c7d029f7f974e3f1f1`, [run 44061079937](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44061079937), job `133962818875`, passed **all eight native cases, zero failures**. Both real clients reached readiness. Native assertions verified the loaded ModList JAR path/hash and absence of Jail.

[Artifact 14318597401](https://github.com/WeepingAngeIs/Server-Jars/actions/runs/44061079937/artifacts/14318597401) was downloaded independently. Its ZIP SHA-256 matched published `bf9dc56f2226fc5607a37c58a44472b77c56ff365df936deda0261dc28d41016`. Its JAR independently matched the native runtime SHA-256 and was byte for byte identical to the earlier meeting-verified candidate. The artifact contains zero new unit-result files; no production build or unit suite was repeated.

The eight passing boundaries are:

1. First, middle and last roster pages, stable row order and disabled boundary arrows.
2. A trimmed mixed-case member query applied from a later page, page reset, and clearing the query back to the original roster.
3. Ordinary-member controls and an empty roster search with disabled pagination.
4. Quest title search, cross-chapter Next/wrap and exclusion of personally hidden quests.
5. All five status categories plus All, including claimed versus unclaimed completion and filtered cross-chapter wrap.
6. Owner pin synchronized to an ordinary member, who opens the informational detail.
7. Ordinary member replacement/unpin synchronized to the owner, with distinct personal counters retained.
8. Actor-specific hidden goal refusal, hidden title search exclusion, explicit server pin refusal and owner unpin.

All eleven native PNGs were extracted without image editing, verified at 1024 by 768 pixels and visually inspected. Roster row names, filtered results and the no-match message are legible. The STARTED screenshot leaves the detail closed so the entire status control is visible; the CLAIM screenshot shows the actual completed task and unclaimed reward. Goal screenshots show the member's available detail, the owner's distinct 4/10 counter after a member pin, and the viewer-specific unavailable/no-matches notices.

Native onboarding/advancement/recipe notifications overlap some upper-right controls in early frames; in particular the first-page arrows are not an unobstructed screenshot proof. Their states/click refusal are covered by native assertions, and the last-page and search images show the arrows clearly. The screenshots are retained as captured, without removing notifications. A detail panel also normally covers part of the sidebar; the dedicated STARTED frame and hidden-goal frames show the full sidebar. No clean minimum-resolution or notification-free visual certification is claimed.

After every ordered action and case, all declared progress, claim, spin, inventory, XP, membership and settings sentinels remained equal, sharing stayed disabled and the personal progress records remained distinct. The final goal was unpinned. The hidden-for-actor packet requests the already-pinned ID; explicit server refusal and unchanged state are established, rather than a different-ID replacement attempt. No defect was reproduced and no production fix was made.

## Exact unchanged candidate and evidence

Version remains **1.2.0**:

- SHA-256: `216de93ca87cc9e0a54f0f0991c675a1058ee95de3247c8efbebffc664f0c504`
- Raw SHA-512: `349284d438561c34d7005899cec9a80025ccd6bd3589f9b51c8768303cb22e60275ebf4ba7cb31acd2023bbaf9dabc414c5313db4ed0ee6856c167c8ae5c3ef2`

The [machine-readable results](UNYIELDING-NAVIGATION-ACCEPTANCE-RESULTS.json) retain the exact source, run, case list, artifact digest and per-screenshot hashes. The artifact contains native client observations/action logs and generated runtime inputs. Existing production features, resources, art and model bytes are unchanged.

## Preservation and limits

No installed profile, JAR, live world, private configuration, settings, saved hotbar, artwork or model is read or modified for this pass. No deployment occurs. Version stays 1.2.0. The separate Jail project, exact-pair certificate and gates remain unchanged and are not build prerequisites. Basic Mawile feeding and previously completed suites are not repeated.

These are eight bounded native UI boundaries in one generated running server, not an exhaustive GUI, role, paging, localization, minimum-resolution or full-pack matrix. The chosen readable viewport does not certify every compact layout. No server restart, crash recovery, real old-save migration, live rank/provider/claim policy or cross-store atomic payout durability is established. Authoritative inputs for those broader claims remain necessary. The pass ends here; it does not select another acceptance target.
