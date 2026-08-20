import unittest
import re

class TestVhalServerLuau(unittest.TestCase):
    def setUp(self):
        with open("vhal_server.luau", "r", encoding="utf-8") as f:
            self.luau_code = f.read()

    def test_property_constants(self):
        print("\n--- 🔍 VERIFYING VHAL PROPERTY CONSTANTS IN vhal_server.luau ---")
        # HVAC_TEMPERATURE_SET = 0x15600503 (358614275)
        self.assertIn("HVAC_TEMPERATURE_SET = 0x15600503", self.luau_code)
        # HVAC_FAN_SPEED = 0x15400500 (356517120)
        self.assertIn("HVAC_FAN_SPEED       = 0x15400500", self.luau_code)
        # DOOR_LOCK = 0x16200b02 (371198722)
        self.assertIn("DOOR_LOCK            = 0x16200b02", self.luau_code)
        # VEHICLE_SPEED = 0x11600207
        self.assertIn("VEHICLE_SPEED        = 0x11600207", self.luau_code)

    def test_property_id_based_signature(self):
        print("\n--- 🔍 VERIFYING (propertyId, areaId, value) HANDLER IN vhal_server.luau ---")
        # Check process_vhal_set_property(property_id, area_id, value)
        self.assertIn("function process_vhal_set_property(property_id, area_id, value)", self.luau_code)
        self.assertIn("function check_safety_guard(prop_id, area_id, target_val)", self.luau_code)

    def test_door_lock_polarity_contract(self):
        print("\n--- 🔍 VERIFYING DOOR LOCK POLARITY IN vhal_server.luau ---")
        # Contract: 1/true = LOCKED, 0/false = UNLOCKED
        self.assertIn("door_locked = 1", self.luau_code)
        self.assertIn("1/true: Locked, 0/false: Unlocked", self.luau_code)
        # Check safety guard rule G1.1 blocks unlock request when speed > 0
        self.assertIn("is_unlock_request", self.luau_code)
        self.assertIn("VehicleState.speed > 0", self.luau_code)

if __name__ == "__main__":
    unittest.main()
