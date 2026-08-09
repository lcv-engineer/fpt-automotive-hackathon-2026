-- body_gateway.lua — body-domain CAN ↔ KUKSA bridge.
--
-- Translates KUKSA actuator writes (HVAC fields, door locks) into CAN
-- Command frames, and mirrors CAN Status frames back to KUKSA Current.
--
-- Pins:
--   pins.kuksa — shared Vehicle KUKSA databroker
--   pins.can   — Body CAN (DBC: examples/latest_demo/dbc/body_can.dbc)

local kuksa = pins.kuksa
local can   = pins.can

local can_hvac_command  = can.db.HvacCommand
local can_hvac_status   = can.db.HvacStatus
local can_door_command  = can.db.DoorCommand
local can_door_status   = can.db.DoorStatus
local can_tire_pressure = can.db.TirePressure
local can_power_state   = can.db.PowerState
local can_seatbelt      = can.db.VCU_TX_SEATBELT
local can_pwt_speed     = can.db.PWT_VehicleSpeed

local Vehicle      = kuksa.vss.Vehicle
local vss_hvac     = Vehicle.Cabin.HVAC
local vss_hvac_d   = Vehicle.Cabin.HVAC.Station.Row1.Driver
local vss_hvac_p   = Vehicle.Cabin.HVAC.Station.Row1.Passenger
local vss_door_r1d = Vehicle.Cabin.Door.Row1.DriverSide
local vss_door_r1p = Vehicle.Cabin.Door.Row1.PassengerSide
local vss_door_r2d = Vehicle.Cabin.Door.Row2.DriverSide
local vss_door_r2p = Vehicle.Cabin.Door.Row2.PassengerSide
local vss_tire_r1l = Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire
local vss_tire_r1r = Vehicle.Chassis.Axle.Row1.Wheel.Right.Tire
local vss_tire_r2l = Vehicle.Chassis.Axle.Row2.Wheel.Left.Tire
local vss_tire_r2r = Vehicle.Chassis.Axle.Row2.Wheel.Right.Tire
local vss_seat_r1d = Vehicle.Cabin.Seat.Row1.DriverSide

-- ── Scale / enum conversions ──────────────────────────

-- FanSpeed: DBC 0..5 level ↔ VSS 0..100 percent.
local FAN_LEVEL_MAX = 5
local function fan_vss_to_can(pct)
    if pct == nil or pct <= 0 then return 0 end
    if pct >= 100 then return FAN_LEVEL_MAX end
    return math.floor(pct / 100 * FAN_LEVEL_MAX + 0.5)
end
local function fan_can_to_vss(level)
    if level == nil or level <= 0 then return 0 end
    if level >= FAN_LEVEL_MAX then return 100 end
    return math.floor(level / FAN_LEVEL_MAX * 100 + 0.5)
end

local AIR_CAN_TO_VSS = { [0] = "UP", [1] = "MIDDLE", [2] = "DOWN" }
local AIR_VSS_TO_CAN = { UP = 0, MIDDLE = 1, DOWN = 2 }

local POWER_STATE = {
    [0] = "UNDEFINED", [1] = "LOCK", [2] = "OFF",
    [3] = "ACC",       [4] = "ON",   [5] = "START",
}

-- SeatBelt vECU FSM: 0=INIT, 1=READY, 2=NOT_READY_1, 3=NOT_READY_2.
-- READY ⇒ belt buckled. INIT ⇒ seat empty (weight ≤ 10); every other state
-- requires weight > 10, so anything but INIT means the seat is occupied.
local SEATBELT_READY = 1
local SEATBELT_INIT  = 0

local current_vehicle_speed = 0.0

-- ── KUKSA → CAN ───────────────────────────────────────

vss_hvac_d.Temperature:on_actuate(function(v)
    if v and (v < 16.0 or v > 32.0) then
        log(string.format("[SAFETY GUARD G1.2 BLOCKED] Temperature %.1f degC out of safe range (16-32 degC)", v))
        return
    end
    can_hvac_command.Driver_Temperature:publish(v)
    log(string.format("[bgw] vss→can Driver_Temperature = %s degC", tostring(v)))
end)

