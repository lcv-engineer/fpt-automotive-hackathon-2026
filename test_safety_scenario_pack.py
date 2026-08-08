"""
VIVA PROJECT - EMBEDDED SAFETY SCENARIO PACK & ABLATION A1 BENCHMARK RUNNER
Mục đích: 
- Task T8 & T9: Bộ kiểm thử an toàn tự động ≥8 kịch bản Pass/Fail (Tiêu chí Testability)
- Task N4b: Ablation Study A1 — So sánh minh chứng khi BẬT vs TẮT SafetyGuard (Barem Team-owned 25đ)
Tác giả: Lê Đức Tùng (Embedded / System Engineer - Team VIVA)
"""

import unittest
import csv
import os
import time

class SafetyGuardSimulator:
    def __init__(self, is_enabled=True):
        self.is_enabled = is_enabled
        # Property ID constants (AAOS VehicleProperties.kt)
        self.HVAC_TEMPERATURE_SET = 0x15600503
        self.HVAC_FAN_SPEED       = 0x15400500
        self.DOOR_LOCK            = 0x16200b02
        self.VEHICLE_SPEED        = 0x11600207
        self.AUDIO_VOLUME         = 0x11400901
        self.GEAR_SELECTION       = 0x11400400

    def check_safety(self, prop_id, area_id, value, current_speed_kph):
        if not self.is_enabled:
            return {"allowed": True, "rule_id": "NONE", "reason": "ABLATION_A1_DISABLED"}

        if prop_id == self.DOOR_LOCK:
            # 1/true = LOCKED, 0/false = UNLOCKED
            is_unlock = (value == 0 or value is False or str(value).lower() in ["0", "false", "unlock"])
            if is_unlock and current_speed_kph > 0.0:
                return {
                    "allowed": False,
                    "rule_id": "G1.1",
                    "reason": f"CẢNH BÁO AN TOÀN (G1.1): Xe đang di chuyển ở {current_speed_kph} km/h. TỪ CHỐI MỞ CỬA!"
                }

        elif prop_id == self.HVAC_TEMPERATURE_SET:
            temp = float(value)
            if temp < 16.0 or temp > 32.0:
                return {
                    "allowed": False,
                    "rule_id": "G1.2",
                    "reason": f"CẢNH BÁO AN TOÀN (G1.2): Nhiệt độ ({temp}°C) nằm ngoài dải an toàn (16.0°C - 32.0°C)."
                }

        elif prop_id == self.HVAC_FAN_SPEED:
            fan = int(value)
            if fan < 0 or fan > 5:
                return {
                    "allowed": False,
                    "rule_id": "G1.3",
                    "reason": f"CẢNH BÁO AN TOÀN (G1.3): Quạt gió ({fan}) nằm ngoài dải cho phép (0 - 5)."
                }

        elif prop_id == self.AUDIO_VOLUME:
            vol = int(value)
            if vol < 0 or vol > 100:
                return {
                    "allowed": False,
                    "rule_id": "G1.4",
                    "reason": f"CẢNH BÁO AN TOÀN (G1.4): Âm lượng ({vol}) vượt dải cho phép (0 - 100)."
                }

        elif prop_id == self.GEAR_SELECTION:
            gear = int(value)
            if (gear == -1 or gear == 126) and current_speed_kph > 10.0:
                gear_name = "R" if gear == -1 else "P"
                return {
                    "allowed": False,
                    "rule_id": "G2.1",
                    "reason": f"CẢNH BÁO AN TOÀN (G2.1): Không thể cài số {gear_name} khi xe chạy {current_speed_kph} km/h!"
                }

        return {"allowed": True, "rule_id": "PASS", "reason": "ALLOWED"}

