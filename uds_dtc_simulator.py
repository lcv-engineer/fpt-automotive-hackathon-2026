# ===================================================================
# VIVA PROJECT - ADVANCED UDS / DTC DIAGNOSTIC SIMULATOR (ISO 14229 / ISO 15031)
# Ngôn ngữ: Python (Dùng cho CAN Bus Node / Container Node trên CarSky)
# Task T10: ISO-TP Service 0x19 0x02 FF + Phân nhóm P/C/B/U + Phân tích 3 Trục
# Tác giả: Lê Đức Tùng (VHAL Embedded Engineer - Team VIVA)
# ===================================================================

import json
import time
from typing import Dict, List, Any

# 1. CƠ SỞ DỮ LIỆU MÃ LỖI DTC CHUẨN ISO 15031 / SAE J2012 (P, C, B, U)
DTC_DATABASE: Dict[str, Dict[str, Any]] = {
    "P0301": {
        "code": "P0301",
        "category": "Powertrain (P)",
        "hex_code": "0x030113",
        "system_vn": "Động cơ / Truyền lực",
        "description_vn": "Bỏ lửa xy-lanh 1 (Cylinder 1 Misfire Detected)",
        "severity": "CRITICAL",
        "status": "ACTIVE", # Active (0x09), Pending (0x04), Stored (0x08)
        "status_byte": 0x09,
        "frequency": 14, # Số lần xuất hiện trong 100km gần nhất
        "trend": "ESCALATING", # ESCALATING, STABLE, INTERMITTENT
        "action_vn": "Cần kiểm tra bugi và cuộn đánh lửa (mobin) xy-lanh 1 ngay.",
        "correlated_with": ["P0171", "U0100"]
    },
    "P0171": {
        "code": "P0171",
        "category": "Powertrain (P)",
        "hex_code": "0x017100",
        "system_vn": "Hệ thống nhiên liệu",
        "description_vn": "Hỗn hợp hòa khí quá nghèo (System Too Lean - Bank 1)",
        "severity": "WARNING",
        "status": "PENDING",
        "status_byte": 0x04,
        "frequency": 6,
        "trend": "INTERMITTENT",
        "action_vn": "Vệ sinh cảm biến đường gió nạp MAF và kiểm tra rò rỉ khí nạp.",
        "correlated_with": ["P0301"]
    },
    "C0035": {
        "code": "C0035",
        "category": "Chassis (C)",
        "hex_code": "0x403514",
        "system_vn": "Khung gầm / Phanh ABS",
        "description_vn": "Lỗi cảm biến tốc độ bánh xe trước bên trái (Front Left ABS Sensor)",
        "severity": "WARNING",
        "status": "ACTIVE",
        "status_byte": 0x09,
        "frequency": 8,
        "trend": "STABLE",
        "action_vn": "Kiểm tra dây cáp và cảm biến ABS bánh trước trái.",
        "correlated_with": []
    },
    "B1200": {
        "code": "B1200",
        "category": "Body (B)",
        "hex_code": "0x920000",
        "system_vn": "Thân xe / Điện điều hòa",
        "description_vn": "Mạch điều khiển công tắc điều hòa không khí gặp sự cố",
        "severity": "MINOR",
        "status": "STORED",
        "status_byte": 0x08,
        "frequency": 2,
        "trend": "STABLE",
        "action_vn": "Kiểm tra cầu chì và cụm công tắc HVAC.",
        "correlated_with": []
    },
    "U0100": {
        "code": "U0100",
        "category": "Network (U)",
        "hex_code": "0xC10000",
        "system_vn": "Mạng truyền thông CAN Bus",
        "description_vn": "Mất kết nối truyền thông CAN với ECM/VCU (Lost Comm with ECM/VCU)",
        "severity": "CRITICAL",
        "status": "ACTIVE",
        "status_byte": 0x09,
        "frequency": 19,
        "trend": "ESCALATING",
        "action_vn": "Kiểm tra nguồn điện ECU và đường dây CAN High/Low.",
        "correlated_with": ["P0301"]
    }
}

