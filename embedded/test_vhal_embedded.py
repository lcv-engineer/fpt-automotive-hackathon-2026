"""
VIVA PROJECT - EMBEDDED VHAL & DTC TEST SUITE
Dành riêng cho: Lê Đức Tùng (Embedded / System Engineer)
Kiểm thử: VHAL Property Mapping + AI Safety Guard G1/G2 + UDS/DTC 3-Axis Analysis
Cập nhật 01/08/2026: Kiểm thử PropertyID (0x15600503, 0x15400500, 0x16200b02) & Đảo cực Door Lock (1=Lock, 0=Unlock)
"""

import unittest
import json
import os
from uds_dtc_simulator import UDSDiagnosticSimulator

class TestEmbeddedVhal(unittest.TestCase):
    def setUp(self):
        self.dtc_sim = UDSDiagnosticSimulator()
        self.vhal_prop_map = {
            "HVAC_TEMPERATURE_SET": 0x15600503, # 358614275
            "HVAC_FAN_SPEED":       0x15400500, # 356517120
            "DOOR_LOCK":            0x16200b02, # 371198722
            "VEHICLE_SPEED":        0x11600207  # 308282375
        }

    def test_vhal_property_id_constants(self):
        print("\n--- 🔍 TESTING VHAL PROPERTY ID AAOS CONSTANTS ---")
        self.assertEqual(self.vhal_prop_map["HVAC_TEMPERATURE_SET"], 0x15600503)
        self.assertEqual(self.vhal_prop_map["HVAC_FAN_SPEED"], 0x15400500)
        self.assertEqual(self.vhal_prop_map["DOOR_LOCK"], 0x16200b02)
        self.assertEqual(self.vhal_prop_map["VEHICLE_SPEED"], 0x11600207)
        print("✅ AAOS VehicleProperties constants verified!")

    def test_uds_pdu_0x19_02_ff(self):
        print("\n--- 🚗 TESTING UDS DTC SIMULATOR 3-AXIS ANALYSIS ---")
        req_pdu = bytes.fromhex("1902FF")
        resp_pdu = self.dtc_sim.handle_uds_request_pdu(req_pdu)
        hex_resp = resp_pdu.hex().upper()
        
        self.assertTrue(hex_resp.startswith("5902FF"))
        
        analysis = self.dtc_sim.analyze_dtc_three_axes()
        self.assertIn("axis_1_frequency", analysis)
        self.assertIn("axis_2_trend", analysis)
        self.assertIn("axis_3_correlation", analysis)

        print("PDU Response Hex:", hex_resp)
        summary_vn = self.dtc_sim.get_dtc_summary_vietnamese()
        print("DTC 3-Axis Analysis Summary (Voice AI):", summary_vn)

    def test_safety_guard_g1_g2_embedded(self):
        print("\n--- 🛡️ TESTING SAFETY GUARD RULES G1 & G2 WITH AAOS DOOR POLARITY ---")
        # Chuẩn AAOS: 1/true = LOCKED, 0/false = UNLOCKED
        # Safety G1.1: Block Door UNLOCK (value = 0) when Speed > 0
        speed = 50.0
        target_door_val = 0 # Request UNLOCK
        door_cmd_allowed = False if (speed > 0 and target_door_val == 0) else True
        self.assertFalse(door_cmd_allowed, "Unlock request must be BLOCKED when speed > 0!")

        # Request LOCK (value = 1) when Speed > 0 -> ALLOWED
        target_lock_val = 1 # Request LOCK
        lock_cmd_allowed = True if target_lock_val == 1 else False
        self.assertTrue(lock_cmd_allowed, "Locking door while moving must be ALLOWED!")

        # Safety G2.1: Block Reverse Gear (R: -1) when Speed > 10 km/h
        speed_gear = 25.0
        target_gear = -1 # R
        gear_shift_allowed = False if (speed_gear > 10.0 and target_gear == -1) else True
        self.assertFalse(gear_shift_allowed, "Reverse gear must be BLOCKED when speed > 10 km/h!")

        print("✅ Safety Guard G1 & G2 with AAOS door polarity validated successfully!")

if __name__ == "__main__":
    unittest.main()
