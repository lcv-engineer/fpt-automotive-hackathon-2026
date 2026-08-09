-- vcu.lua — Vehicle Control Unit (Powertrain-domain coordinator, EV).
--
-- Owns the motioncontrol signals vehicle speed (gpio slider), derived
-- electric-motor speed, a fixed odometer reading, selected gear, drive mode.
-- All values are pure functions of gpio input — no time-based simulation.
-- Publishes onto the Powertrain CAN; the Powertrain Gateway bridges to KUKSA.
--
-- Pins
--   pins.can    — Powertrain CAN (DBC exampleslatest_demodbcpowertrain_can.dbc)
--   pins.sensor — Drive Controls gpio-panel
--                   Speed      slider   0..240 kmh
--                   Gear       selector 0=P 1=R 2=N 3=D
--                   DriveMode  selector 0=NORMAL 1=SPORT 2=ECONOMY 3=SNOW 4=RAIN
--
-- DBC cycle times (Speed 100ms, MotorSpeed 50ms, Odometer 1s, Drivetrain
-- 100ms) make nydus broadcast cyclically once publish primes the cache.

local can      = pins.can
local controls = pins.sensor
local db       = can.db

-- Single-speed EV reduction motor rpm ≈ road speed × ratio (≈15840 @ 240).
local RPM_PER_KPH = 66

-- gpio gear index → VSS-native PRNDL int8 (P=126, R=-1, N=0, D=127).
local GEAR_BY_INDEX = { [0] = 126, [1] = -1, [2] = 0, [3] = 127 }

local speed_kph  = 0
local ODOMETER_M = 12000000   -- fixed odometer reading (~12,000 km); static, not time-integrated

local function publish_motion()
    db.PWT_VehicleSpeed.Speed_kph:publish(speed_kph)
    local rpm = math.floor(speed_kph * RPM_PER_KPH)
    if rpm > 16000 then rpm = 16000 end
    db.PWT_MotorSpeed.Speed_rpm:publish(rpm)
end

-- ── Speed slider → speed + derived motor rpm ───────────
controls:on("Speed", function(v)
    speed_kph = v or 0
    publish_motion()
    log(string.format("[vcu] speed=%.0f kmh", speed_kph))
end)

-- ── Gear selector (index → PRNDL) ──────────────────────
controls:on("Gear", function(v)
    local g = GEAR_BY_INDEX[math.floor(v or 0)] or 126
    db.PWT_DrivetrainStatus.SelectedGear:publish(g)
    log(string.format("[vcu] gear index=%s -> PRNDL %d", tostring(v), g))
end)

-- ── Drive-mode selector (0..4 == DBC PerformanceMode VAL_) ──
controls:on("DriveMode", function(v)
    local m = math.floor(v or 0)
    db.PWT_DrivetrainStatus.PerformanceMode:publish(m)
    log(string.format("[vcu] drive mode = %d", m))
end)

-- ── Bootstrap (static values; DBC cyclic re-broadcasts them) ──
publish_motion()
db.PWT_DrivetrainStatus.SelectedGear:publish(126)    -- Park
db.PWT_DrivetrainStatus.PerformanceMode:publish(0)   -- NORMAL
db.PWT_Odometer.TraveledDistance_m:publish(ODOMETER_M)

log("vcu ready")