vss_hvac_p.Temperature:on_actuate(function(v)
    if v and (v < 16.0 or v > 32.0) then
        log(string.format("[SAFETY GUARD G1.2 BLOCKED] Temperature %.1f degC out of safe range (16-32 degC)", v))
        return
    end
    can_hvac_command.Passenger_Temperature:publish(v)
    log(string.format("[bgw] vss→can Passenger_Temperature = %s degC", tostring(v)))
end)

vss_hvac_d.FanSpeed:on_actuate(function(v)
    local lvl = fan_vss_to_can(v)
    if lvl < 0 or lvl > 5 then
        log(string.format("[SAFETY GUARD G1.3 BLOCKED] Fan speed level %d out of range (0-5)", lvl))
        return
    end
    can_hvac_command.Driver_FanSpeed:publish(lvl)
    log(string.format("[bgw] vss→can Driver_FanSpeed = %d (vss %s%%)", lvl, tostring(v)))
end)

vss_hvac_d.AirDistribution:on_actuate(function(v)
    local raw = AIR_VSS_TO_CAN[v]
    if raw == nil then return end
    can_hvac_command.Driver_AirDistribution:publish(raw)
    log(string.format("[bgw] vss→can Driver_AirDistribution = %d (%s)", raw, tostring(v)))
end)

local function bind_bool_actuate(vss_sig, can_sig, label)
    vss_sig:on_actuate(function(v)
        can_sig:publish(v and 1 or 0)
        log("[bgw] vss→can " .. label .. " = " .. tostring(v))
    end)
end

local function bind_door_actuate(vss_sig, can_sig, label)
    vss_sig:on_actuate(function(v)
        -- Safety Guard G1.1: Block unlocking doors when vehicle is moving
        local is_unlocking = (v == false or v == 0)
        if is_unlocking and current_vehicle_speed > 0 then
            log(string.format("[SAFETY GUARD G1.1 BLOCKED] Vehicle is moving at %.1f km/h. DENIED UNLOCK DOOR for %s!", current_vehicle_speed, label))
            return
        end
        can_sig:publish(v and 1 or 0)
        log("[bgw] vss→can " .. label .. " = " .. tostring(v))
    end)
end

bind_bool_actuate(vss_hvac.IsAirConditioningActive, can_hvac_command.IsAirConditioningActive, "IsAirConditioningActive")
bind_bool_actuate(vss_hvac.IsRecirculationActive,   can_hvac_command.IsRecirculationActive,   "IsRecirculationActive")
bind_bool_actuate(vss_hvac.IsAutoPowerOptimize,     can_hvac_command.IsAutoPowerOptimize,     "IsAutoPowerOptimize")
bind_bool_actuate(vss_hvac.IsFrontDefrosterActive,  can_hvac_command.IsFrontDefrosterActive,  "IsFrontDefrosterActive")
bind_bool_actuate(vss_hvac.IsRearDefrosterActive,   can_hvac_command.IsRearDefrosterActive,   "IsRearDefrosterActive")

bind_door_actuate(vss_door_r1d.IsLocked, can_door_command.Row1Driver_IsLocked,    "Row1Driver")
bind_door_actuate(vss_door_r1p.IsLocked, can_door_command.Row1Passenger_IsLocked, "Row1Passenger")
bind_door_actuate(vss_door_r2d.IsLocked, can_door_command.Row2Driver_IsLocked,    "Row2Driver")
bind_door_actuate(vss_door_r2p.IsLocked, can_door_command.Row2Passenger_IsLocked, "Row2Passenger")

-- ── CAN → KUKSA ───────────────────────────────────────

can_hvac_status.Driver_Temperature:on_change(function(v)    vss_hvac_d.Temperature:publish(v) end)
can_hvac_status.Passenger_Temperature:on_change(function(v) vss_hvac_p.Temperature:publish(v) end)
can_hvac_status.Driver_FanSpeed:on_change(function(v)       vss_hvac_d.FanSpeed:publish(fan_can_to_vss(v)) end)

