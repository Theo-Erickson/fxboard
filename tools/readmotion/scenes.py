"""Writes FXboard's README recordings into readmotion.json: a hero and keyfx's effect gallery.

    python tools/readmotion/scenes.py

The recordings play keyfx's lab in showcase mode, from the keyfx/ subtree, so they show the same
effects with the same settings FXboard uses. See keyfx/tools/readmotion/showcase.py.
"""

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[1] / "keyfx" / "tools" / "readmotion"))
import showcase as s  # noqa: E402

ACCENT = "#C58AF9"


def hero() -> list[dict]:
    """A real message, typed and swiped with the effects on, then sent."""
    return s.scene(
        s.mark("hero"),
        s.setup("FXboard", "Effects while you type", defaults=True, text="",
                set_={"global": {"accent": ACCENT}, "float": {"mode": "rise"}, "enter": {"style": "check"}}),
        s.type_text("running late,"), s.record(0.3),
        s.setup("FXboard", "Swipe typing, with letter hop"),
        s.swipe("see"), s.record(0.7), s.swipe("you"), s.record(0.7), s.swipe("tonight"), s.record(1.2),
        s.setup("FXboard", "Backspace takes it back", set_={"del": {"tap": "fire"}}),
        s.tap("del", 0.3), s.record(0.9),
        s.swipe("later"), s.record(1.0),
        s.setup("FXboard", "Send it"),
        s.tap("enter", 0.3), s.record(1.4),
        # TODO(phone): end on FXboard's real Keyboard effects settings page, recorded on a phone.
        # See tools/readmotion/phone/TODO.md for what to record and how to add it here.
        s.mark("end"), s.record(0.1),
    )


if __name__ == "__main__":
    gallery = s.gallery()
    gallery.insert(0, {"eval": f"showcase.set({{global: {{accent: '{ACCENT}'}}}})"})
    s.write(HERE / "readmotion.json", {
        "hero": s.recording("../../keyfx", hero()),
        "gallery": s.recording("../../keyfx", gallery),
    })
