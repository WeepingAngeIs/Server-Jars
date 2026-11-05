"""Export the workbook's art on the GitHub runner and inspect the shipped PNG bytes."""
import base64
import hashlib
import html
import io
import json
from pathlib import Path
import re
import zipfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from dex import frames_for

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
OUT = REPO / ".astra-output"
SIZE = 32
PLAY = [0, 1, 2, 3, 2, 1]
EXPECTED = json.loads((ROOT / "expected.json").read_text(encoding="utf-8-sig"))


def check(ok, message):
    if not ok:
        raise ValueError(message)


def binary_mask(image):
    return image.getchannel("A").point(lambda a: 255 if a >= 128 else 0).filter(ImageFilter.MedianFilter(3))


def split_axis(mask, n, vertical=False):
    """Choose empty cuts near regular grid lines, tolerating slightly uneven gutters."""
    if vertical:
        mask = mask.transpose(Image.Transpose.TRANSPOSE)
    width, height = mask.size
    band = mask.resize((width, 1), Image.Resampling.BOX)
    density = list(band.getdata())
    step = width / n
    cuts = [0]
    for i in range(1, n):
        target = round(i * step)
        allowance = round(step * 0.35)
        options = range(target - allowance, target + allowance + 1)
        cuts.append(min(options, key=lambda x: (sum(density[x-2:x+3]), abs(x-target))))
    return [*cuts, width]


def read_atlas(path, columns=4, row_count=4):
    sheet = Image.open(path).convert("RGBA")
    mask = binary_mask(sheet)
    check(mask.getextrema() == (0,255), f"{path.name}: expected transparent background")
    sheet.putalpha(mask)
    ycuts = split_axis(mask,row_count,True)
    cells = []
    for y0,y1 in zip(ycuts,ycuts[1:]):
        band = sheet.crop((0,y0,sheet.width,y1))
        xcuts = split_axis(band.getchannel("A"),columns)
        cells.append([band.crop((x,0,x1,band.height)) for x,x1 in zip(xcuts,xcuts[1:])])
    return cells


