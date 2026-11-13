import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

APP = "com.example.barcodescanner"
UI_PATH = "/data/local/tmp/barcode-readme.xml"
CAPTURES = Path("captures")
CAPTURES.mkdir(exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", *args], stderr=subprocess.STDOUT, timeout=30)


def shell(*args):
    return adb("shell", *map(str, args)).decode().strip()


def layout():
    shell("uiautomator", "dump", "--compressed", UI_PATH)
    return ET.fromstring(adb("exec-out", "cat", UI_PATH))


def find(predicate, label, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            for node in layout().iter("node"):
                if predicate(node):
                    return node
        except (ET.ParseError, subprocess.SubprocessError):
            pass
        time.sleep(0.35)
    (CAPTURES / "diagnostic.png").write_bytes(adb("exec-out", "screencap", "-p"))
    raise RuntimeError(f"Missing UI element: {label}")


def by_id(name):
    value = name if ":id/" in name else f"{APP}:id/{name}"
    return find(lambda node: node.get("resource-id") == value, value)


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    shell("input", "tap", (x1 + x2) // 2, (y1 + y2) // 2)


def capture(name):
    time.sleep(1)
    (CAPTURES / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    print(f"Captured {name}", flush=True)


def main():
    shell("am", "start", "-W", "-n", f"{APP}/.MainActivity")
    by_id("manual")


def lookup(language):
    tap(by_id("manual"))
    find(lambda node: node.get("class") == "android.widget.EditText" and node.get("focused") == "true",
         "focused barcode input")
    time.sleep(1)
    if language == "tr":
        capture("barcode-tr")
    shell("input", "text", "3017620422003")
    find(lambda node: node.get("class") == "android.widget.EditText" and node.get("text") == "3017620422003",
         "entered barcode")
    tap(by_id("android:id/button1"))
    find(lambda node: node.get("resource-id") == f"{APP}:id/tvProductName" and "Nutella" in node.get("text", ""),
         "Nutella product", timeout=50)




adb("install", "app/build/outputs/apk/debug/app-debug.apk")
adb("install", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
shell("wm", "size", "900x1950")
shell("wm", "density", 360)
result = subprocess.run(["adb", "shell", "am", "instrument", "-w", "-r",
                         f"{APP}.test/androidx.test.runner.AndroidJUnitRunner"],
                        capture_output=True, text=True, timeout=220, check=False)
(CAPTURES / "instrumentation.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
print(result.stdout, flush=True)
tests_ok = bool(re.search(r"^OK \(\d+ tests?\)", result.stdout, re.MULTILINE))

shell("pm", "clear", APP)
shell("pm", "grant", APP, "android.permission.CAMERA")
shell("cmd", "uimode", "night", "no")
for language in ("tr", "en"):
    shell("am", "force-stop", APP)
    shell("cmd", "locale", "set-app-locales", APP, "--user", 0, "--locales", language)
    main()
    time.sleep(7)
    capture(f"scan-{language}")
    lookup(language)
    time.sleep(5)
    capture(f"product-{language}")
    if language == "tr":
        tap(by_id("btnEditLabel"))
        find(lambda node: "100 mL" in node.get("text", ""), "package unit toggle")
        capture("label-tr")
        shell("input", "keyevent", "KEYCODE_BACK")
    shell("input", "keyevent", "KEYCODE_BACK")
    by_id("manual")
    tap(by_id("history"))
    find(lambda node: node.get("resource-id") == f"{APP}:id/recordDetails" and
         "3017620422003" in node.get("text", ""), "history row")
    capture(f"history-{language}")
    shell("input", "keyevent", "KEYCODE_BACK")
    if language == "tr":
        tap(by_id("catalogSettings"))
        by_id("catalogAddress")
        capture("catalog-tr")
        shell("input", "keyevent", "KEYCODE_BACK")

shell("cmd", "uimode", "night", "yes")
shell("am", "force-stop", APP)
main()
time.sleep(7)
capture("scan-dark")
if not tests_ok:
    raise SystemExit("Android instrumentation failed. See instrumentation.txt.")