# Embedded custom content input

`Mawonna.zip` is the complete previously verified pack from the immutable upstream
revision recorded in `tutorial-update/release.json`. The cloud assembly verifies
its SHA-256 before including its `assets/` and `data/` resources in the combined
JAR. License notices are retained under `LICENSES/unyielding-custom-content/`.

The existing companion module remains in the combined JAR. Its translations are
merged with the pack's translations so item and interaction messages remain
available. The standalone pack's installation notes and build reports are not
embedded. This input does not add natural spawn rules.

All assembly and runtime verification run on GitHub-hosted runners.
