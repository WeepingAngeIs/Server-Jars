"""Assemble the IV and Substitute update against the approved Mega editor JAR."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import tomllib
from zipfile import ZIP_DEFLATED, ZipFile

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def assemble(args):
    inputs = [args.base, args.iv, args.substitute]
    expected = [
        "88ba55541b72b52fe7aeee5db5616c0a27d5426f03bd99d5bbac0daeeaf2da7b",
        "6ec95f23edb6a9277488ed548a5553187b4774d1bf49f9dea7abeb9b7323a728",
        "2980c936e64e26f58ccd16fef33797f91dbfc4b15146acb833a03094cad8a28e",
    ]
    if [digest(p) for p in inputs] != expected:
        raise ValueError("Review input versions before assembling this release.")
    here = Path(__file__).resolve().parent
    quests = here.parent / "unyielding-quests/src/main/java/dev/unyielding/quests"
    sources = [here / "dev/unyielding/balance/mixin/RaidBossMixin.java",
               quests / "client/BalanceSettingsScreen.java", quests / "settings/BalanceSettings.java"]
    with tempfile.TemporaryDirectory() as tmp:
        classes = Path(tmp)
        import os
        cp = os.pathsep.join([str(args.base), str(args.iv), args.classpath.read_text().strip()])
        argfile = classes / "javac.args"
        argfile.write_text('-proc:none -encoding UTF-8 --release 21 -classpath "' + cp.replace(chr(92), "/")
                           + '" -d "' + classes.as_posix() + '" ' + " ".join('"'+p.as_posix()+'"' for p in sources))
        subprocess.run([args.javac, "@"+str(argfile)], check=True)
        updates = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob("*.class")}
    meta = "META-INF/neoforge.mods.toml"
    with ZipFile(args.base) as base, ZipFile(args.iv) as iv, ZipFile(args.substitute) as sub:
        def add(name, data):
            if name in updates or name in base.namelist():
                raise ValueError("Unexpected collision: " + name)
            updates[name] = data
        for n in iv.namelist():
            if n.endswith(".class") and "/mixin/" not in n:
                add(n, iv.read(n))
        for n in sub.namelist():
            if n.endswith("/") or n in base.namelist():
                continue
            if "substitute" not in n and n != "assets/unyielding/lang/en_us.json":
                raise ValueError("Unclassified legacy resource: " + n)
            add(n, sub.read(n))
        lang = "assets/cobblemon/lang/en_us.json"
        language = json.loads(base.read(lang))
        for key, value in json.loads(sub.read(lang)).items():
            if key in language and language[key] != value:
                raise ValueError("Conflicting translation: " + key)
            language[key] = value
        updates[lang] = json.dumps(language, indent=2, ensure_ascii=False).encode()
        metadata = base.read(meta).decode() + "\n"
        for donor, mod in [(sub, "cusubstitute"), (iv, "uwuivrebalance")]:
            for section in re.split(r"(?m)(?=^\[\[)", donor.read(meta).decode()):
                if not section.startswith("[["):
                    continue
                if (section.startswith("[[mods]]") and tomllib.loads(section)["mods"][0]["modId"] == mod
                    or section.startswith("[[dependencies." + mod + "]]")):
                    metadata += section.strip() + "\n\n"
        updates[meta] = metadata.encode()
        doc = tomllib.loads(metadata)
        ids = [m["modId"] for m in doc["mods"]]
        assert len(ids) == len(set(ids)) and {"cusubstitute","uwuivrebalance"} <= set(ids)
        args.output.parent.mkdir(parents=True,exist_ok=True)
        with ZipFile(args.output,"w",ZIP_DEFLATED) as dest:
            for info in base.infolist():
                dest.writestr(info,updates.pop(info.filename) if info.filename in updates else base.read(info))
            for n, data in updates.items():
                dest.writestr(n,data)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for key in ["base","iv","substitute","javac","classpath","output"]:
        parser.add_argument("--"+key,type=Path,required=True)
    assemble(parser.parse_args())