can_hvac_status.Driver_AirDistribution:on_change(function(v)
    local s = AIR_CAN_TO_VSS[math.floor(v)]
    if s ~= nil then vss_hvac_d.AirDistribution:publish(s) end
end)

local function bind_bool_publish(status_sig, vss_sig)
    status_sig:on_change(function(v) vss_sig:publish(v ~= 0) end)
end
bind_bool_publish(can_hvac_status.IsAirConditioningActive, vss_hvac.IsAirConditioningActive)
bind_bool_publish(can_hvac_status.IsRecirculationActive,   vss_hvac.IsRecirculationActive)
bind_bool_publish(can_hvac_status.IsAutoPowerOptimize,     vss_hvac.IsAutoPowerOptimize)
bind_bool_publish(can_hvac_status.IsFrontDefrosterActive,  vss_hvac.IsFrontDefrosterActive)
bind_bool_publish(can_hvac_status.IsRearDefrosterActive,   vss_hvac.IsRearDefrosterActive)

bind_bool_publish(can_door_status.Row1Driver_IsLocked,    vss_door_r1d.IsLocked)
bind_bool_publish(can_door_status.Row1Passenger_IsLocked, vss_door_r1p.IsLocked)
bind_bool_publish(can_door_status.Row2Driver_IsLocked,    vss_door_r2d.IsLocked)
bind_bool_publish(can_door_status.Row2Passenger_IsLocked, vss_door_r2p.IsLocked)

local function bind_tire(can_sig, vss_sig, label)
    can_sig:on_change(function(v)
        local kpa = math.floor((v or 0) + 0.5)
        vss_sig:publish(kpa)
        log("[bgw] can→vss " .. label .. " = " .. kpa .. " kPa")
    end)
end
bind_tire(can_tire_pressure.Row1Left_TirePressure,  vss_tire_r1l.Pressure, "Row1Left")
bind_tire(can_tire_pressure.Row1Right_TirePressure, vss_tire_r1r.Pressure, "Row1Right")
bind_tire(can_tire_pressure.Row2Left_TirePressure,  vss_tire_r2l.Pressure, "Row2Left")
bind_tire(can_tire_pressure.Row2Right_TirePressure, vss_tire_r2r.Pressure, "Row2Right")

can_power_state.LowVoltageSystemState:on_change(function(v)
    local s = POWER_STATE[math.floor(v)]
    if s ~= nil then Vehicle.LowVoltageSystemState:publish(s) end
end)

can_seatbelt.VCU_Seatbelt_Sts:on_change(function(v)
    local state = math.floor(v or 0)
    local belted = state == SEATBELT_READY
    local occupied = state ~= SEATBELT_INIT
    vss_seat_r1d.IsBelted:publish(belted)
    vss_seat_r1d.OccupancyStatus:publish(occupied and "OCCUPIED" or "EMPTY")
    log(string.format("[bgw] can→vss Seatbelt_Sts = %d (belted=%s occupied=%s)",
        state, tostring(belted), tostring(occupied)))
end)

-- ── Cross-domain: Vehicle.Speed (KUKSA) → Body CAN PWT_VehicleSpeed ──
-- Speed originates in the Powertrain domain (Vehicle Info → Powertrain
-- Gateway → KUKSA). The SeatBelt vECU reads it off the Body CAN for its
-- NOT_READY_2 (speed>20) state, so the Body Gateway re-emits Vehicle.Speed
-- here as PWT_VehicleSpeed 0x460. DBC cyclic (100ms) keeps the vECU fresh.
Vehicle.Speed:on_change(function(v)
    current_vehicle_speed = v or 0
    can_pwt_speed.Speed_kph:publish(v or 0)
    log(string.format("[bgw] vss→can PWT_VehicleSpeed = %.1f km/h", v or 0))
end)

log("body_gateway ready")
