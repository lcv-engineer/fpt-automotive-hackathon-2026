-- bcm_ecu.lua — door echo + TPMS sensor for the body bus.
--
-- Pins:
--   pins.can    — Body CAN (DBC: examples/latest_demo/dbc/body_can.dbc)
--   pins.sensor — TirePressure Sensor gpio-panel (pin ids match DBC
--                 signal names verbatim).

local can    = pins.can
local sensor = pins.sensor

local can_door_command  = can.db.DoorCommand
local can_door_status   = can.db.DoorStatus
local can_tire_pressure = can.db.TirePressure

-- ── Door lock echo ────────────────────────────────────

local function mirror_door(cmd_sig, status_sig, label)
    cmd_sig:on_change(function(v)
        status_sig:publish(v)
        log("Door " .. label .. " locked=" .. tostring(v))
    end)
end

mirror_door(can_door_command.Row1Driver_IsLocked,    can_door_status.Row1Driver_IsLocked,    "Row1Driver")
mirror_door(can_door_command.Row1Passenger_IsLocked, can_door_status.Row1Passenger_IsLocked, "Row1Passenger")
mirror_door(can_door_command.Row2Driver_IsLocked,    can_door_status.Row2Driver_IsLocked,    "Row2Driver")
mirror_door(can_door_command.Row2Passenger_IsLocked, can_door_status.Row2Passenger_IsLocked, "Row2Passenger")

-- ── TPMS slider → CAN ─────────────────────────────────

sensor:on("Row1Left_TirePressure", function(v)
    can_tire_pressure.Row1Left_TirePressure:publish(v)
    log("Tire Row1Left = " .. v .. " kPa")
end)

sensor:on("Row1Right_TirePressure", function(v)
    can_tire_pressure.Row1Right_TirePressure:publish(v)
    log("Tire Row1Right = " .. v .. " kPa")
end)

sensor:on("Row2Left_TirePressure", function(v)
    can_tire_pressure.Row2Left_TirePressure:publish(v)
    log("Tire Row2Left = " .. v .. " kPa")
end)

sensor:on("Row2Right_TirePressure", function(v)
    can_tire_pressure.Row2Right_TirePressure:publish(v)
    log("Tire Row2Right = " .. v .. " kPa")
end)

log("bcm_ecu ready")