class UDSDiagnosticSimulator:
    """
    Mô phỏng UDS Diagnostic Server (ISO 14229 Service 0x19: ReadDTCInformation)
    Hỗ trợ subfunction 0x02 (reportDTCByStatusMask) và Phân tích 3 Trục cho AI Agent.
    """
    def __init__(self):
        # Mặc định danh sách lỗi đang tồn tại trên xe
        self.active_dtc_codes = ["P0301", "C0035", "U0100"]
        print("[UDS SIMULATOR] Đã khởi tạo UDS Server (ISO 14229 Service 0x19 & 0x14)...")

    def handle_uds_request_pdu(self, pdu_bytes: bytes) -> bytes:
        """
        Xử lý UDS Request PDU thô (ISO-TP payload).
        Ví dụ Request ReadDTCByStatusMask: 0x19 0x02 0xFF
        Trả về Response PDU positive: 0x59 0x02 ...
        """
        if len(pdu_bytes) < 2 or pdu_bytes[0] != 0x19:
            # Negative Response 0x7F 0x19 0x11 (ServiceNotSupported)
            return bytes([0x7F, pdu_bytes[0] if len(pdu_bytes) > 0 else 0x00, 0x11])

        subfunction = pdu_bytes[1]
        status_mask = pdu_bytes[2] if len(pdu_bytes) > 2 else 0xFF

        if subfunction == 0x02: # reportDTCByStatusMask
            response = bytearray([0x59, 0x02, status_mask])
            for code in self.active_dtc_codes:
                if code in DTC_DATABASE:
                    dtc_info = DTC_DATABASE[code]
                    if (dtc_info["status_byte"] & status_mask) != 0:
                        # 3 bytes DTC High/Middle/Low + 1 byte Status
                        dtc_hex = int(dtc_info["hex_code"], 16)
                        response.append((dtc_hex >> 16) & 0xFF)
                        response.append((dtc_hex >> 8) & 0xFF)
                        response.append(dtc_hex & 0xFF)
                        response.append(dtc_info["status_byte"])
            return bytes(response)

        elif subfunction == 0x01: # reportNumberOfDTCByStatusMask
            count = len([c for c in self.active_dtc_codes if c in DTC_DATABASE])
            return bytes([0x59, 0x01, status_mask, 0x00, 0x00, count])

        # Negative Response: SubFunctionNotSupported
        return bytes([0x7F, 0x19, 0x12])

    def get_detailed_dtc_list(self, status_mask: int = 0xFF) -> List[Dict[str, Any]]:
        """
        Trả về danh sách đối tượng DTC chi tiết
        """
        dtc_list = []
        for code in self.active_dtc_codes:
            if code in DTC_DATABASE:
                info = DTC_DATABASE[code]
                if (info["status_byte"] & status_mask) != 0:
                    dtc_list.append(info)
        return dtc_list

    def analyze_dtc_three_axes(self) -> Dict[str, Any]:
        """
        PHÂN TÍCH CHẨN ĐOÁN 3 TRỤC (Task T10 Requirement):
        1. Trục 1: Tần suất xuất hiện (Frequency Analysis)
        2. Trục 2: Xu hướng diễn biến lỗi (Trend Analysis: Escalating / Stable / Intermittent)
        3. Trục 3: Tương quan nhân quả giữa các lỗi (Correlation Analysis)
        """
        current_dtcs = self.get_detailed_dtc_list()
        if not current_dtcs:
            return {
                "status": "HEALTHY",
                "summary_vn": "Hệ thống xe hoạt động hoàn hảo. Không ghi nhận lỗi kỹ thuật nào.",
                "analysis": {}
            }

        # 1. Trục Tần suất
        frequency_report = []
        for dtc in current_dtcs:
            frequency_report.append({
                "code": dtc["code"],
                "frequency": dtc["frequency"],
                "assessment": f"Lỗi {dtc['code']} xuất hiện {dtc['frequency']} lần trong chu kỳ vận hành."
            })

        # 2. Trục Xu hướng
        trend_report = {
            "escalating_codes": [d["code"] for d in current_dtcs if d["trend"] == "ESCALATING"],
            "intermittent_codes": [d["code"] for d in current_dtcs if d["trend"] == "INTERMITTENT"],
            "stable_codes": [d["code"] for d in current_dtcs if d["trend"] == "STABLE"]
        }

        # 3. Trục Tương quan
        correlation_clusters = []
        visited = set()
        for dtc in current_dtcs:
            code = dtc["code"]
            if code not in visited:
                cluster = [code]
                visited.add(code)
                for rel in dtc.get("correlated_with", []):
                    if rel in self.active_dtc_codes and rel not in visited:
                        cluster.append(rel)
                        visited.add(rel)
                if len(cluster) > 1:
                    correlation_clusters.append({
                        "root_cause_candidates": cluster,
                        "explanation": f"Cụm lỗi tương quan: {', '.join(cluster)} có khả năng xuất phát từ cùng sự cố nguồn điện/mạng truyền thông."
                    })

        return {
            "status": "FAULT_DETECTED",
            "total_faults": len(current_dtcs),
            "axis_1_frequency": frequency_report,
            "axis_2_trend": trend_report,
            "axis_3_correlation": correlation_clusters,
            "raw_dtcs": current_dtcs
        }

    def get_dtc_summary_vietnamese(self) -> str:
        """
        Tổng hợp phản hồi giọng nói Tiếng Việt tự nhiên cho AI Voice Agent
        (Giọng nói thân thiện, chi tiết, hướng dẫn xử lý)
        """
        dtcs = self.get_detailed_dtc_list()
        if not dtcs:
            return "Hệ thống xe hoàn toàn bình thường, không có mã lỗi nào cần chú ý."

        analysis = self.analyze_dtc_three_axes()
        lines = [f"Hiện tại xe đang ghi nhận {len(dtcs)} mã lỗi DTC:"]

        for idx, dtc in enumerate(dtcs, 1):
            category_name = dtc["category"]
            lines.append(f"{idx}. Mã {dtc['code']} thuộc hệ thống {dtc['system_vn']}: {dtc['description_vn']}.")

        # Bổ sung cảnh báo nâng cao từ phân tích tương quan & xu hướng
        escalating = analysis["axis_2_trend"]["escalating_codes"]
        if escalating:
            lines.append(f"Cảnh báo: Mã lỗi {', '.join(escalating)} đang có xu hướng gia tăng tần suất lặp lại.")

        if analysis["axis_3_correlation"]:
            lines.append("Phân tích AI ghi nhận sự tương quan giữa lỗi bỏ lửa động cơ và lỗi mất kết nối mạng CAN Bus.")

        # Lấy lời khuyên xử lý của lỗi nguy hiểm nhất
        critical_dtc = next((d for d in dtcs if d["severity"] == "CRITICAL"), dtcs[0])
        lines.append(f"Khuyên dùng: {critical_dtc['action_vn']}")

        return " ".join(lines)

    def inject_simulated_fault(self, dtc_code: str) -> bool:
        """Inject mã lỗi mô phỏng vào xe"""
        if dtc_code in DTC_DATABASE and dtc_code not in self.active_dtc_codes:
            self.active_dtc_codes.append(dtc_code)
            print(f"[UDS TEST BENCH] Đã inject thành công mã lỗi mô phỏng: {dtc_code}")
            return True
        return False

    def clear_simulated_faults(self) -> str:
        """Xóa mã lỗi UDS Service 0x14"""
        self.active_dtc_codes = []
        print("[UDS SERVICE 0x14] Đã xóa sạch mã lỗi chẩn đoán trên ECU.")
        return "Đã xóa toàn bộ mã lỗi trên xe."

# --- DEMO TEST BENCH ---
if __name__ == "__main__":
    sim = UDSDiagnosticSimulator()
    print("\n--- TEST UDS SERVICE 0x19 0x02 FF (RAW PDU PACKET) ---")
    request_pdu = bytes([0x19, 0x02, 0xFF])
    response_pdu = sim.handle_uds_request_pdu(request_pdu)
    print("Request PDU: ", request_pdu.hex())
    print("Response PDU:", response_pdu.hex())

    print("\n--- TEST SKILL #4: PHÂN TÍCH CHẨN ĐOÁN LỖI 3 TRỤC (TASK T10) ---")
    analysis = sim.analyze_dtc_three_axes()
    print(json.dumps(analysis, indent=2, ensure_ascii=False))

    print("\n--- TEST PHẢN HỒI GIỌNG NÓI TIẾNG VIỆT CHO AI AGENT ---")
    voice_text = sim.get_dtc_summary_vietnamese()
    print("AI Voice Response:", voice_text)
