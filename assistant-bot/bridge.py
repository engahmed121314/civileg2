"""CivilEG2 Assistant Bot — bridge between Telegram and opencode.

What it does:
  - Long-polls your Telegram bot for incoming messages.
  - Only answers YOUR chat id (everyone else is ignored).
  - /start | /status | /diff | /log  -> quick repo info (no AI call).
  - Any other message -> forwarded to `opencode run "<text>"` inside the
    civileg2 project, and the result is sent back to Telegram.

Requirements: Python 3.8+ (stdlib only), `opencode` CLI on PATH.
Run:  python bridge.py   (or double-click run.bat on Windows)
"""

import json
import os
import subprocess
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(HERE)  # civileg2/
CONFIG_FILE = os.path.join(HERE, "config.env")
CHUNK_LIMIT = 3900
POLL_TIMEOUT = 30
OPENCODE_TIMEOUT = 900  # 15 min max per request


def find_opencode():
    """Locate the opencode CLI: PATH first, then known install spots."""
    import shutil
    for name in ("opencode", "opencode.exe", "opencode-cli",
                 "opencode-cli.exe"):
        p = shutil.which(name)
        if p:
            return p
    candidates = [
        os.path.join(os.environ.get("LOCALAPPDATA", ""),
                     "OpenCode", "opencode-cli.exe"),
        os.path.expanduser("~/.opencode/bin/opencode"),
    ]
    for p in candidates:
        if p and os.path.isfile(p):
            return p
    return None


OPENCODE_BIN = find_opencode()


def load_config():
    cfg = {}
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                cfg[k.strip()] = v.strip().strip('"').strip("'")
    return {
        "token": os.environ.get("BOT_TOKEN", cfg.get("BOT_TOKEN", "")),
        "chat_id": os.environ.get("ALLOWED_CHAT_ID", cfg.get("ALLOWED_CHAT_ID", "")),
    }


def api(token, method, params=None, timeout=45):
    url = "https://api.telegram.org/bot%s/%s" % (token, method)
    data = urllib.parse.urlencode(params or {}).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def send(token, chat_id, text):
    for i in range(0, max(len(text), 1), CHUNK_LIMIT):
        chunk = text[i:i + CHUNK_LIMIT] or "(empty response)"
        api(token, "sendMessage", {"chat_id": chat_id, "text": chunk})
        if i + CHUNK_LIMIT < len(text):
            time.sleep(0.4)


def run_cmd(args, cwd=PROJECT_ROOT, timeout=60):
    try:
        p = subprocess.run(
            args, cwd=cwd, capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=timeout,
        )
        out = (p.stdout or "") + (("\n[stderr]\n" + p.stderr) if p.stderr else "")
        return out.strip() or "(no output, exit=%d)" % p.returncode
    except FileNotFoundError:
        return "ERROR: command not found: %s" % args[0]
    except subprocess.TimeoutExpired:
        return "ERROR: command timed out after %ds" % timeout


def opencode_available():
    return OPENCODE_BIN is not None


HELP = (
    "CivilEG2 Assistant Bot\n"
    "Send any message and I run it in opencode on the civileg2 project.\n\n"
    "Commands:\n"
    "/start - this help\n"
    "/status - git branch + working tree status\n"
    "/diff - changed files summary\n"
    "/log - last 5 commits\n\n"
    "Anything else is executed as an opencode coding task."
)


def handle(token, allowed, chat_id, text):
    text = (text or "").strip()
    if text in ("/start", "/help"):
        return HELP
    if text == "/status":
        branch = run_cmd(["git", "branch", "--show-current"])
        status = run_cmd(["git", "status", "--porcelain"])
        return "Branch: %s\n\n%s" % (branch, status or "(clean tree)")
    if text == "/diff":
        return run_cmd(["git", "diff", "--stat"]) or "(no changes)"
    if text == "/log":
        return run_cmd(["git", "log", "--oneline", "-5"])
    # Anything else -> opencode coding task
    if not OPENCODE_BIN:
        return ("ERROR: `opencode` CLI not found. Install it or add it to PATH, "
                "then restart the bridge.")
    send(token, allowed, "Working on it...\n%s" % text[:200])
    return run_cmd([OPENCODE_BIN, "run", text], timeout=OPENCODE_TIMEOUT)


def main():
    cfg = load_config()
    token, allowed = cfg["token"], cfg["chat_id"]
    if not token or not allowed:
        print("Missing BOT_TOKEN / ALLOWED_CHAT_ID in assistant-bot/config.env")
        sys.exit(1)
    try:
        me = api(token, "getMe")
        print("Bot: @%s (id=%s)" % (
            me["result"].get("username"), me["result"].get("id")))
    except Exception as e:  # noqa: BLE001
        print("Cannot reach Telegram API (check token / network / VPN): %s" % e)
        sys.exit(1)
    if not opencode_available():
        print("WARNING: `opencode` CLI not found on PATH. "
              "AI tasks will fail until it is installed.")
    else:
        print("opencode CLI: %s" % OPENCODE_BIN)
    print("Listening for chat_id=%s ... (Ctrl+C to stop)" % allowed)

    offset = 0
    while True:
        try:
            updates = api(token, "getUpdates",
                          {"offset": offset, "timeout": POLL_TIMEOUT},
                          timeout=POLL_TIMEOUT + 10)
        except Exception as e:  # noqa: BLE001 - network hiccup, keep polling
            print("poll error: %s" % e)
            time.sleep(3)
            continue
        for u in updates.get("result", []):
            offset = u["update_id"] + 1
            msg = u.get("message") or {}
            chat = str((msg.get("chat") or {}).get("id", ""))
            if chat != str(allowed):
                continue  # ignore everyone else
            text = msg.get("text", "")
            if not text:
                continue
            who = (msg.get("from") or {}).get("username", "?")
            print("<< @%s: %s" % (who, text[:120]))
            try:
                reply = handle(token, allowed, chat, text)
            except Exception as e:  # noqa: BLE001
                reply = "ERROR: %s" % e
            try:
                send(token, allowed, reply)
            except Exception as e:  # noqa: BLE001
                print("send error: %s" % e)


if __name__ == "__main__":
    main()
