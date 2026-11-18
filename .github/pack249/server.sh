#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]]
root="$GITHUB_WORKSPACE"
src="$root/.github/pack249"
server="$RUNNER_TEMP/pack249-server"
evidence="$root/dist/pack249"
candidate="${1:-}"
mkdir -p "$evidence" "$server"
cd "$server"
python3 "$src/provision.py" --side server --dest "$server"
curl -fLsS --retry 3 -o installer.jar https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar
echo 'd88b448eab73cd65bdf1720844a4828262de30a15fc71bd04dd81acc61c5399a  installer.jar' | sha256sum --check
java -jar installer.jar --installServer > "$evidence/install.log" 2>&1
if [[ "${PACK249_INSTALL_ONLY:-0}" == 1 ]]; then exit 0; fi
mkdir -p probe/META-INF
# Compile the read-only census against the same actual FML/NeoForge jars used by the server.
# Package a private copy so census can run without Jail and does not shadow its classes.
sed 's/^package dev.unyielding.jail;/package dev.unyielding.pack249;/' \
    "$root/minecraft-jail/src/main/java/dev/unyielding/jail/RuntimeFileIdentity.java" > RuntimeFileIdentity.java
javac -proc:none -cp "$(find libraries -name '*.jar' | paste -sd:)" -d probe "$src/PackCensus.java" "$src/SyntheticPlayerProjection.java" RuntimeFileIdentity.java
cp "$src/probe.mods.toml" probe/META-INF/neoforge.mods.toml
jar --create --file mods/pack249-census.jar -C probe .
if [[ -n "$candidate" ]]; then cp "$candidate" mods/; fi
export PACK_CENSUS="$evidence/loaded-rows.txt"
echo eula=true > eula.txt
cat > server.properties <<'PROP'
online-mode=false
server-ip=127.0.0.1
level-name=disposable_pack249
level-type=minecraft:flat
view-distance=4
simulation-distance=3
max-tick-time=0
spawn-protection=0
enable-rcon=false
enable-query=false
PROP
mkfifo console
exec 9<>console
java -Xms1G -Xmx7G @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui <console >"$evidence/server.log" 2>&1 & pid=$!
trap 'kill "$pid" 2>/dev/null || true' EXIT
for ((i=0; i<360; i++)); do
    if [[ -s "$PACK_CENSUS" ]] && grep -Fq 'Done (' "$evidence/server.log"; then
        echo stop >&9
        wait "$pid"
        grep -q 'All dimensions are saved' "$evidence/server.log"
        echo 'Clean dedicated .249 server start, loaded-file census and save verified.'
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 2
done
tail -120 "$evidence/server.log"
exit 1
