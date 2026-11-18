"""Check a real .255 held wallet stays in escrow on a .249 copy of the synthetic world."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

root = Path(os.environ["GITHUB_WORKSPACE"])
if os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted":
    sys.exit("Requires a disposable hosted runner")
evidence = root/"dist/packaged-smoke"
project = root/"dist/packaged-harness"
snapshot = root/"occupied/packaged-smoke/synthetic-old-save.tar.gz"
if hashlib.sha256(snapshot.read_bytes()).hexdigest() != "39816c7a1f085e1c0722c5d4451a1e3e9a0c3a557cbb830ceab689e06ce74aa1":
    sys.exit("Unexpected occupied world")
world = project/"run-server/jail_certified_smoke"
if world.exists():
    sys.exit("Refusing to restore over any existing world")
with tarfile.open(snapshot) as source:
    if any(not m.name.startswith("jail_certified_smoke/") and m.name != "jail_certified_smoke" or m.issym() or m.islnk() for m in source):
        sys.exit("Unexpected synthetic archive layout")
    source.extractall(world.parent, filter="data")
archive = world/"jail-migration-inputs/old.jar"
archive.parent.mkdir(exist_ok=True)
shutil.copyfile(os.environ["MIGRATION_PREDECESSOR"], archive)
assert hashlib.sha256(archive.read_bytes()).hexdigest() == os.environ["MIGRATION_OLD_SHA256"]
candidate = Path(os.environ["MIGRATION_CANDIDATE"])
def replace(path, a, b):
    s = path.read_text()
    if s.count(a) != 1:
        raise RuntimeError(f"Test template changed: {path}")
    path.write_text(s.replace(a, b))

replace(project/"build.gradle", "version = '21.1.255'", "version = '21.1.249'")
test = project/"src/main/java/dev/unyielding/bundlesmoke/PlayerOnlySmoke.java"
before = "    static void migrate()throws Exception{"
code = """
    static void migratedKernelRefusal() throws Exception {
        var player = p();
        var modList = net.neoforged.fml.ModList.get();
        require(modList.getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString().equals("21.1.249"), "Wrong target kernel");
        var assignment = JailData.get(player.server).assignment(player.getUUID());
        var tx = CertifiedJailSmoke.last;
        require(assignment != null && tx.equals(JailIntegrated.activeTransaction(player.server,player.getUUID())), "Missing legacy custody");
        var items = file("integrated-jail-v1/"+tx+"/items/escrow.v1");
        byte[] journal = Files.readAllBytes(items);
        var carried = player.getInventory().save(new net.minecraft.nbt.ListTag());
        require(carried.isEmpty(), "Held items unexpectedly present before transition");
        require(cmd("unjail Visitor")==0, "Released despite incompatible runtime");
        refused(()->prepare(),"cross_kernel_proposal_refused");
        require(!Files.exists(transition()), "Created a migration proposal despite changed loader");
        require(Arrays.equals(journal, Files.readAllBytes(items)), "Changed item escrow");
        require(player.getInventory().save(new net.minecraft.nbt.ListTag()).equals(carried), "Delivered items or wallet on mismatch");
        require(assignment.equals(JailData.get(player.server).assignment(player.getUUID())) && tx.equals(JailIntegrated.activeTransaction(player.server,player.getUUID())), "Lost occupied assignment");
        proof.put("candidate",System.getenv("UQ_JAIL_SHA256"));
        flag("retained_old_assignment_inventory_and_wallet_receipt");
        Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/cross-kernel.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        Files.writeString(Path.of("/tmp/uq-player-only-result"),"passed: refused cross-kernel migration and retained the held wallet");
        done=true;
    }
"""
replace(test,before,code+before)
replace(test,"migrate();return;", "migratedKernelRefusal();return;")
env = dict(os.environ, UQ_PACKAGED_JAIL_SMOKE="1", LIBGL_ALWAYS_SOFTWARE="true", ALSOFT_DRIVERS="null",
           UQ_WORLD_NAME=world.name, UQ_RESULT_FILE="/tmp/uq-player-only-result",
           MIGRATION_OLD_SHA512=hashlib.sha512(archive.read_bytes()).hexdigest(),
           MIGRATION_CANDIDATE_SHA512=hashlib.sha512(candidate.read_bytes()).hexdigest(),
           JAVA_TOOL_OPTIONS="-Duq.integratedJail=true -Duq.certifiedJail=true -Duq.playerOnly=true -Duq.playerOnlyPhase=2 -Duq.certifiedPhase=2")
subprocess.run(["bash",".github/scripts/meeting-two-player.sh"], env=env, check=True)
assert (evidence/"cross-kernel.json").is_file()

