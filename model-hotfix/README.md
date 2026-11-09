# Fairy Sliggoo and Inkyeon model repair

Unyielding 1.1.8 patches three asset files in the verified 1.1.7 combined JAR. Fairy Sliggoo uses regular Sliggoo's original eyes and mouth. Inkyeon's rear legs have no sideways splay during movement and use half the former stride amplitude; the front stride and diagonal timing are preserved.

The source and assembler are in [unyielding-mod at 660dd3a](https://github.com/WeepingAngeIs/unyielding-mod/tree/660dd3a3d0c1e4240139fbade3e8c79984969963). Run `python tools/build_model_hotfix.py` from that checkout. The script verifies pinned baseline hashes, retains assembly-added riding locators, and audits every entry. Only the three specified assets and the outer version manifest may change. Gameplay data, textures, Java classes and riding definitions remain byte-identical to 1.1.7.

The targeted `model-hotfix.yml` workflow renders the assembled combined JAR in Minecraft/Cobblemon. It captures the restored face and moving gait in normal and shiny colors, compares rear and front stride with native Sylveon, and exercises idle/battle-idle transitions with real nonzero movement. The harness records all rear ancestors, joints and foot positions. A local compilation preflight uses the existing dependency cache; actual game checks run on GitHub.

See the release installation and validation records for the tested artifact and checksums. These are focused rendering checks, without a full modpack play session or live-server installation.
