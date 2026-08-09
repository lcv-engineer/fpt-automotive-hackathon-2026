-- powertrain_gateway.lua — Powertrain CAN → KUKSA bridge (read-only).
--
-- Mirrors Powertrain CAN status frames onto KUKSA Current values. EV framing:
-- motor speed maps to Powertrain.ElectricMotor.Speed (no combustion engine).
--
-- Cross-domain note: powertrain signals reach other domains ONLY via KUKSA.
-- (The Body Gateway subscribes Vehicle.Speed and re-emits it on the Body CAN
-- for the SeatBelt vECU — no CAN-to-CAN bridge here.)
--
-- Pins:
--   pins.kuksa — shared Vehicle KUKSA databroker
--   pins.can   — Powertrain CAN (DBC: examples/latest_demo/dbc/powertrain_can.dbc)

local kuksa = pins.kuksa
local can   = pins.can
local db    = can.db

local Vehicle = kuksa.vss.Vehicle
local pt      = Vehicle.Powertrain
local motor   = pt.ElectricMotor
local battery = pt.TractionBattery
local trans   = pt.Transmission

-- CAN PerformanceMode uint enum → VSS string enum.
local PERF_MODE = { [0] = "NORMAL", [1] = "SPORT", [2] = "ECONOMY", [3] = "SNOW", [4] = "RAIN" }

-- ── Motion ─────────────────────────────────────────────
db.PWT_VehicleSpeed.Speed_kph:on_change(function(v)
    Vehicle.Speed:publish(v or 0)
    log(string.format("[pgw] can→vss Speed = %.1f km/h", v or 0))
end)

db.PWT_MotorSpeed.Speed_rpm:on_change(function(v)
    local rpm = math.floor((v or 0) + 0.5)
    motor.Speed:publish(rpm)
    log(string.format("[pgw] can→vss ElectricMotor.Speed = %d rpm", rpm))
end)

db.PWT_Odometer.TraveledDistance_m:on_change(function(v)
    Vehicle.TraveledDistance:publish(math.floor((v or 0) + 0.5))
end)

-- ── Energy ─────────────────────────────────────────────
db.PWT_BatteryStatus.SoC_pct:on_change(function(v)
    battery.StateOfCharge.Current:publish(v or 0)
    log(string.format("[pgw] can→vss SoC = %.1f %%", v or 0))
end)
db.PWT_BatteryStatus.Voltage_V:on_change(function(v)        battery.CurrentVoltage:publish(v or 0) end)
db.PWT_BatteryStatus.Current_A:on_change(function(v)        battery.CurrentCurrent:publish(v or 0) end)
db.PWT_BatteryStatus.Temperature_degC:on_change(function(v) battery.Temperature.Average:publish(v or 0) end)

db.PWT_Range.Range_m:on_change(function(v)
    pt.Range:publish(math.floor((v or 0) + 0.5))
    log(string.format("[pgw] can→vss Powertrain.Range = %d m", math.floor((v or 0) + 0.5)))
end)

-- ── Drivetrain (gear + drive mode) ─────────────────────
db.PWT_DrivetrainStatus.SelectedGear:on_change(function(v)
    -- VSS-native PRNDL int8 — identity passthrough.
    trans.SelectedGear:publish(math.floor(v or 0))
    log(string.format("[pgw] can→vss SelectedGear = %d", math.floor(v or 0)))
end)

db.PWT_DrivetrainStatus.PerformanceMode:on_change(function(v)
    local s = PERF_MODE[math.floor(v or 0)]
    if s ~= nil then
        trans.PerformanceMode:publish(s)
        log("[pgw] can→vss PerformanceMode = " .. s)
    end
end)

log("powertrain_gateway ready")
