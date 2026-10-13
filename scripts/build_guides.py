#!/usr/bin/env python3
"""Build offline exercise guide videos: intro card (camera placement + how-to)
stitched to the pose-processed evidence clip (skeleton/angles/cues burned in).

Usage: python3 scripts/build_guides.py
Reads evidence mp4s from ./<name>/evidence.mp4, writes assets/guides/guide_<exid>.mp4
plus a guides.json manifest the app downloads/reads.
"""
import json, os, subprocess, sys

FF = os.environ.get("FFMPEG", "/home/ubuntu/.hermes/tools/ffmpeg-9.0.1-linux-x64/bin/ffmpeg")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_R = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
W, H, FPS = 1280, 720, 30

GUIDES = [
    {
        "exercise_id": "cervical_rotation_rom",
        "src": "yt_rom/evidence.mp4",
        "title_en": "Cervical rotation ROM",
        "title_ro": "Rotatie cervicala ROM",
        "camera_en": "Camera: FRONT view, chest facing camera",
        "camera_ro": "Camera: vedere FRONT, pieptul spre camera",
        "cue_en": "Watch the skeleton overlay: yellow = measured angle, red = form warning.",
        "cue_ro": "Urmareste overlay-ul: galben = unghi masurat, rosu = alerta de forma.",
    },
    {
        "exercise_id": "chin_tuck",
        "src": "yt_chin/evidence.mp4",
        "title_en": "Chin tuck (deep neck flexors)",
        "title_ro": "Retractie de barbie (flexori profunzi)",
        "camera_en": "Camera: SIDE view, left profile",
        "camera_ro": "Camera: vedere SIDE, profilul stang",
        "cue_en": "The overlay tracks ear-vs-shoulder offset: that is the tuck.",
        "cue_ro": "Overlay-ul urmareste decalajul ureche-umăr: aceea este retracția.",
    },
    {
        "exercise_id": "cervical_rotation_rom_nhs",
        "src": "nhs_rom/evidence.mp4",
        "title_en": "Neck rotation (NHS reference)",
        "title_ro": "Rotatie cervicala (referinta NHS)",
        "camera_en": "Camera: FRONT view, seated or standing",
        "camera_ro": "Camera: vedere FRONT, sezand sau in picioare",
        "cue_en": "Rep counter increments only inside the pain-free band.",
        "cue_ro": "Contorul de repetari creste doar in limitele fara durere.",
    },
    {
        "exercise_id": "cervical_lateral_flexion",
        "src": "lat_flex/evidence.mp4",
        "title_en": "Cervical lateral flexion (ear-to-shoulder)",
        "title_ro": "Flectie laterala cervicala (ureche-spre-umăr)",
        "camera_en": "Camera: FRONT view, head and both shoulders in frame",
        "camera_ro": "Camera: vedere FRONT, capul si ambele umeri in cadru",
        "cue_en": "cervical_tilt is the ear-to-shoulder angle: tilt slowly, keep shoulders level.",
        "cue_ro": "cervical_tilt este unghiul ureche-umăr: inclina lent, mentine umerii pe aceeasi linie.",
    },
    {
        "exercise_id": "shoulder_shrug",
        "src": "shrug/evidence.mp4",
        "title_en": "Shoulder shrug and release",
        "title_ro": "Ridicare si relaxare umeri",
        "camera_en": "Camera: SIDE view, profile with head and shoulders in frame",
        "camera_ro": "Camera: vedere SIDE, profil cu capul si umerii in cadru",
        "cue_en": "ear_sho_dist grows as you lift: raise toward ears, then fully relax down.",
        "cue_ro": "ear_sho_dist creste cand ridici: ridica spre urechi, apoi relaxeaza complet.",
    },
    {
        "exercise_id": "neck_isometric_hold",
        "src": "iso_hold/evidence.mp4",
        "title_en": "Isometric neck hold (hand resistance)",
        "title_ro": "Izometrie cervicala (rezistenta cu mana)",
        "camera_en": "Camera: SIDE view recommended (demo clip: front)",
        "camera_ro": "Camera: SIDE recomandata (clip demonstratie: front)",
        "cue_en": "Press into your hand WITHOUT moving the head: the overlay stays still. That is correct.",
        "cue_ro": "Apasa pe mana FARA sa misti capul: overlay-ul ramane nemișcat. Asta e corect.",
    },
]

def card(path, lines, colors, sizes):
    """Render a static intro/outro card with drawtext filters."""
    dts = []
    y = 90
    for text, col, size in zip(lines, colors, sizes):
        esc = text.replace(":", "\\:").replace("'", "\\'")
        font = FONT if size >= 34 else FONT_R
        dts.append(f"drawtext=fontfile={font}:text='{esc}':fontcolor={col}:fontsize={size}:x=(w-text_w)/2:y={y}")
        y += size + 26
    filt = ",".join(dts)
    subprocess.run([FF, "-y", "-f", "lavfi", "-i",
                    f"color=c=0x0B1220:s={W}x{H}:r={FPS}:d=6",
                    "-vf", filt, "-c:v", "libx264", "-pix_fmt", "yuv420p",
                    "-preset", "fast", "-crf", "26", path],
                   check=True, capture_output=True)

def main():
    os.makedirs("assets/guides", exist_ok=True)
    manifest = []
    for g in GUIDES:
        src = g["src"]
        if not os.path.exists(src):
            print(f"skip {g['exercise_id']}: {src} missing"); continue
        intro = "/tmp/guide_intro.mp4"; outro = "/tmp/guide_outro.mp4"
        card(intro,
             [g["title_en"], g["title_ro"], g["camera_en"], g["camera_ro"]],
             ["white", "0x38BDF8", "0xE5E7EB", "0x9CA3AF"], [44, 34, 30, 26])
        card(outro,
             ["Now do it yourself", "Acum executa tu", g["cue_en"], g["cue_ro"]],
             ["white", "0x38BDF8", "0xE5E7EB", "0x9CA3AF"], [44, 34, 28, 24])
        # normalize the evidence clip then concat
        norm = "/tmp/guide_norm.mp4"
        subprocess.run([FF, "-y", "-i", src, "-vf", f"scale={W}:{H}:force_original_aspect_ratio=decrease,pad={W}:{H}:(ow-iw)/2:(oh-ih)/2",
                        "-r", str(FPS), "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "fast", "-crf", "28",
                        "-an", norm], check=True, capture_output=True)
        lst = "/tmp/guide_list.txt"
        open(lst, "w").write("\n".join(f"file '{p}'" for p in [intro, norm, outro]))
        out = f"assets/guides/guide_{g['exercise_id']}.mp4"
        subprocess.run([FF, "-y", "-f", "concat", "-safe", "0", "-i", lst,
                        "-c", "copy", out], check=True, capture_output=True)
        size = os.path.getsize(out)
        manifest.append({"exercise_id": g["exercise_id"], "file": f"guide_{g['exercise_id']}.mp4",
                         "bytes": size})
        print(f"wrote {out} ({size/1e6:.1f} MB)")
    json.dump({"version": 1, "guides": manifest}, open("assets/guides/guides.json", "w"), indent=2)
    print("wrote assets/guides/guides.json")

if __name__ == "__main__":
    main()
