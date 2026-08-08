import csv
import json
import os
import subprocess
import sys
import time
import urllib.request
import wave
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

PORT = 8080

def load_pcm(path: str) -> bytes:
    if path.lower().endswith(".wav"):
        with wave.open(path, "rb") as wav:
            return wav.readframes(wav.getnframes())
    with open(path, "rb") as raw:
        return raw.read()

def wait_health():
    url = f"http://127.0.0.1:{PORT}/health"
    deadline = time.time() + 180
    last_response = None
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=5) as resp:
                body = json.loads(resp.read())
                if body.get("status") == "ok":
                    return body
        except Exception as e:
            last_response = e
        time.sleep(2)
    raise RuntimeError(f"Container didn't become healthy. Last error: {last_response}")

def get_image_id(container_name="viva-asr-bench"):
    try:
        out = subprocess.run(["docker", "inspect", container_name], capture_output=True, text=True, check=True)
        info = json.loads(out.stdout)[0]
        return info['Image']
    except Exception as e:
        return f"unknown ({e})"

def start_container(image, envs):
    subprocess.run(["docker", "stop", "viva-asr-bench"], capture_output=True)
    cmd = ["docker", "run", "-d", "--rm", "-p", f"{PORT}:{PORT}", "--name", "viva-asr-bench"]
    for k, v in envs.items():
        cmd.extend(["-e", f"{k}={v}"])
    cmd.append(image)
    subprocess.run(cmd, check=True, capture_output=True)
    
    health_data = wait_health()
    image_id = get_image_id()
    return health_data, image_id

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
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read()), None
    except Exception as e:
        return None, str(e)

def run_ablation():
    repo_root = Path(__file__).resolve().parents[3]
    
    # Ensure v14 output dir exists
    out_dir = repo_root / "evidence/asr/v14"
    out_dir.mkdir(parents=True, exist_ok=True)
    
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
        # INT8, no hotwords, uncapped
        ("INT8_uncapped", "viva-asr:tiny-v14", {
            "ASR_MODEL_NAME": "viva-asr:tiny",
            "ASR_COMPUTE_TYPE": "int8",
            "ASR_INITIAL_PROMPT": "",
            "ASR_HOTWORDS": "",
            "ASR_MAX_NEW_TOKENS": "0"
        }),
        # INT8, no hotwords, max_new_tokens=64
        ("INT8_max_tokens_64", "viva-asr:tiny-v14", {
            "ASR_MODEL_NAME": "viva-asr:tiny",
            "ASR_COMPUTE_TYPE": "int8",
            "ASR_INITIAL_PROMPT": "",
            "ASR_HOTWORDS": "",
            "ASR_MAX_NEW_TOKENS": "64"
        }),
        # INT8, hotwords, max_new_tokens=64
        ("INT8_hotwords_max_tokens_64", "viva-asr:tiny-v14", {
            "ASR_MODEL_NAME": "viva-asr:tiny",
            "ASR_COMPUTE_TYPE": "int8",
            "ASR_INITIAL_PROMPT": "",
            "ASR_HOTWORDS": "điều hòa, nhiệt độ, quạt gió, độ C, mở khóa, khóa cửa, âm lượng, chuyển bài, phát nhạc",
            "ASR_MAX_NEW_TOKENS": "64"
        }),
        # Float32, no hotwords, max_new_tokens=64
        ("Float32_max_tokens_64", "viva-asr:tiny-v14", { # Note: Re-using the image if float32 runs on the same image via env
            "ASR_MODEL_NAME": "viva-asr:tiny-float32",
            "ASR_COMPUTE_TYPE": "float32",
            "ASR_INITIAL_PROMPT": "",
            "ASR_HOTWORDS": "",
            "ASR_MAX_NEW_TOKENS": "64"
        })
    ]
    
    csv_file_path = out_dir / "ablation_results.csv"
    with open(csv_file_path, "w", encoding="utf-8", newline="") as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(["config", "image ID", "model", "compute type", "clip", "reference", "hypothesis", "confidence", "server_ms", "error"])
        
        for name, image, envs in configs:
            print(f"\n--- Running: {name} ---")
            try:
                health_data, image_id = start_container(image, envs)
                
                # Configuration Assertion
                cfg = health_data.get("config", {})
                assert health_data.get("model") == envs["ASR_MODEL_NAME"], f"Model mismatch: {health_data.get('model')} != {envs['ASR_MODEL_NAME']}"
                assert cfg.get("compute_type") == envs["ASR_COMPUTE_TYPE"], f"Compute type mismatch: {cfg.get('compute_type')} != {envs['ASR_COMPUTE_TYPE']}"
                expected_prompt = None if not envs.get("ASR_INITIAL_PROMPT") else envs["ASR_INITIAL_PROMPT"]
                assert cfg.get("initial_prompt") == expected_prompt, f"Initial prompt mismatch: {cfg.get('initial_prompt')} != {expected_prompt}"
                expected_hotwords = None if not envs.get("ASR_HOTWORDS") else envs["ASR_HOTWORDS"]
                assert cfg.get("hotwords") == expected_hotwords, f"Hotwords mismatch: {cfg.get('hotwords')} != {expected_hotwords}"
                assert cfg.get("max_new_tokens") == int(envs["ASR_MAX_NEW_TOKENS"]), f"Tokens mismatch: {cfg.get('max_new_tokens')} != {envs['ASR_MAX_NEW_TOKENS']}"
                
                print(f"Container configuration asserted successfully. Image ID: {image_id}")
            except Exception as e:
                print(f"Failed to start/assert container: {e}")
                continue
                
            for clip_name, path in clips:
                ref_text = prompts[clip_name]
                res, error = transcribe(str(path))
                
                if res:
                    writer.writerow([name, image_id, envs["ASR_MODEL_NAME"], envs["ASR_COMPUTE_TYPE"], clip_name, ref_text, res["text"], res["confidence"], res["server_ms"], ""])
                else:
                    writer.writerow([name, image_id, envs["ASR_MODEL_NAME"], envs["ASR_COMPUTE_TYPE"], clip_name, ref_text, "", 0.0, 0, error])
            
            stop_container()
            
    print(f"\nBenchmarking complete. Results written to {csv_file_path}")
    print("Please run the Kotlin IntentAccuracyScorer to generate the manifest and score the results.")

if __name__ == "__main__":
    run_ablation()
