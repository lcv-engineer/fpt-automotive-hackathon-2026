import csv
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
import wave
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

PORT = 8080

def normalize_numbers(text):
    number_map = {
        "không": "0", "một": "1", "hai": "2", "ba": "3", "bốn": "4", "năm": "5",
        "sáu": "6", "bảy": "7", "tám": "8", "chín": "9", "mười": "10",
        "mười một": "11", "mười hai": "12", "mười ba": "13", "mười bốn": "14",
        "mười lăm": "15", "mười sáu": "16", "mười bảy": "17", "mười tám": "18",
        "mười chín": "19", "hai mươi": "20", "hai mươi một": "21", "hai mốt": "21",
        "hai một": "21", "hai mươi hai": "22", "hai hai": "22", "hai mươi ba": "23",
        "hai ba": "23", "hai mươi bốn": "24", "hai mươi tư": "24", "hai bốn": "24",
        "hai tư": "24", "hai mươi lăm": "25", "hai lăm": "25", "hai năm": "25",
        "hai mươi sáu": "26", "hai sáu": "26", "hai mươi bảy": "27", "hai bảy": "27",
        "hai mươi tám": "28", "hai tám": "28", "hai mươi chín": "29", "hai chín": "29",
        "ba mươi": "30", "ba mươi một": "31", "ba mốt": "31", "ba một": "31",
        "ba mươi hai": "32", "ba hai": "32"
    }
    sorted_keys = sorted(number_map.keys(), key=lambda x: len(x), reverse=True)
    for k in sorted_keys:
        text = re.sub(rf"(^|\s){k}(?=\s|$)", rf"\g<1>{number_map[k]}", text)
    return text

def normalize(raw):
    text = raw.lower()
    text = re.sub(r'[,.!?;:]', ' ', text)
    text = re.sub(r'\s+', ' ', text)
    text = text.strip()
    return normalize_numbers(text)

def route(text):
    norm = normalize(text)
    if re.search(r'^(?:siri|alexa|hey google)\s+ơi?(?:\s+|$)', norm): return "Unsupported"
    command = re.sub(r'^(?:viva|vivi)\s+ơi(?:\s+|$)', '', norm).strip()
    if not command: return "NeedsClarification"
    
    removed = [
        r'\b(?:bật|tắt)\s+(?:điều hòa|ac)\b',
        r'đặt\s+âm lượng',
        r'\b(?:bài trước|quay lại bài trước)\b',
        r'\b(?:dtc|mã lỗi|xe có lỗi)\b'
    ]
    if any(re.search(p, command) for p in removed): return "Unsupported"
    
    if "lạnh quá" in command: return "NeedsClarification"
    if "nóng quá" in command: return "NeedsClarification"
    
    def is_temp(cmd):
        if "nhiệt độ" in cmd: return True
        if "điều hòa" not in cmd: return False
        if re.search(r'(\d{1,2})', cmd): return True
        return any(c in cmd for c in ["đặt", "hạ", "tăng", "giảm", "xuống", "lên", "độ"])
        
    if is_temp(command):
        m = re.search(r'(\d{1,2})', command)
        if not m: return "NeedsClarification"
        val = int(m.group(1))
        if val < 16 or val > 32: return "NeedsClarification"
        return f"hvac_set_temp_{float(val)}"
        
    if "quạt" in command:
        m = re.search(r'(\d{1,2})', command)
        if not m: return "NeedsClarification"
        level = int(m.group(1))
        if level < 0 or level > 5: return "NeedsClarification"
        return f"hvac_set_fan_{level}"
        
    if "mở cửa" in command or "mở khóa cửa" in command: return "door_lock_false"
    if "khóa cửa" in command: return "door_lock_true"
    if "tăng âm lượng" in command: return "volume_adjust_1"
    if "giảm âm lượng" in command: return "volume_adjust_-1"
    if "dừng nhạc" in command or "tạm dừng nhạc" in command: return "media_pause"
    if "chuyển bài" in command or "bài tiếp theo" in command: return "media_next"
    
    if command.startswith("phát nhạc") or command.startswith("phát playlist"):
        q = command[len("phát "):].strip()
        if q == "nhạc": q = ""
        return f"media_play_{q}" if q else "media_play"
        
    if "chặng tiếp theo" in command or "điểm dừng tiếp theo" in command: return "delivery_next_stop"
    
    status_cues = ["thế nào", "trạng thái", "đến đâu"]
    if "đơn" in command and any(c in command for c in status_cues):
        m = re.search(r'\b([a-z]\d{1,6})\b', command)
        order_id = m.group(1).upper() if m else ""
        return f"delivery_order_status_{order_id}" if order_id else "delivery_order_status"
        
    if "xác nhận" in command and "giao" in command:
        m = re.search(r'\b([a-z]\d{1,6})\b', command)
        order_id = m.group(1).upper() if m else ""
        return f"delivery_confirm_{order_id}" if order_id else "delivery_confirm"
        
    return "Unsupported"

