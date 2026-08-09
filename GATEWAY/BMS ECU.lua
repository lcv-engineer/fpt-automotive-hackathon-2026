-- bms.lua — Battery Management System (Powertrain-domain, EV).
--
-- Owns the energy signals: traction-battery SoC, pack voltage/current/temp,
-- and remaining range derived from SoC. Publishes onto the Powertrain CAN;
-- the Powertrain Gateway bridges to KUKSA.
--
-- Pins:
--   pins.can    — Powertrain CAN (DBC: examples/latest_demo/dbc/powertrain_can.dbc)
--   pins.sensor — "Battery Sensor" gpio-panel: SoC slider 0..100 (override)
--
-- SoC is gpio-driven (static until the slider moves); Range / voltage /
-- current / temperature are derived from SoC. No time-based simulation.

local can    = pins.can
local sensor = pins.sensor
local db     = can.db

local FULL_RANGE_KM = 450     -- range at 100% SoC

local soc = 82.0

local function publish_energy()
    db.PWT_BatteryStatus.SoC_pct:publish(soc)
    -- Pack electricals correlate loosely with SoC (demo sim).
    db.PWT_BatteryStatus.Voltage_V:publish(320 + soc * 0.6)   -- ~320..380 V
    db.PWT_BatteryStatus.Current_A:publish(-15)               -- mild steady discharge
    db.PWT_BatteryStatus.Temperature_degC:publish(28)
    -- Remaining range (metres) from SoC.
    db.PWT_Range.Range_m:publish(math.floor(soc / 100 * FULL_RANGE_KM * 1000))
end

-- ── SoC override slider ────────────────────────────────
sensor:on("SoC", function(v)
    soc = v or soc
    publish_energy()
    log(string.format("[bms] SoC override = %.1f %%", soc))
end)

-- ── Bootstrap (SoC static until the gpio slider moves) ──
publish_energy()

log("bms ready")
