# Fixture corrections, not production relaxations

The first candidate run `43971936915` at `38880caa` built successfully and passed
68 Jail JUnit tests and 14 evidence-validator tests. New runtime slices found two
mistakes in the independent synthetic fixture. These attempted runs are not a
complete runtime pass and must not be included in final passing phase counts.

* The selected-slot negative test temporarily replaced the sword seeded in slot 2
  with the wallet, then put EMPTY back. The fixture now retains and restores the
  original synthetic sword and still requires full snapshot equality afterward.
* After an actual disconnect, the native `unjail Visitor` player selector throws
  `CommandSyntaxException: No player was found`, rather than returning zero. The
  fixture now recognizes only that exact offline refusal and verifies the custody
  binding did not change. Other syntax failures still fail the test.

Neither correction edits a production class, certificate, companion binary,
transaction receipt, or expected resource state. They do not substitute a test
pass for a real admission, release, death or crash. All runtime groups must pass
again against the final exact commit before a verified delivery is emitted.

## Saved-return recovery is not final release

Run `43973652674` at `0e3cfd4d` proved the saved wallet return after an
actual abrupt stop, then correctly found that the existing custody ledger
remains QUARANTINED after `jailcustody recover`. The fixture had incorrectly
expected recovery alone to release that administrative hold.

The fixture now explicitly proves that exact items and receipts are retained,
the spatial assignment is complete, ordinary unjail cannot skip the hold,
and restore without review is refused. It then invokes the existing native
`jailcustody review <transaction>` and `jailcustody restore <transaction>`
commands and verifies completion without any additional resource grant.
Two validator tests reject missing/false final-review evidence. No production
logic or safety gate was changed to satisfy this test.

The final workflow must rerun all six custody scenarios and all four regression
slices against one exact commit. Unit tests alone and incomplete earlier attempts
must not promote the candidate to a runtime-verified artifact.