class TestSafetyScenarioPack(unittest.TestCase):
    def setUp(self):
        self.guard = SafetyGuardSimulator(is_enabled=True)
        self.scenarios = [
            {
                "id": "S1",
                "name": "Mở cửa xe khi xe chạy 60 km/h",
                "prop": 0x16200b02, "area": 1, "val": 0, "speed": 60.0,
                "expected_allowed": False, "expected_rule": "G1.1"
            },
            {
                "id": "S2",
                "name": "Khóa cửa xe khi xe chạy 60 km/h",
                "prop": 0x16200b02, "area": 1, "val": 1, "speed": 60.0,
                "expected_allowed": True, "expected_rule": "PASS"
            },
            {
                "id": "S3",
                "name": "Hạ nhiệt độ xuống 14.0°C (Dưới dải an toàn)",
                "prop": 0x15600503, "area": 0, "val": 14.0, "speed": 0.0,
                "expected_allowed": False, "expected_rule": "G1.2"
            },
            {
                "id": "S4",
                "name": "Chỉnh nhiệt độ xuống 22.0°C (Trong dải an toàn)",
                "prop": 0x15600503, "area": 0, "val": 22.0, "speed": 0.0,
                "expected_allowed": True, "expected_rule": "PASS"
            },
            {
                "id": "S5",
                "name": "Bật quạt gió mức 8 (Vượt ngưỡng max 5)",
                "prop": 0x15400500, "area": 0, "val": 8, "speed": 0.0,
                "expected_allowed": False, "expected_rule": "G1.3"
            },
            {
                "id": "S6",
                "name": "Chỉnh âm lượng 150 (Vượt ngưỡng max 100)",
                "prop": 0x11400901, "area": 0, "val": 150, "speed": 0.0,
                "expected_allowed": False, "expected_rule": "G1.4"
            },
            {
                "id": "S7",
                "name": "Cài số Lùi (R = -1) khi xe đang di chuyển 45 km/h",
                "prop": 0x11400400, "area": 0, "val": -1, "speed": 45.0,
                "expected_allowed": False, "expected_rule": "G2.1"
            },
            {
                "id": "S8",
                "name": "Cài số Lùi (R = -1) khi xe dừng hẳn (0 km/h)",
                "prop": 0x11400400, "area": 0, "val": -1, "speed": 0.0,
                "expected_allowed": True, "expected_rule": "PASS"
            }
        ]

    def test_run_safety_scenario_pack(self):
        print("\n========================================================")
        print("🛡️ RUNNING AUTOMATED SAFETY SCENARIO PACK (TASK T8 & T9)")
        print("========================================================")

        results_report = []
        for s in self.scenarios:
            res = self.guard.check_safety(s["prop"], s["area"], s["val"], s["speed"])
            pass_status = (res["allowed"] == s["expected_allowed"]) and (res["rule_id"] == s["expected_rule"])
            self.assertTrue(pass_status, f"Kịch bản {s['id']} thất bại!")

            status_str = "PASSED (BLOCKED CORRECTLY)" if not res["allowed"] else "PASSED (ALLOWED CORRECTLY)"
            print(f"[{s['id']}] {s['name']:<50} | Speed: {s['speed']:>4} km/h -> {status_str} | Rule: {res['rule_id']}")

            results_report.append({
                "Scenario_ID": s["id"],
                "Scenario_Name": s["name"],
                "Speed_Kph": s["speed"],
                "Property_ID": f"0x{s['prop']:X}",
                "Input_Value": s["val"],
                "Is_Allowed": res["allowed"],
                "Rule_Triggered": res["rule_id"],
                "Reason": res["reason"]
            })

        # Export CSV report for Evidence with UTF-8 BOM for Microsoft Excel compatibility
        report_path = "viva_safety_scenario_report.csv"
        with open(report_path, "w", newline="", encoding="utf-8-sig") as f:
            writer = csv.DictWriter(f, fieldnames=results_report[0].keys())
            writer.writeheader()
            writer.writerows(results_report)
        print(f"\n✅ Đã xuất báo cáo kịch bản an toàn ra file: {report_path}")

    def test_run_ablation_study_a1(self):
        print("\n========================================================")
        print("🧪 RUNNING ABLATION STUDY A1 (TASK N4b: SAFETYGUARD ON vs OFF)")
        print("========================================================")

        guard_on = SafetyGuardSimulator(is_enabled=True)
        guard_off = SafetyGuardSimulator(is_enabled=False)

        ablation_rows = []
        unsafe_scenarios = [s for s in self.scenarios if not s["expected_allowed"]]

        for s in unsafe_scenarios:
            res_on = guard_on.check_safety(s["prop"], s["area"], s["val"], s["speed"])
            res_off = guard_off.check_safety(s["prop"], s["area"], s["val"], s["speed"])

            print(f"[ABLATION A1] {s['name']}")
            print(f"   ├─ With SafetyGuard (NORMAL)   : Allowed={res_on['allowed']} (Blocked by {res_on['rule_id']})")
            print(f"   └─ Without SafetyGuard (ABLATION): Allowed={res_off['allowed']} (⚠️ DANGEROUS EXECUTION ALLOWED!)")

            ablation_rows.append({
                "Scenario_ID": s["id"],
                "Scenario_Name": s["name"],
                "Speed_Kph": s["speed"],
                "Guard_ON_Result": "BLOCKED" if not res_on["allowed"] else "ALLOWED",
                "Guard_OFF_Result": "UNSAFE_ALLOWED" if res_off["allowed"] else "BLOCKED",
                "Impact_Claim": "SafetyGuard successfully prevented hazard" if not res_on["allowed"] and res_off["allowed"] else "N/A"
            })

        ablation_file = "viva_ablation_a1_report.csv"
        with open(ablation_file, "w", newline="", encoding="utf-8-sig") as f:
            writer = csv.DictWriter(f, fieldnames=ablation_rows[0].keys())
            writer.writeheader()
            writer.writerows(ablation_rows)
        print(f"\n✅ Đã xuất bằng chứng Ablation A1 ra file: {ablation_file}")

if __name__ == "__main__":
    unittest.main()
