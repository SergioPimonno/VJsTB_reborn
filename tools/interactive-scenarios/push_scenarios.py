"""
One-shot: log in to ledscheme-server as ADMIN and upsert the INTERACTIVE_SCENARIOS
singleton from interactive-scenarios.full.json — the same call the admin console's
"Импортировать из файла…" + "Сохранить" makes (AdminLibraryClient.upsertSingleton).

Password is read from a prompt (never an arg, never echoed). Cert check is disabled
because the server uses a self-signed Caddy cert on :8443 (same as TrustedHttp
pinning in the real clients) — this talks to your own server from your own machine.

Run:  python push_scenarios.py
"""
import json, os, ssl, sys, getpass, urllib.request, urllib.error

BASE = "https://138.16.177.176:8443"
USER = "SergioPimonno"
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "interactive-scenarios.full.json")

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE


def _req(path, method, token=None, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=30) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        sys.exit(f"HTTP {e.code} on {method} {path}: {e.read().decode('utf-8', 'replace')}")


pw = getpass.getpass(f"Admin password for {USER} @ {BASE}: ")

auth = _req("/api/auth/login", "POST", body={"username": USER, "password": pw})
if auth.get("role") != "ADMIN":
    sys.exit(f"role is {auth.get('role')}, need ADMIN — aborting")
token = auth["token"]
print("logged in as ADMIN")

src = json.load(open(SRC, encoding="utf-8"))
payload = {"scenarios": src["scenarios"]}
steps = sum(len(s["steps"]) for s in payload["scenarios"])
imgs = sum("imageBase64" in st for s in payload["scenarios"] for st in s["steps"])
hots = sum("hotspotX" in st for s in payload["scenarios"] for st in s["steps"])
body_json = json.dumps(payload, ensure_ascii=False)
print(f"source: {len(payload['scenarios'])} scenarios, {steps} steps "
      f"({imgs} with image, {hots} with hotspot), payload {len(body_json.encode()) / 1024 / 1024:.2f} MB")

res = _req("/api/admin/library/singleton/INTERACTIVE_SCENARIOS", "POST", token,
           body={"payloadJson": body_json})
print(f"saved singleton: id={res.get('id')} kind={res.get('kind')} globalSeq={res.get('globalSeq')}")

check = _req("/api/admin/library?kind=INTERACTIVE_SCENARIOS&includeDeleted=false", "GET", token)
on_server = json.loads(check[0]["payloadJson"])["scenarios"]
print(f"verify — server now holds {len(on_server)} scenarios:")
for s in on_server:
    si = sum("imageBase64" in st for st in s["steps"])
    sh = sum("hotspotX" in st for st in s["steps"])
    print(f"  - {s['id']:<22} {len(s['steps'])} steps, {si} img, {sh} hotspot   {s['title']}")
