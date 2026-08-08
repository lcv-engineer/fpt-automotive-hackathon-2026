import csv
import re
import glob

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

for path in glob.glob("*/v12-noise-levels.csv"):
    correct_by_level = {"clean": 0, "quiet": 0, "cabin": 0, "highway": 0}
    total_by_level = {"clean": 0, "quiet": 0, "cabin": 0, "highway": 0}
    
    with open(path, encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            ref = row['reference']
            hyp = row['hypothesis']
            level = row['level']
            
            ref_route = route(ref)
            hyp_route = route(hyp)
            
            total_by_level[level] += 1
            if ref_route == hyp_route:
                correct_by_level[level] += 1
    
    total_correct = sum(correct_by_level.values())
    total_samples = sum(total_by_level.values())
    print(f"Model: {path}")
    for lvl in ["clean", "quiet", "cabin", "highway"]:
        print(f"  {lvl}: {correct_by_level[lvl]} / {total_by_level[lvl]}")
    print(f"  TOTAL: {total_correct} / {total_samples} ({total_correct/total_samples*100:.2f}%)")
    print()
