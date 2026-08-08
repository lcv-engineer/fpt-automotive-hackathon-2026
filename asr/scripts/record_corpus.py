#!/usr/bin/env python3
"""Interactive script to record the 20 benchmark prompts at 16kHz mono 16-bit PCM.

Requires: pip install sounddevice numpy

Usage:
    python asr/scripts/record_corpus.py
"""

from __future__ import annotations

import csv
import sys
import wave
from pathlib import Path

try:
    import numpy as np
    import sounddevice as sd
except ImportError:
    print("Thu vien 'sounddevice' hoac 'numpy' chua duoc cai dat.")
    print("Vui long cai dat: pip install sounddevice numpy")
    sys.exit(1)

SAMPLE_RATE = 16000
CHANNELS = 1
SAMPWIDTH = 2  # 16-bit PCM


def record_prompt(name: str, text: str, output_path: Path) -> bool:
    print("\n" + "=" * 60)
    print(f"File   : {name}.wav")
    print(f"Cau  : \"{text}\"")
    print("=" * 60)
    
    cmd = input("Nhan ENTER de bat dau thu (hoac nhap 's' de bo qua, 'q' de thoat): ").strip().lower()
    if cmd == 'q':
        return False
    if cmd == 's':
        print(f"-> Da bo qua {name}.wav")
        return True

    print("\n[REC] ĐANG THU ÂM... (Nhan ENTER de KET THUC thu âm)")
    
    recorded_frames = []
    
    def callback(indata, frames, time, status):
        if status:
            print(status, file=sys.stderr)
        recorded_frames.append(indata.copy())

    with sd.InputStream(samplerate=SAMPLE_RATE, channels=CHANNELS, dtype='int16', callback=callback):
        input()  # Wait for user to press ENTER to stop recording

    if not recorded_frames:
        print("Lỗi: Không ghi nhận được dữ liệu âm thanh.")
        return True

    audio_data = np.concatenate(recorded_frames, axis=0)
    
    # Save WAV file
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(output_path), "wb") as wav:
        wav.setnchannels(CHANNELS)
        wav.setsampwidth(SAMPWIDTH)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(audio_data.tobytes())

    duration = len(audio_data) / SAMPLE_RATE
    print(f"✓ Da luu {output_path.name} (Thoi luong: {duration:.2f}s)")
    return True


def main() -> int:
    prompts_file = Path("asr/scripts/corpus_prompts.tsv")
    output_dir = Path("evidence/asr/corpus-human/raw")

    if not prompts_file.exists():
        print(f"Lỗi: Không tìm thấy file prompts tại {prompts_file}")
        return 1

    prompts = []
    with open(prompts_file, encoding="utf-8") as h:
        for row in csv.DictReader(h, delimiter="\t"):
            name = (row.get("raw_name") or "").strip()
            text = (row.get("text_vi") or "").strip()
            if name and text:
                prompts.append((name, text))

    print(f"Tim thay {len(prompts)} cau trong {prompts_file}")
    print(f"Thu muc dau ra: {output_dir.resolve()}")
    print("Dinh dang thu: 16000 Hz, Mono, PCM 16-bit")

    for i, (name, text) in enumerate(prompts, 1):
        target_wav = output_dir / f"{name}.wav"
        status_str = "[Đã thu]" if target_wav.exists() else "[Chưa thu]"
        print(f"\n({i}/{len(prompts)}) {status_str} {name}: {text}")
        
        cont = record_prompt(name, text, target_wav)
        if not cont:
            print("\nDa dung qua trinh thu am.")
            break

    print("\nQuá trình hoàn tất! Chạy script kiểm tra:")
    print("python asr/scripts/check_corpus.py --dir evidence/asr/corpus-human/raw --prompts asr/scripts/corpus_prompts.tsv")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
