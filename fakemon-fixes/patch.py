#!/usr/bin/env python3
"""Asset-only fixes for the exact user-selected Unyielding 1.1.7 release.

Restore Cobblemon 1.8.4 Sliggoo's eyes/mouth on Fairy Sliggoo and hold only
Inkyeon's rear-leg chains in their existing neutral standing pose. No gameplay,
other species, walking, front-leg, ribbon, body or tail changes are permitted.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import io
import json
import math
from pathlib import Path
import re
import zipfile
from PIL import Image, ImageChops, ImageDraw

BASE_SHA256 = '876595a2d8bc79589bfde81c7253354e76e042eb9eefdb7db847d3968f85c09b'
FAIRY_MODEL = 'assets/mawonna/bedrock/pokemon/models/fairy_sliggoo/fairy_sliggoo.geo.json'
INK_ANIMATION = 'assets/mawonna/bedrock/pokemon/animations/inkyeon/inkyeon.animation.json'
FAIRY_TEXTURE = 'assets/mawonna/textures/pokemon/fairy_sliggoo/fairy_sliggoo{}.png'
REFERENCE_MODEL = 'assets/cobblemon/bedrock/pokemon/models/0705_sliggoo/sliggoo.geo.json'
REFERENCE_TEXTURE = 'assets/cobblemon/textures/pokemon/0705_sliggoo/sliggoo{}.png'
FACE_BONES = ('eye_left', 'eye_right', 'mouth')
FACE_TEXTURE_BONES = (*FACE_BONES, 'mouth_big')
IDLES = ('animation.inkyeon.playful_idle', 'animation.inkyeon.battle_idle')
REAR_BONES = tuple(f'{joint}_{side}' for side in ('left', 'right')
                   for joint in ('back_leg', 'knee', 'ankle', 'foot'))
ALLOWED = {FAIRY_MODEL, INK_ANIMATION, FAIRY_TEXTURE.format(''), FAIRY_TEXTURE.format('_shiny')}


def require(ok: bool, message: str) -> None:
    if not ok:
        raise ValueError(message)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def encode_json(data: object) -> bytes:
    return (json.dumps(data, indent=2, ensure_ascii=False, allow_nan=False) + '\n').encode('utf-8')


def reject_duplicates(pairs: list[tuple[str, object]]) -> dict:
    result = {}
    for key, value in pairs:
        require(key not in result, f'Duplicate JSON key: {key}')
        result[key] = value
    return result


def load_json(data: bytes | str) -> dict:
    return json.loads(data, object_pairs_hook=reject_duplicates)


def face_rects(cube: dict) -> list[tuple[float, float, float, float]]:
    """Actual nonzero-area texture faces, not the unused bounding-box corners."""
    uv = cube['uv']
    if isinstance(uv, dict):
        rects = []
        for face in uv.values():
            u, v = face['uv']
            w, h = face['uv_size']
            rects.append((min(u, u+w), min(v, v+h), max(u, u+w), max(v, v+h)))
    else:
        u, v = uv
        w, h, d = cube['size']
        rects = [(u, v+d, u+d, v+d+h),
                 (u+d, v+d, u+d+w, v+d+h),
                 (u+d+w, v+d, u+2*d+w, v+d+h),
                 (u+2*d+w, v+d, u+2*d+2*w, v+d+h),
                 (u+d, v, u+d+w, v+d),
                 (u+d+w, v, u+d+2*w, v+d)]
    return [r for r in rects if r[0] < r[2] and r[1] < r[3]]


def overlap(a: tuple, b: tuple) -> bool:
    return max(a[0], b[0]) < min(a[2], b[2]) and max(a[1], b[1]) < min(a[3], b[3])


def patch_fairy(entries: dict[str, bytes], reference: Path) -> dict:
    original = load_json(entries[FAIRY_MODEL])
    model = copy.deepcopy(original)
    target = model['minecraft:geometry'][0]
    stock = load_json((reference / REFERENCE_MODEL).read_bytes())['minecraft:geometry'][0]
    regular_bones = {bone['name']: bone for bone in stock['bones']}
    old_bones = {bone['name']: bone for bone in target['bones']}
    require(old_bones['head'] == regular_bones['head'], 'Head coordinates no longer match Sliggoo')
    require(target['description']['texture_width'] == 512 and
            target['description']['texture_height'] == 256, 'Unexpected Fairy atlas size')
    for index, bone in enumerate(target['bones']):
        if bone['name'] in FACE_BONES:
            require({k:v for k,v in bone.items() if k != 'cubes'} ==
                    {k:v for k,v in regular_bones[bone['name']].items() if k != 'cubes'},
                    'Face hierarchy changed; manual review required')
            target['bones'][index] = copy.deepcopy(regular_bones[bone['name']])
    for bone in target['bones']:
        if bone['name'] not in FACE_BONES:
            require(bone == old_bones[bone['name']], 'Non-face geometry changed')
    require(model['minecraft:geometry'][0]['description'] ==
            original['minecraft:geometry'][0]['description'], 'Model description changed')

    rects = sorted({tuple(map(int, r)) for name in FACE_TEXTURE_BONES
                    for cube in regular_bones[name]['cubes'] for r in face_rects(cube)})
    for bone in target['bones']:
        if bone['name'] in FACE_TEXTURE_BONES:
            continue
        for cube in bone.get('cubes', []):
            for used in face_rects(cube):
                require(not any(overlap(used, r) for r in rects),
                        f'Face UV overlaps non-face part: {bone["name"]}')

    pixel_report = {}
    for variant in ('', '_shiny'):
        path = FAIRY_TEXTURE.format(variant)
        image = Image.open(io.BytesIO(entries[path])).convert('RGBA')
        before = image.copy()
        stock_image = Image.open(reference / REFERENCE_TEXTURE.format(variant)).convert('RGBA')
        require(stock_image.size == (64, 64), 'Unexpected stock Sliggoo texture dimensions')
        require(image.size == (2048, 1024), 'Unexpected Fairy texture dimensions')
        scale = 4
        mask = Image.new('L', image.size, 0)
        draw = ImageDraw.Draw(mask)
        for x0, y0, x1, y1 in rects:
            destination = (x0*scale, y0*scale, x1*scale, y1*scale)
            patch = stock_image.crop((x0, y0, x1, y1)).resize(
                ((x1-x0)*scale, (y1-y0)*scale), Image.Resampling.NEAREST)
            image.paste(patch, destination)
            draw.rectangle((destination[0], destination[1], destination[2]-1, destination[3]-1), fill=255)
            require(image.crop(destination).tobytes() == patch.tobytes(), 'Face pixels mismatch')
        # Compare all four channels: RGBA.getbbox() alone can miss RGB-only changes.
        changed = ImageChops.difference(before, image)
        any_channel = Image.new('L', image.size, 0)
        for channel in changed.split():
            any_channel = ImageChops.lighter(any_channel, channel)
        outside = ImageChops.multiply(any_channel, ImageChops.invert(mask))
        require(outside.getbbox() is None, 'Pixels outside face regions changed')
        pixel_report['shiny' if variant else 'normal'] = {
            'face_pixels_changed': sum(any_channel.histogram()[1:]),
            'outside_face_pixels_changed': 0,
            'reference_texture_sha256': sha256((reference / REFERENCE_TEXTURE.format(variant)).read_bytes()),
        }
        output = io.BytesIO()
        image.save(output, format='PNG', optimize=True)
        entries[path] = output.getvalue()
    entries[FAIRY_MODEL] = encode_json(model)
    return {'stock_version': 'Cobblemon 1.8.4 NeoForge',
            'restored_model_bones': list(FACE_BONES), 'face_uv_rectangles': rects,
            'non_face_bones_unchanged': True, 'textures': pixel_report,
            'reference_model_sha256': sha256((reference / REFERENCE_MODEL).read_bytes())}


def patch_inkyeon(entries: dict[str, bytes]) -> dict:
    before = load_json(entries[INK_ANIMATION])
    after = copy.deepcopy(before)
    animations = after['animations']
    neutral = animations['animation.inkyeon.ground_idle']['bones']
    geometry = load_json(entries['assets/mawonna/bedrock/pokemon/models/inkyeon/inkyeon.geo.json'])
    bones = {bone['name']: bone for bone in geometry['minecraft:geometry'][0]['bones']}
    for name in IDLES:
        anim = animations[name]
        for bone in REAR_BONES:
            require(bone in anim['bones'] and bone in bones, f'Missing rear joint: {bone}')
            anim['bones'][bone] = copy.deepcopy(neutral.get(bone, {'rotation': [0, 0, 0]}))
        for bone, channels in anim['bones'].items():
            if bone not in REAR_BONES:
                require(channels == before['animations'][name]['bones'][bone], 'Other motion changed')
        require({k:v for k,v in anim.items() if k != 'bones'} ==
                {k:v for k,v in before['animations'][name].items() if k != 'bones'},
                'Idle playback settings changed')
        # Every rear joint and ancestor is time-independent. This proves the full
        # rear chain stays stationary, not merely that one hip track was removed.
        for end in ('foot_left', 'foot_right'):
            current = end
            while current:
                for channel, value in anim['bones'].get(current, {}).items():
                    require(isinstance(value, list) and len(value) == 3 and
                            all(isinstance(x, (int, float)) and math.isfinite(x) for x in value),
                            f'Rear-chain ancestor remains animated: {current}/{channel}')
                current = bones[current].get('parent')
    for name, anim in animations.items():
        if name not in IDLES:
            require(anim == before['animations'][name], f'Other animation changed: {name}')
    entries[INK_ANIMATION] = encode_json(after)
    return {'changed_animations': list(IDLES), 'stabilized_bones': list(REAR_BONES),
            'standing_and_battle_rear_chains_time_independent': True,
            'ground_idle_and_walking_unchanged': True,
            'all_other_bones_and_animations_unchanged': True}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(args.input.resolve() != args.output.resolve(), 'Never overwrite the source JAR')
    require(sha256(args.input.read_bytes()) == BASE_SHA256, 'Wrong input JAR; refuses to patch another 1.1.7 build')
    with zipfile.ZipFile(args.input) as jar:
        require(jar.testzip() is None, 'Corrupt input archive')
        infos = jar.infolist()
        names = [item.filename for item in infos]
        require(len(names) == len(set(names)), 'Duplicate JAR entries')
        require(not any(re.search(r'^META-INF/[^/]+\.(SF|RSA|DSA|EC)$', n, re.I) for n in names), 'Signed JAR requires a source rebuild')
        require(not any(n.startswith('/') or '..' in Path(n).parts for n in names), 'Unsafe path')
        original = {item.filename: jar.read(item) for item in infos}
        comment = jar.comment
    entries = dict(original)
    report = {'input_sha256': BASE_SHA256, 'runtime_tested': False,
              'validation_scope': 'Archive, JSON, exact stock face assets, rear-chain motion and preservation audit; no live game test.'}
    report['fairy_sliggoo'] = patch_fairy(entries, args.reference)
    report['inkyeon'] = patch_inkyeon(entries)
    changed = {name for name in entries if entries[name] != original[name]}
    require(changed == ALLOWED, f'Unexpected changed entries: {changed}')
    for name, raw in entries.items():
        if name.endswith('.json'):
            load_json(raw)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as jar:
        jar.comment = comment
        for info in infos:
            jar.writestr(copy.copy(info), entries[info.filename])
    with zipfile.ZipFile(args.output) as jar:
        require(jar.testzip() is None, 'Corrupt output archive')
        require(jar.namelist() == names, 'Archive inventory/order changed')
        for name in names:
            require(jar.read(name) == entries[name], 'Write verification failed')
            if name not in ALLOWED:
                require(jar.read(name) == original[name], f'Unrelated file changed: {name}')
    report.update({'status': 'passed', 'output': args.output.name,
                   'output_sha256': sha256(args.output.read_bytes()),
                   'total_entries': len(entries), 'unchanged_entries': len(entries)-len(changed),
                   'changed_entries': [{ 'path': name, 'before_sha256': sha256(original[name]),
                                        'after_sha256': sha256(entries[name])} for name in sorted(changed)]})
    (args.output.parent/'verification.json').write_bytes(encode_json(report))
    (args.output.parent/'SHA256SUMS.txt').write_text(f'{report["output_sha256"]}  {args.output.name}\n')
    instructions = '''Unyielding 1.1.7: Fairy Sliggoo + Inkyeon visual fixes\n\nReplace the existing Unyielding-1.1.7.jar in the client mods folder with this\nfile while Minecraft is closed. Do not install two copies of Unyielding.\nUse the same patched JAR on the server when synchronizing the pack.\nKeep the original as a backup outside the mods folder.\n\nFairy Sliggoo: original Cobblemon 1.8.4 eyes and mouth restored, including\nnormal/shiny face textures. Fairy decorations and all other pixels retained.\nInkyeon: only the rear hip/knee/ankle/foot chains in standing and battle idle\nare stabilized. Walking, front legs, body, tail and ribbons are unchanged.\n\nNo stats, evolutions, moves, riding definitions, quests, Java classes, version\nmetadata, Mawonna, Corvislight or other species were changed.\n\nAn old separate Mawonna/Fakemon resource pack can override the embedded\nassets. Disable an obsolete duplicate only when it supplies these assets.\nLeave unrelated resource packs and the Reactive Music songpack alone.\n\nValidation: structural and preservation checks passed. This patch has not\nbeen tested visually in a running Minecraft client.\n\nSource face assets: Cobblemon 1.8.4, Sliggoo normal/shiny. Existing bundled\nlicense and credit documents remain unchanged.\n'''
    (args.output.parent/'INSTALL.txt').write_text(instructions)
    # Separate source overlay for inspection, not a second mod to install.
    overlay = args.output.parent/'changed-assets.zip'
    with zipfile.ZipFile(overlay, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(changed):
            z.writestr(name, entries[name])
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
