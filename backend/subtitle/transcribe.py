"""Transcribe a Mandarin voiceover, using a reference only for supported spellings."""

import argparse
import json
import re
import unicodedata
from difflib import SequenceMatcher
from functools import lru_cache
from pathlib import Path

from faster_whisper import WhisperModel
from pypinyin import Style, lazy_pinyin


LINE_BREAK = re.compile(r"[。！？!?；;][”’）]?$")
CITATION = re.compile(r"【资料\d+】")


def utf16_length(text):
    return len(text.encode("utf-16-le")) // 2


def recognize(source, model, length_ms):
    model = WhisperModel(model, device="cpu", compute_type="int8", cpu_threads=4)
    segments, _ = model.transcribe(
        source,
        language="zh",
        initial_prompt="这是一段简体中文普通话口播，请按录音原话转写。",
        word_timestamps=True,
        vad_filter=True,
        vad_parameters={"min_silence_duration_ms": 500},
        condition_on_previous_text=False,
    )
    cues = []
    for segment in segments:
        words = segment.words or ()
        if not words:
            append_cue(cues, round(segment.start * 1000), round(segment.end * 1000), segment.text, length_ms)
            continue
        start_ms = end_ms = 0
        text = ""
        for word in words:
            word_start_ms = round(word.start * 1000)
            word_end_ms = round(word.end * 1000)
            if text and (word_start_ms - end_ms > 600 or utf16_length(text + word.word) > 27
                         or word_end_ms - start_ms > 6000):
                append_cue(cues, start_ms, end_ms, text, length_ms)
                text = ""
            if not text:
                start_ms = word_start_ms
            text += word.word
            end_ms = word_end_ms
            if LINE_BREAK.search(text.strip()) and end_ms - start_ms >= 700:
                append_cue(cues, start_ms, end_ms, text, length_ms)
                text = ""
        append_cue(cues, start_ms, end_ms, text, length_ms)
    return cues


def append_cue(cues, start_ms, end_ms, text, length_ms):
    text = text.strip()
    if not text:
        return
    previous_end = cues[-1]["endMs"] if cues else 0
    first = max(previous_end, start_ms)
    last = min(length_ms, end_ms)
    if last > first:
        cues.append({"startMs": first, "endMs": last, "text": text})


@lru_cache(maxsize=4096)
def phonetic_key(char):
    if "\u3400" <= char <= "\u9fff":
        return "han:" + lazy_pinyin(char, style=Style.TONE3, neutral_tone_with_five=True)[0]
    return unicodedata.normalize("NFKC", char).casefold()


def align_spelling(cues, script):
    # Preserve the recognized timeline and sequence, including omissions and repetitions.
    reference = CITATION.sub("", " ".join(line for line in script.splitlines() if not line.lstrip().startswith("#")))
    target = [char for char in reference if char.isalnum()]
    bodies = [list(cue["text"]) for cue in cues]
    positions = [(row, column) for row, body in enumerate(bodies) for column, char in enumerate(body) if char.isalnum()]
    spoken = [bodies[row][column] for row, column in positions]
    if not target or not spoken:
        return
    match = SequenceMatcher(None, list(map(phonetic_key, spoken)), list(map(phonetic_key, target)), autojunk=False)
    for block in match.get_matching_blocks():
        original = spoken[block.a:block.a + block.size]
        written = target[block.b:block.b + block.size]
        for index, (heard, expected) in enumerate(zip(original, written)):
            if heard == expected or not phonetic_key(heard).startswith("han:"):
                continue
            left = max(0, index - 4)
            right = min(block.size, index + 5)
            anchors = sum(a == b for a, b in zip(original[left:right], written[left:right]))
            if anchors >= 3:
                row, column = positions[block.a + index]
                bodies[row][column] = expected
    for cue, body in zip(cues, bodies):
        cue["text"] = "".join(body)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("video")
    parser.add_argument("reference", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("model")
    parser.add_argument("duration_ms", type=int)
    args = parser.parse_args()
    cues = recognize(args.video, args.model, args.duration_ms)
    reference = args.reference.read_text(encoding="utf-8")
    if reference.strip():
        align_spelling(cues, reference)
    args.output.write_text(json.dumps(cues, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
