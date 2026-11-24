# Migration validation scope

The hosted occupied-save suite starts with the attached Minecraft Jail 1.2.0
artifact (SHA-256 `5e2b40e36f2c6036005bed46c62f5da6b5faa7b8f77becec2ec334aa83990cd8`)
and a synthetic player, custody journal, wallet and world. The same-loader
scenario checks the supported migration, interruptions, restart, escape and
return. The changed-loader scenario checks that a .255 occupied save is held
for review on .249, without changing the saved custody or delivering its items.

These are isolated GitHub-hosted checks, not a migration of any real world.
The exact candidate and results must be taken from the corresponding workflow
artifact; this document does not claim that a run passed. Existing occupied
custody is bound to its creating JAR and runtime. A successful fresh-world
rehearsal alone does not authorize an upgrade of an occupied jail.
