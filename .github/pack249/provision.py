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
    # Only byte-matched, publicly published declarative overrides. Never restore a world,
    # remote addresses, private settings or executable modpack scripts.
    overrides = json.loads((ROOT / "overrides.json").read_text())
    with ZipFile(retrieve(PACK["pack"],cache)) as archive:
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
