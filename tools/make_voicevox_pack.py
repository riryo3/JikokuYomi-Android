#!/usr/bin/env python3
"""Makes a voice pack for 時刻読み with a running VOICEVOX engine (http://127.0.0.1:50021).

  python3 tools/make_voicevox_pack.py --speaker 3 --out ~/Desktop/pack          # ずんだもん ノーマル
  python3 tools/make_voicevox_pack.py --list                                     # speakers and their ids
  python3 tools/make_voicevox_pack.py --speaker 3 --only t0715 m30 --out /tmp/t  # try a few first

Output: t0000.wav ... t2345.wav (every 15 minutes), m15.wav, m30.wav, m45.wav, and pack.json.
The lines are made from the template below: edit PREFIX / SUFFIX or pass --prefix / --suffix.
VOICEVOX voices have their own terms of use (credit "VOICEVOX:キャラクター名"); check them before sharing a pack.
"""
import argparse
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ENGINE = "http://127.0.0.1:50021"
PREFIX = "はいはい、"
SUFFIX = "だよ"


def hour_word(h12: int, h24: int, noon: bool) -> str:
    # 0時 is 零時 so the voice reads れいじ; the rest are digits ("4時" is read よじ).
    return "零時" if h12 == 0 else f"{h12}時"


def line(hour: int, minute: int, prefix: str, suffix: str, twelve_hour: bool = True) -> str:
    """The spoken line for a time, in the same words the app uses (午前/午後, 半, 正午)."""
    if twelve_hour and hour == 12 and minute == 0:
        return f"{prefix}正午{suffix}"
    shown = hour % 12 if twelve_hour else hour
    text = "零時" if shown == 0 else f"{shown}時"
    if minute == 30:
        text += "半"          # "半" on its own is read なかば, but after 時 the voice reads はん
    elif minute != 0:
        text += f"{minute}分"
    ampm = ("午前" if hour < 12 else "午後") if twelve_hour else ""
    return f"{prefix}{ampm}{text}{suffix}"


def minutes_only_line(minute: int, prefix: str, suffix: str) -> str:
    return f"{prefix}{'半' if minute == 30 else str(minute) + '分'}{suffix}"


def synthesize(text: str, speaker: int, speed: float) -> bytes:
    query_url = f"{ENGINE}/audio_query?" + urllib.parse.urlencode({"text": text, "speaker": speaker})
    query = json.loads(urllib.request.urlopen(urllib.request.Request(query_url, method="POST"), timeout=60).read())
    query["speedScale"] = speed
    request = urllib.request.Request(
        f"{ENGINE}/synthesis?speaker={speaker}", data=json.dumps(query).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    return urllib.request.urlopen(request, timeout=120).read()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--speaker", type=int, help="VOICEVOX speaker (style) id; see --list")
    ap.add_argument("--out", type=Path, default=Path("voicevox-pack"))
    ap.add_argument("--speed", type=float, default=1.0)
    ap.add_argument("--prefix", default=PREFIX)
    ap.add_argument("--suffix", default=SUFFIX)
    ap.add_argument("--24h", dest="h24", action="store_true", help="say 14時 instead of 午後2時")
    ap.add_argument("--only", nargs="*", help="make only these files, e.g. t0715 m30")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args()

    try:
        speakers = json.loads(urllib.request.urlopen(f"{ENGINE}/speakers", timeout=5).read())
    except OSError:
        print("VOICEVOXが起動していません。VOICEVOXを開いてから、もう一度実行してください。", file=sys.stderr)
        return 1
    if args.list or args.speaker is None:
        for sp in speakers:
            print(sp["name"], ", ".join(f"{s['name']}={s['id']}" for s in sp["styles"]))
        return 0

    label = next((f"{sp['name']}({s['name']})" for sp in speakers for s in sp["styles"] if s["id"] == args.speaker), str(args.speaker))
    jobs = []
    for h in range(24):
        for m in (0, 15, 30, 45):
            jobs.append((f"t{h:02d}{m:02d}", line(h, m, args.prefix, args.suffix, not args.h24)))
    for m in (15, 30, 45):
        jobs.append((f"m{m}", minutes_only_line(m, args.prefix, args.suffix)))
    if args.only:
        jobs = [j for j in jobs if j[0] in args.only]

    args.out.mkdir(parents=True, exist_ok=True)
    for index, (name, text) in enumerate(jobs, 1):
        (args.out / f"{name}.wav").write_bytes(synthesize(text, args.speaker, args.speed))
        print(f"[{index}/{len(jobs)}] {name}.wav  {text}")
    (args.out / "pack.json").write_text(json.dumps({"name": f"VOICEVOX {label}", "credit": f"VOICEVOX:{label}"}, ensure_ascii=False), encoding="utf-8")
    print(f"\n{len(jobs)} 個を {args.out} に作りました。アプリの「音声パックを取り込む」で、このフォルダを選んでください。")
    print(f"クレジット表記: VOICEVOX:{label.split('(')[0]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
