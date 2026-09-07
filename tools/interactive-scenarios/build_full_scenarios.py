"""
Assemble interactive-scenarios.full.json: take the text steps from
../../interactive-scenarios.seed.json, attach a downscaled JPEG screenshot
(base64) to every step and a pulsing click-hotspot to the navigational steps,
using the PNGs and hotspots.json in ./shots/ (produced by the JUnit generator
ScenarioShotSpike: `mvn test -Dtest=ScenarioShotSpike -Dscenario.shots=true`).

Then publish with push_scenarios.py.
"""
import base64, io, json, os

HERE = os.path.dirname(os.path.abspath(__file__))
SEED = os.path.normpath(os.path.join(HERE, "..", "..", "interactive-scenarios.seed.json"))
SHOTS = os.path.join(HERE, "shots")
OUT = os.path.join(HERE, "interactive-scenarios.full.json")

from PIL import Image

MAX_W = 760
QUALITY = 66

# step -> (image basename, hotspot key or None), one tuple per step, in order.
MAP = {
    "tour-setup": [("setup", "stage:Сетап")] + [("setup", None)] * 8,
    "tour-power": [("power", "stage:Питание")] + [("power", None)] * 6
                  + [("power", "view:Общая схема питания"), ("power-schema", None)],
    "tour-signal": [("signal", "stage:Сигнал")] + [("signal", None)] * 5
                   + [("signal-schema", None), ("signal", None)],
    "tour-network-manager": [("signal", "view:Сетевой менеджер")]
                            + [("network", None)] * 4
                            + [("network", "net:Найти устройства"),
                               ("network", "net:Таблица адресов"),
                               ("network", None)],
    "tour-masks": [("masks", "stage:Генерация масок"), ("masks", "masks:Разместить экран")]
                  + [("masks", None)] * 4,
    "tour-output": [("output", "stage:Вывод")] + [("output", None)] * 4,
    "tour-libraries": [("libraries", "stage:Библиотеки")] + [("libraries", None)] * 6,
}

hotspots = json.load(open(os.path.join(SHOTS, "hotspots.json"), encoding="utf-8"))


def inflate(r):
    """Расширяем прямоугольник кнопки в заметную область-хотспот (клик всё равно
    попадает в саму кнопку, но подсветка крупнее и уверенно нажимается)."""
    x, y, w, h = r
    nx = max(0.0, x - 0.07 * w)
    ny = max(0.0, y - 0.60 * h)
    nw = min(w * 1.14, 1.0 - nx)
    nh = min(h * 2.2, 1.0 - ny)
    return [round(v, 5) for v in (nx, ny, nw, nh)]


_cache = {}
def enc(name, q):
    key = (name, q)
    if key not in _cache:
        im = Image.open(os.path.join(SHOTS, name + ".png")).convert("RGB")
        s = MAX_W / im.width
        im = im.resize((MAX_W, round(im.height * s)), Image.LANCZOS)
        b = io.BytesIO()
        im.save(b, "JPEG", quality=q, optimize=True)
        _cache[key] = base64.b64encode(b.getvalue()).decode("ascii")
    return _cache[key]


def build(quality):
    _cache.clear()
    seed = json.load(open(SEED, encoding="utf-8"))
    scenarios = []
    for sc in seed["scenarios"]:
        m = MAP[sc["id"]]
        assert len(m) == len(sc["steps"]), f'{sc["id"]}: map {len(m)} vs steps {len(sc["steps"])}'
        steps = []
        for st, (img, hk) in zip(sc["steps"], m):
            step = {"title": st["title"], "bodyHtml": st["bodyHtml"], "imageBase64": enc(img, quality)}
            if hk:
                hx, hy, hw, hh = inflate(hotspots[hk])
                step.update(hotspotX=hx, hotspotY=hy, hotspotWidth=hw, hotspotHeight=hh)
            steps.append(step)
        scenarios.append({"id": sc["id"], "title": sc["title"], "steps": steps})
    return {"scenarios": scenarios}


q = QUALITY
while True:
    payload = build(q)
    blob = json.dumps(payload, ensure_ascii=False)
    mb = len(blob.encode("utf-8")) / 1024 / 1024
    print(f"quality={q}: payload {mb:.2f} MB, "
          f"{sum(len(s['steps']) for s in payload['scenarios'])} steps, "
          f"{len(_cache)} distinct images")
    if mb <= 1.85 or q <= 38:
        break
    q -= 4

json.dump(payload, open(OUT, "w", encoding="utf-8"), ensure_ascii=False)
print("written", OUT)
