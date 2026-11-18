"""Restore published pack binaries by digest into an isolated GitHub runner."""
import argparse
import concurrent.futures
import hashlib
import json
import os
import shutil
import time
from pathlib import Path
from urllib.parse import quote, urlsplit, urlunsplit
from urllib.request import Request, urlopen
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent
PACK = json.loads((ROOT / "profile.json").read_text())
def digest(path, algorithm):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()

def retrieve(row, cache):
    algorithm = "sha512" if "sha512" in row else "sha1"
    dst = cache / row[algorithm]
    if dst.exists() and digest(dst, algorithm) == row[algorithm]:
        return dst
    errors = []
    for attempt in range(3):
        for url in dict.fromkeys([row.get("modrinth", row["url"]), row["url"]]):
            parts = urlsplit(url)
            assert parts.scheme == "https" and parts.hostname in {"mediafilez.forgecdn.net","cdn.modrinth.com"}
            uri = urlunsplit(parts._replace(path=quote(parts.path, safe="/%")))
            tmp = dst.with_suffix(".part")
            try:
                with urlopen(Request(uri, headers={"User-Agent":"WeepingAngeIs/Server-Jars pack-compatibility"}), timeout=120) as src, tmp.open("wb") as out:
                    shutil.copyfileobj(src, out, 1024*1024)
                if tmp.stat().st_size != row["size"] or digest(tmp, algorithm) != row[algorithm]:
                    raise ValueError("published bytes differ from installed profile")
                tmp.replace(dst)
                return dst
            except Exception as error:
                tmp.unlink(missing_ok=True)
                errors.append(str(error))
        time.sleep(attempt+1)
    raise RuntimeError(f"{row['name']}: {errors}")

def active_settings(archive, dest):
    # Reproduce the installed non-personal gameplay settings from the public files.
    adjustments = {
        "config/cubalance-common.toml": (b"armor_percent = 100.0", b"armor_percent = 50.0"),
        "config/defaultoptions-common.toml": (b', "file/CobblemonMoreCosmetics Release v1.1.7-1.21.x.zip"', b''),
        "config/defaultoptions/options.txt": (b',"file/CobblemonMoreCosmetics Release v1.1.7-1.21.x.zip"', b''),
    }
    expected = json.loads((ROOT / "configured-hashes.json").read_text())
    for name, sha in expected.items():
        data = archive.read("overrides/" + name)
        if name in adjustments:
            old, new = adjustments[name]
            assert data.count(old) == 1
            data = data.replace(old, new)
        elif name == "config/infernalmobs.cfg":
            registry = json.loads(data)
            for key, value in [("permittedentities",True),("entitiesalwaysinfernal",False)]:
                for entity in ("Maledictus_Entity","HydraEntity","StymphalianBirdEntity","The_Harbinger_Entity"):
                    del registry[key][entity]
                registry[key].update(PiglinBrute=value, Piglin=value)
            for entity in ("StymphalianBirdEntity","HydraEntity","GhostEntity","ElderGuardian"):
                del registry["entitybasehealth"][entity]
            registry["entitybasehealth"].update(TheGatekeeper=175.0, PiglinBrute=25.0)
            data = json.dumps(registry, ensure_ascii=False, indent=2).encode() + b"\r\n"
        if hashlib.sha256(data).hexdigest() != sha:
            raise ValueError(f"Published configuration reconstruction differs for {name}")
        target = dest / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)

def populate(dest, side, cache):
    dest.mkdir(parents=True, exist_ok=True)
    mods = dest / "mods"
    mods.mkdir(exist_ok=True)
    excluded = json.loads((ROOT / "server-exclusions.json").read_text())
    # Simply More declares Simply Tooltips required on BOTH sides in its shipped NeoForge metadata.
    server_dependencies = {"SimplyTooltips-neoforge-0.1.5.jar"}
    selected = [r for r in PACK["mods"] if side == "client" or
                (r["side"] != "unsupported" or r["name"] in server_dependencies) and r["name"] not in excluded]
    def install(row):
        name = row["name"]
        assert name == Path(name).name and name.endswith(".jar")
        source = retrieve(row,cache)
        target = mods / name
        if target.exists():
            if digest(target,"sha512") != row["sha512"]:
                raise ValueError(f"Conflicting mod at {target}")
        else:
            os.link(source,target)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(install, selected))
    for row in PACK["additional_packs"]:
        name = Path(row["path"])
        assert len(name.parts) == 2 and name.parts[0] in {"resourcepacks","datapacks"} and name.name == row["name"]
        path = dest / name
        path.parent.mkdir(parents=True, exist_ok=True)
        source = retrieve(row, cache)
        if not path.exists():
            os.link(source, path)
        elif digest(path, "sha512") != row["sha512"]:
            raise ValueError(f"Conflicting pack at {path}")
    # Public configuration, resources and audited data/battle packs. Saved worlds,
    # addresses, user identities and generated player caches stay out of the fixture.
    overrides = json.loads((ROOT / "overrides.json").read_text())
    with ZipFile(retrieve(PACK["pack"],cache)) as archive:
        active_settings(archive,dest)
        for name,expected in overrides.items():
            assert not Path(name).is_absolute() and ".." not in Path(name).parts
            body=archive.read("overrides/"+name)
            if hashlib.sha256(body).hexdigest()!=expected:
                raise ValueError(f"Local and published override differ: {name}")
            path = dest / name
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(body)
    print(f"Installed {len(selected)} hash-pinned {side} JARs and {len(overrides)} byte-matched pack overrides",flush=True)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--side",choices=["client","server"],required=True)
    parser.add_argument("--dest",type=Path,required=True)
    args=parser.parse_args()
    assert os.environ.get("GITHUB_ACTIONS")=="true" and os.environ.get("RUNNER_ENVIRONMENT")=="github-hosted"
    cache=Path(os.environ["RUNNER_TEMP"])/"jail-pack249-downloads"
    cache.mkdir(exist_ok=True)
    populate(args.dest,args.side,cache)
if __name__=="__main__":
    main()