def normalize(cells):
    bounds = [c.getchannel("A").getbbox() for c in cells]
    check(all(bounds), "empty quadrant")
    crops = [c.crop(b) for c,b in zip(cells,bounds)]
    for cell in cells:
        alpha=cell.getchannel('A')
        filled=alpha.histogram()[255]/(cell.width*cell.height)
        check(filled < 0.89, 'cell looks like an opaque background')
        corners=[alpha.getpixel((x,y)) for x in (0,cell.width-1) for y in (0,cell.height-1)]
        check(sum(v==255 for v in corners)<=1,'opaque outer corners or adjoining sprite')
    for crop in crops:
        check(min(crop.size) >= 10, "tiny or empty source sprite")
    # Scale equally across the four poses to retain the designed movement.
    scale = min(28/max(c.width for c in crops), 28/max(c.height for c in crops))
    frames = []
    for crop in crops:
        dims = (max(1,round(crop.width*scale)),max(1,round(crop.height*scale)))
        sprite = crop.resize(dims, Image.Resampling.NEAREST)
        f = Image.new("RGBA",(32,32))
        f.paste(sprite,((32-sprite.width)//2,30-sprite.height))
        frames.append(f)
    return frames


def shared_palette(frames):
    visible = [p[:3] for f in frames for p in f.getdata() if p[3] == 255]
    check(len(visible) >= 64,"too few visible pixels")
    samples=Image.new("RGB",(len(visible),1))
    samples.putdata(visible)
    pal=samples.quantize(colors=16,method=Image.Quantize.MEDIANCUT,dither=Image.Dither.NONE)
    result=[]
    for f in frames:
        flat=f.convert("RGB").quantize(palette=pal,dither=Image.Dither.NONE).convert("RGBA")
        flat.putalpha(f.getchannel("A"))
        flat.putdata([p if p[3] else (0,0,0,0) for p in flat.getdata()])
        result.append(flat)
    return result


def encode(im,fmt,**kw):
    buf=io.BytesIO()
    im.save(buf,format=fmt,**kw)
    return buf.getvalue()


def export_one(meta,raw_frames):
    frames=shared_palette(raw_frames)
    strip=Image.new("RGBA",(32,128))
    palette=set()
    bounds=[]
    for i,f in enumerate(frames):
        b=f.getchannel("A").getbbox()
        check(b and min(b[:2])>=2 and max(b[2:])<=30, "missing transparent margin")
        check({p[3] for p in f.getdata()} == {0,255}, "non-binary alpha")
        palette.update(p[:3] for p in f.getdata() if p[3])
        bounds.append(b)
        strip.paste(f,(0,i*SIZE))
    check(2<=len(palette)<=16, f"palette size {len(palette)}")
    distinct=len({f.tobytes() for f in frames})
    check(distinct>=2,"motion disappeared at native size")
    data=encode(strip,"PNG",optimize=True)
    with Image.open(io.BytesIO(data)) as decoded:
        check(decoded.size==(32,128) and decoded.mode=="RGBA","invalid saved strip")
        for i,f in enumerate(frames):
            check(decoded.crop((0,32*i,32,32*i+32)).tobytes()==f.tobytes(),"saved frame differs")
    webp=encode(frames[0],"WEBP",save_all=True,append_images=[frames[i] for i in PLAY[1:]],
                duration=180,loop=0,lossless=True,exact=True)
    with Image.open(io.BytesIO(webp)) as preview:
        check(preview.n_frames>=2 and preview.info.get("loop")==0,"unplayable preview")
    masks=[set(i for i,p in enumerate(f.getdata()) if p[3]) for f in frames]
    overlap=min(len(masks[0]&m)/len(masks[0]|m) for m in masks)
    return dict(frames=frames, png=data, webp=webp, distinct=distinct,
                colors=len(palette), overlap=round(overlap,3),
                sha256=hashlib.sha256(data).hexdigest(), bounds=bounds, **meta)


def previews(items):
    small=ImageFont.truetype("DejaVuSans.ttf",12)
    title=ImageFont.truetype("DejaVuSans.ttf",15)
    by_title={item['title']:item for item in items if item['kind']=='quest_art'}
    picks=[('Your First Raid','First raid'),('Rainbow Badge','Rainbow Badge'),
           ('Your First Waystone','Waystone'),('Undead Pirate Ship','Pirate ship'),
           ('Sakura Grove','Sakura Grove'),('Shattered Glacier','Shattered Glacier'),
           ('Pokédex: 100% Caught','Pokédex complete'),('★ The Complete Pharmacy','Complete pharmacy')]
    if all(name in by_title for name,_ in picks):
        film=[]
        for phase in PLAY:
            page=Image.new('RGB',(552,236),'#10141f')
            draw=ImageDraw.Draw(page)
            for i,(name,label) in enumerate(picks):
                x=138*(i%4)
                y=118*(i//4)
                draw.rounded_rectangle((x+3,y+3,x+134,y+114),radius=7,fill='#20293a')
                sprite=by_title[name]['frames'][phase].resize((80,80),Image.Resampling.NEAREST)
                page.paste(sprite,(x+29,y+7),sprite)
                draw.text((x+69,y+95),label,font=small,anchor='mm',fill='#f4f4ff')
            film.append(page)
        film[0].save(OUT/'Astra-Round-4-Preview.gif',save_all=True,append_images=film[1:],
                     duration=180,loop=0,disposal=2)
    for start in range(0,len(items),20):
        page=items[start:start+20]
        w,h=210,135
        base=Image.new("RGB",(5*w,((len(page)+4)//5)*h+35),"#10141f")
        d=ImageDraw.Draw(base)
        d.text((12,8),f"Astra 4   /   {start+1}–{start+len(page)}",font=title,fill="#edf0fa")
        for i,item in enumerate(page):
            x,y=(i%5)*w,35+(i//5)*h
            d.rounded_rectangle((x+3,y+2,x+w-4,y+h-4),radius=6,fill="#20293a")
            f=item["frames"][0]
            big=f.resize((72,72),Image.Resampling.NEAREST)
            base.paste(big,(x+20,y+4),big)
            base.paste(f,(x+131,y+40),f)
            label=item["title"]
            while d.textlength(label,font=title)>w-24:
                label=label[:-2]+"…"
            d.text((x+10,y+80),label,font=title,fill="#f4f4ff")
            d.text((x+10,y+106),item["chapter"],font=small,fill="#b5c4de")
        base.save(OUT/f"Review-{1+start//20:02}.png")
    cards=[]
    for item in items:
        esc=html.escape
        source=base64.b64encode(item["webp"]).decode()
        cards.append(f'<article data-s="{esc(item["title"]+" "+item["chapter"]+" "+item["id"],quote=True)}"><img width="96" height="96" alt="{esc(item["title"],quote=True)}" src="data:image/webp;base64,{source}"><p>{esc(item["chapter"])} · {"Chapter" if item["kind"]=="chapter_icon" else "Quest"}</p><h2>{esc(item["title"])}</h2></article>')
    gallery='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Astra · Round 4</title><style>*{box-sizing:border-box}body{background:#121723;color:#f5f1e6;font:16px system-ui;margin:0 auto;max-width:1450px;padding:28px}h1{font-size:32px;margin:0}header p,article p{color:#b5c4d8}input{width:min(100%,480px);background:#202a3c;border:1px solid #576683;border-radius:6px;color:white;padding:12px;font:inherit;margin:8px 0 18px}main{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:12px}article{background:#20293a;border:1px solid #354154;border-radius:8px;padding:16px}article img{display:block;image-rendering:pixelated;margin:0 auto 12px}article p{font-size:12px;margin:0}h2{font-size:16px;margin:4px 0}article[hidden]{display:none}</style><header><h1>Astra · Round 4</h1><p>Animated quest pictures and chapter icons</p><input id="find" type="search" aria-label="Find artwork" placeholder="Find a quest or chapter"></header><main>'''+"".join(cards)+'''</main><script>document.querySelector('#find').oninput=e=>{let s=e.target.value.toLowerCase();for(let c of document.querySelectorAll('article'))c.hidden=!c.dataset.s.toLowerCase().includes(s)}</script></html>'''
    (OUT/"Astra-Round-4-Gallery.html").write_text(gallery,encoding="utf-8")


def main():
    OUT.mkdir(exist_ok=True)
    catalog={r["id"]:r for r in EXPECTED}
    check(len(catalog)==len(EXPECTED)==220,"expected 220 unique workbook filenames")
    handoff=(REPO/"astra-art/Astra-Art-List-4.txt").read_text(encoding="utf-8-sig")
    for key in catalog:
        check(re.fullmatch(r"[a-z0-9_]+\.png",key) and key in handoff,f"filename missing from repo handoff: {key}")
    made,failures,attempted={},[],set()
    def add(key,fn):
        check(key in catalog and key not in attempted, f"unknown or duplicate {key}")
        attempted.add(key)
        try:
            made[key]=export_one(catalog[key],fn())
        except Exception as exc:
            failures.append({"id":key,"reason":str(exc)})
    for meta in EXPECTED:
        hit=re.fullmatch(r"Pokédex: (\d+)% Caught",meta["title"])
        if hit:
            level=int(hit[1])//10
            add(meta["id"],lambda:frames_for(level))
    for manifest in sorted((ROOT/"art").glob("*/manifest.json")):
        sheets=json.loads(manifest.read_text(encoding="utf-8-sig"))["sheets"]
        for sheet in sheets:
            file=manifest.parent/sheet["file"]
            try:
                cells=read_atlas(file,sheet.get("columns",4),sheet.get("row_count",4))
            except Exception as exc:
                for row in sheet['rows']:
                    attempted.add(row['id'])
                    failures.append({'id':row['id'],'reason':str(exc)})
                continue
            for row in sheet["rows"]:
                check(row['title']==catalog[row['id']]['title'], 'title/filename mapping mismatch: '+row['id'])
                add(row["id"],lambda:normalize(cells[row["row"]]))
    missing=sorted(set(catalog)-attempted)
    check(len(set(a["sha256"] for a in made.values()))==len(made),"duplicate shipped artwork")
    ordered=[made[m["id"]] for m in EXPECTED if m["id"] in made]
    previews(ordered)
    passed=not missing and not failures
    validation={"status":"passed" if passed else "partial","expected":220,"exported":len(made),
                "quests":sum(r["kind"]=="quest_art" for r in ordered),
                "chapters":sum(r["kind"]=="chapter_icon" for r in ordered),
                "frames":4*len(made),"layout":"32x128; four 32x32 frames","missing":missing,"failures":failures,
                "in_game_tested":False,"assets":[{k:v for k,v in item.items() if k not in {"frames","png","webp"}} for item in ordered]}
    if passed:
        dest=OUT/"Astra-Round-4-Resource-Pack.zip"
        texture="assets/unyieldingquests/textures"
        contents={f'{texture}/{r["kind"]}/{r["id"]}':r["png"] for r in ordered}
        src=json.loads((REPO/"astra-art/UwU-Quest-Art-Pack/pack.mcmeta").read_text(encoding="utf-8-sig"))
        src["pack"]["description"]="UwU Quest Art Pack (Astra) - round 4"
        contents["pack.mcmeta"]=json.dumps(src,indent=2).encode()
        with zipfile.ZipFile(dest,"w",zipfile.ZIP_DEFLATED) as archive:
            for name,data in sorted(contents.items()):
                archive.writestr(name,data)
        with zipfile.ZipFile(dest) as archive:
            check(archive.testzip() is None and set(archive.namelist())==set(contents),"invalid pack ZIP")
        validation["pack_sha256"]=hashlib.sha256(dest.read_bytes()).hexdigest()
    (OUT/"validation.json").write_text(json.dumps(validation,indent=2,ensure_ascii=False),encoding="utf-8")
    print(f'Exported {len(ordered)}. Missing {len(missing)}. Failed {len(failures)}.',flush=True)
    for f in failures:
        print(f)
    if failures or ((ROOT/"final").exists() and not passed):
        raise SystemExit(1)


if __name__=="__main__":
    main()