def load_pcm(path: str) -> bytes:
    if path.lower().endswith(".wav"):
        with wave.open(path, "rb") as wav:
            return wav.readframes(wav.getnframes())
    with open(path, "rb") as raw:
        return raw.read()

def wait_health():
    url = f"http://127.0.0.1:{PORT}/health"
    deadline = time.time() + 180
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=5) as resp:
                body = json.loads(resp.read())
                if body.get("status") == "ok":
                    return True
        except:
            pass
        time.sleep(2)
    return False

def start_container(image, envs):
    subprocess.run(["docker", "stop", "viva-asr-bench"], capture_output=True)
    cmd = ["docker", "run", "-d", "--rm", "-p", f"{PORT}:{PORT}", "--name", "viva-asr-bench"]
    for k, v in envs.items():
        cmd.extend(["-e", f"{k}={v}"])
    cmd.append(image)
    subprocess.run(cmd, check=True, capture_output=True)
    if not wait_health():
        raise RuntimeError("Container didn't become healthy")

def stop_container():
    subprocess.run(["docker", "stop", "viva-asr-bench"], capture_output=True)

def transcribe(audio_path):
    pcm = load_pcm(audio_path)
    req = urllib.request.Request(
        f"http://127.0.0.1:{PORT}/asr",
        data=pcm,
        method="POST",
        headers={
            "Content-Type": "application/octet-stream",
            "X-Sample-Rate": "16000",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())

def run_ablation():
    repo_root = Path(__file__).resolve().parents[3]
    
    # Load prompts mapping
    prompts = {}
    with open(repo_root / "asr/scripts/corpus_prompts.tsv", encoding="utf-8") as f:
        for row in csv.DictReader(f, delimiter='\t'):
            prompts[row["raw_name"]] = row["text_vi"]
            
    # Load clean clips
    clips = []
    with open(repo_root / "evidence/asr/corpus-human/corpus-index.csv", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row["level"] == "clean":
                clips.append((row["clip"], repo_root / row["path"]))
                
    configs = [
        ("INT8 + nobias", "viva-asr:tiny", {"ASR_INITIAL_PROMPT": ""}),
        ("INT8 + hotwords", "viva-asr:tiny", {"ASR_INITIAL_PROMPT": "", "ASR_HOTWORDS": "điều hòa, nhiệt độ, quạt gió, độ C, mở khóa, khóa cửa, âm lượng, chuyển bài, phát nhạc"}),
        ("INT8 + max_tokens", "viva-asr:tiny", {"ASR_INITIAL_PROMPT": "", "ASR_MAX_NEW_TOKENS": "64"}),
        ("Float32 + nobias", "viva-asr:tiny-float32", {"ASR_INITIAL_PROMPT": ""})
    ]
    
    for name, image, envs in configs:
        print(f"\n--- Running: {name} ---")
        try:
            start_container(image, envs)
        except Exception as e:
            print(f"Failed to start container: {e}")
            continue
            
        correct = 0
        total = 0
        for clip_name, path in clips:
            ref_text = prompts[clip_name]
            try:
                res = transcribe(str(path))
                hyp_text = res["text"]
                if route(ref_text) == route(hyp_text):
                    correct += 1
                else:
                    print(f"[{clip_name}] REF: {ref_text} ({route(ref_text)}) | HYP: {hyp_text} ({route(hyp_text)})")
            except Exception as e:
                print(f"Error on {clip_name}: {e}")
            total += 1
        
        print(f"Accuracy: {correct} / {total} ({(correct/total)*100:.1f}%)")
        stop_container()

if __name__ == "__main__":
    run_ablation()
