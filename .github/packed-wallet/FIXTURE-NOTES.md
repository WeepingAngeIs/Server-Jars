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
