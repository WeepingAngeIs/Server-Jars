"""Original 32-pixel handheld design with ten exact, registered progress levels."""
from PIL import Image, ImageDraw

# Each level keeps the same casing and display. New materials distinguish milestones.
CASINGS = [
    ("#c9364d", "#f96763", "#802641"),
    ("#dd484a", "#ff9065", "#82323c"),
    ("#d86537", "#ffb26a", "#91432d"),
    ("#bc7847", "#ffd38c", "#744536"),
    ("#398e91", "#86e5cc", "#285b70"),
    ("#477eb6", "#a4dfff", "#384d83"),
    ("#8863b6", "#dfb3ff", "#563e79"),
    ("#cb8185", "#ffcfab", "#825465"),
    ("#dfab3e", "#fff0a2", "#916225"),
    ("#68c3d8", "#e7ffff", "#5664a1"),
]


def frames_for(level):
    base, light, shade = CASINGS[level - 1]
    ink, shadow, white = "#19213b", "#284356", "#fff2cb"
    teal, mint, lcd = "#1d655e", "#a2f4ae", "#4fc093"
    frames = []
    for phase in range(4):
        image = Image.new("RGBA", (32, 32))
        pen = ImageDraw.Draw(image)
        pen.polygon([(5,2),(15,2),(17,4),(25,4),(28,7),(28,26),(25,29),(6,29),(3,26),(3,5)], fill=ink)
        pen.polygon([(6,3),(15,3),(17,5),(24,5),(27,8),(27,25),(24,28),(7,28),(4,25),(4,6)], fill=shade)
        pen.polygon([(6,4),(14,4),(16,6),(24,6),(26,8),(26,24),(24,26),(7,26),(5,24),(5,6)], fill=base)
        pen.line([(5,10),(5,6),(7,4),(12,4)], fill=light)
        pen.rectangle((8,5,10,7), fill=ink)
        pen.point((9,6), fill=[mint,white,lcd,white][phase])
        for x in (13,15):
            pen.point((x,7), fill=light)
        pen.polygon([(7,9),(24,9),(25,10),(25,18),(24,19),(7,19),(6,18),(6,10)], fill=ink)
        pen.rectangle((8,10,23,17), fill=teal)
        pen.line((8,10,21,10),fill=lcd)
        # Leaf-eared field creature silhouette rendered on the handheld's LCD.
        pen.polygon([(10,12),(12,13),(14,13),(16,11),(16,15),(15,16),(11,16),(10,15)],fill=mint)
        pen.point((12,14),fill=teal)
        pen.point((14,14),fill=teal)
        pen.line((19,15,21,15),fill=lcd)
        pen.point((19+phase,12), fill=white)
        # Ten discrete, equally sized positions make percentage exact at native scale.
        pen.rectangle((5,20,26,23),fill=ink)
        for slot in range(10):
            x = 6 + 2*slot
            pen.rectangle((x,21,x,22),fill=mint if slot<level else shadow)
        pen.line((8,25,11,25),fill=shade)
        pen.line((21,25,23,25),fill=light)
        if level >= 3:
            pen.point((25,7),fill=white)
        if level >= 5:
            pen.line((6,27,10,27),fill=light)
        if level >= 7:
            pen.line((18,6,21,6),fill=white)
        if level == 10:
            for x,c in enumerate(("#ff8098","#ffd072","#a2f4ae","#86dcff","#c5a9ff")):
                pen.line((17+2*x,6,18+2*x,6),fill=c)
            pen.polygon([(15,24),(17,25),(15,27),(13,25)],fill=white)
            pen.point((15,25),fill="#86dcff")
        frames.append(image)
    return frames
