-- climate_ecu.lua — virtual HVAC ECU. Echoes HvacCommand into HvacStatus.
--
-- Pin:
--   pins.can — Body CAN (DBC: examples/latest_demo/dbc/body_can.dbc).

local can = pins.can

local can_hvac_command = can.db.HvacCommand
local can_hvac_status  = can.db.HvacStatus

local function mirror(cmd_sig, status_sig, label)
    cmd_sig:on_change(function(v)
        status_sig:publish(v)
        log("[hvac-ecu] " .. label .. " = " .. tostring(v))
    end)
end

mirror(can_hvac_command.Driver_Temperature,       can_hvac_status.Driver_Temperature,       "Driver_Temperature")
mirror(can_hvac_command.Passenger_Temperature,    can_hvac_status.Passenger_Temperature,    "Passenger_Temperature")
mirror(can_hvac_command.Driver_FanSpeed,          can_hvac_status.Driver_FanSpeed,          "Driver_FanSpeed")
mirror(can_hvac_command.Driver_AirDistribution,   can_hvac_status.Driver_AirDistribution,   "Driver_AirDistribution")
mirror(can_hvac_command.IsAirConditioningActive,  can_hvac_status.IsAirConditioningActive,  "IsAirConditioningActive")
mirror(can_hvac_command.IsRecirculationActive,    can_hvac_status.IsRecirculationActive,    "IsRecirculationActive")
mirror(can_hvac_command.IsAutoPowerOptimize,      can_hvac_status.IsAutoPowerOptimize,      "IsAutoPowerOptimize")
mirror(can_hvac_command.IsFrontDefrosterActive,   can_hvac_status.IsFrontDefrosterActive,   "IsFrontDefrosterActive")
mirror(can_hvac_command.IsRearDefrosterActive,    can_hvac_status.IsRearDefrosterActive,    "IsRearDefrosterActive")

log("climate_ecu ready")
