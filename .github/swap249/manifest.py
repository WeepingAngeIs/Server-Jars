"""Hash only disposable runner files; never read a user world or profile."""
import hashlib
import json

def capture(report,game,side,stage):
    def digest(path,algorithm="sha256"):
        with path.open("rb") as stream:return hashlib.file_digest(stream,algorithm).hexdigest()
    rows=[]
    for sub in ("mods","config","defaultconfigs","kubejs","datapacks","resourcepacks"):
        path=game/sub
        if not path.exists():continue
        for file in sorted(x for x in path.rglob("*") if x.is_file()):
            row={"path":file.relative_to(game).as_posix(),"size":file.stat().st_size,"sha256":digest(file)}
            if sub=="mods" and file.suffix==".jar":row["sha512"]=digest(file,"sha512")
            rows.append(row)
    for name in ("server.properties","options.txt"):
        file=game/name
        if file.is_file():rows.append({"path":name,"size":file.stat().st_size,"sha256":digest(file)})
    (report/f"accepted-{side}-{stage}.json").write_text(json.dumps({"side":side,"stage":stage,"files":rows},indent=2))
