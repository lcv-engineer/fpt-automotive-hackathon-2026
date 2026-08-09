-- infotainment_gateway.lua — KUKSA ↔ VHAL bridge for the Latest Demo.
--
-- Bridges the shared Vehicle KUKSA Databroker to two independent consumers:
--   1. Android IVI Head Unit's VHAL gRPC server (mapped subset, transformed).
--   2. AGL Cluster's internal KUKSA (every signal, raw — see AGL forward).
-- These run as parallel branches off ONE Vehicle subscription; neither
-- gates the other. The CAN side flows through body/powertrain_gateway.
--
-- Pins:
--   pins.kuksa — gRPC client to shared Vehicle KUKSA Databroker (subscribe + actuate).
--   pins.vhal  — gRPC server :9300; Trout VHAL in IVI-FACE skycraft connects here.
--   pins.eth   — IVI Switch L2 NIC (carries the AGL forward traffic below).
--
-- See the `mappings` table for the full VSS sig ↔ VHAL (prop, area) map.

-- ── KUKSA endpoints ─────────────────────────────────────────────────
-- vehicle_kuksa : shared Vehicle Databroker (this pin). vss_root = its VSS tree.
-- agl_kuksa     : AGL VM's internal Databroker, reached over the eth-bridge L2
--                 (IGW pod's e-eth → AGL guest 10.99.0.3:55555). The guest IP +
--                 `--address 0.0.0.0` are baked by patch-agl-rootfs.sh; the
--                 eth-bridge DHCP pins the AGL MAC to that IP — no SSH workaround.
--                 AGL guest = vimba 21.90.0 / databroker 0.6.1, VSS 6.0 catalog,
--                 so its ListMetadata supplies datatypes and connect() needs no hints.
local vehicle_kuksa = pins.kuksa
assert(vehicle_kuksa and vehicle_kuksa.vss,
    "pins.kuksa.vss missing — Vehicle KUKSA databroker unreachable at start")

-- AGL-forward databroker (10.99.0.3:55555 over the eth-bridge) is OPTIONAL and
-- may be down. It is connected LAST (see the bottom of this file), AFTER the
-- Android VHAL ↔ Vehicle handlers are registered, so a down/slow AGL can never
-- gate the Android ↔ CAN path. Forward-declared here; stays nil until then.
local agl_kuksa = nil

local prop = vhal.prop
local seat = vhal.area.seat
local win  = vhal.area.window
local door = vhal.area.door
local wheel = vhal.area.wheel
local fan_dir = vhal.fan_direction

-- ════════════════════════════════════════════════════════════════════
--  Vendor propIds (platform extensions, range 0x21000000-0x2FFFFFFF)
-- ════════════════════════════════════════════════════════════════════

-- Engine RPM vendor mirror — VA app reads VENDOR_ENGINE_RPM = 0x21400020
-- because FAuto Trout doesn't expose PERF_ENGINE_RPM (standard) consistently.
prop.VENDOR_ENGINE_RPM = 0x21400020

-- Standard AOSP propIds VA app reads:
-- EV_BATTERY_LEVEL (current %). Standard AOSP ID from VehiclePropertyIds.
prop.EV_BATTERY_LEVEL           = 0x11600600

-- DOOR_LOCK propId — FAuto Trout AAOS ships an android.car.jar where
-- VehiclePropertyIds.DOOR_LOCK = 0x16200B02 (the older AOSP value).
-- nydus's vhal_constants.rs uses the same 0x16200B02 by default, so we
-- leave `prop.DOOR_LOCK` untouched. (Newer AOSP versions changed this
-- to 0x16400B9A; if FAuto upgrades, set the override here to match.)
-- Confirmed by VA app log: `setBooleanProperty(DOOR_LOCK=0x16200b02, ...)`.

-- HU HVAC vendor mirrors (carried over from face_vhal.lua so the AAOS
-- HvacPanel + voice assistant can write either standard or vendor propIds
-- and get the same effect).
prop.HU_HVAC_TEMPERATURE_DRIVER             = 0x21600409
prop.HU_HVAC_TEMPERATURE_PASSENGER          = 0x2160040A
prop.HU_HVAC_FAN_SPEED                      = 0x21400400
prop.HU_HVAC_POWER_STATE_CONDITIONER        = 0x21200402
prop.HU_HVAC_POWER_STATE_CIRCULATION        = 0x21200403
prop.HU_HVAC_POWER_STATE_AUTO_MODE          = 0x21200401
prop.HU_HVAC_DEFROSTER_STATE_FRONT          = 0x21200404
prop.HU_HVAC_DEFROSTER_STATE_REAR           = 0x21200405
prop.HU_HVAC_SEAT_WARMER_LEVEL_DRIVER       = 0x21400407
prop.HU_HVAC_SEAT_WARMER_LEVEL_PASSENGER    = 0x21400408

-- ════════════════════════════════════════════════════════════════════
--  VSS signal handles (nodes on the Vehicle tree). `vss_` prefix mirrors
--  the body_gateway / powertrain_gateway convention.
-- ════════════════════════════════════════════════════════════════════
local vss_root           = vehicle_kuksa.vss.Vehicle
local vss_cabin          = vss_root.Cabin
local vss_hvac           = vss_cabin.HVAC
local vss_hvac_driver    = vss_hvac.Station.Row1.Driver
local vss_hvac_passenger = vss_hvac.Station.Row1.Passenger
local vss_seat_driver    = vss_cabin.Seat.Row1.DriverSide
local vss_seat_passenger = vss_cabin.Seat.Row1.PassengerSide
local vss_door_driver    = vss_cabin.Door.Row1.DriverSide
local vss_tire_fl        = vss_root.Chassis.Axle.Row1.Wheel.Left.Tire
local vss_tire_fr        = vss_root.Chassis.Axle.Row1.Wheel.Right.Tire
local vss_tire_rl        = vss_root.Chassis.Axle.Row2.Wheel.Left.Tire
local vss_tire_rr        = vss_root.Chassis.Axle.Row2.Wheel.Right.Tire
local vss_powertrain     = vss_root.Powertrain
local vss_engine         = vss_powertrain.CombustionEngine
local vss_battery        = vss_powertrain.TractionBattery

-- ════════════════════════════════════════════════════════════════════
--  Conversion helpers
-- ════════════════════════════════════════════════════════════════════
local MPS_TO_KPH = 3.6
local function speed_kph_to_mps(v) return (v or 0) / MPS_TO_KPH end

-- Fan speed: VSS 6.0 0..100 percent ↔ VHAL 0..5 levels. Body CAN DBC and
-- body_gateway.lua use 0..5 (Driver_FanSpeed signal range [0|5]); IGW
-- mirrors that here so the round-trip is idempotent (no 17→20 VSS noise).
local function fan_vss_to_vhal(v) return math.floor(((v or 0) * 5) / 100 + 0.5) end
local function fan_vhal_to_vss(v) return math.floor(((v or 0) * 100) / 5 + 0.5) end

-- Air distribution: VSS uint8 (0=face,1=feet,2=face+feet,3=defrost) ↔
-- VHAL fan_dir bitmask (FACE/FLOOR/FACE_AND_FLOOR/DEFROST).
local DIR_VSS_TO_VHAL = {
    [0] = fan_dir.FACE,
    [1] = fan_dir.FLOOR,
    [2] = fan_dir.FACE_AND_FLOOR,
    [3] = fan_dir.DEFROST or fan_dir.FACE,
}
local DIR_VHAL_TO_VSS = {}
for k, v in pairs(DIR_VSS_TO_VHAL) do DIR_VHAL_TO_VSS[v] = k end

-- Seat heating: VSS int8 -3..+3 == VHAL HVAC_SEAT_TEMPERATURE -3..+3.
-- Passthrough.

-- ════════════════════════════════════════════════════════════════════
--  Change-only forwarding (VHAL side)
--
--  Single policy for all props: suppress pushes when the new value is
--  identical to the last one we forwarded. KUKSA on_change can re-emit
--  the same value (provider polling, ULP jitter on float, retained
--  emit on subscriber connect); without dedup each becomes a wasted
--  binder fan-out into AAOS car_service.
--
--  Rate-cap for high-frequency signals (Speed, RPM) is intentionally
--  OUT OF SCOPE here — handle at the producer side (DBC feeder
--  cadence) or in a future engine-level resample layer.
-- ════════════════════════════════════════════════════════════════════

-- Per-key state. Lua table keys are composed as "propId:areaId" because
-- nested numeric keys don't compose cleanly in Lua.
local last_vhal_value = {}

local function vhal_key(prop_id, area_id)
    return string.format("%d:%d", prop_id, area_id or 0)
end

local function push_if_changed(prop_id, area_id, value)
    local key = vhal_key(prop_id, area_id)
    local as_str = tostring(value)
    if last_vhal_value[key] == as_str then return false end
    last_vhal_value[key] = as_str
    pins.vhal:push(prop_id, area_id, value)
    return true
end

-- ════════════════════════════════════════════════════════════════════
--  VSS-sig-centric mapping table
--
--  Each entry declares ONE VSS signal + its (prop, area) consumers on the
--  VHAL side. Transforms (to_vhal, to_vss) and read_only flag live at
--  the sig level — they're properties of the signal, not the consumer.
--
--  Multiple consumers per sig handle the FAuto Trout pattern of a
--  standard AOSP propId + a HU_HVAC vendor mirror referring to the
--  same physical value.
-- ════════════════════════════════════════════════════════════════════
local mappings = {
    -- ── Speed (VSS km/h → VHAL m/s) ──
    {
        sig = vss_root.Speed, to_vhal = speed_kph_to_mps, read_only = true,
        consumers = { { prop = prop.PERF_VEHICLE_SPEED, area = 0 } },
    },

    -- ── HVAC zonal temperatures (driver + passenger) ──
    {
        sig = vss_hvac_driver.Temperature,
        consumers = {
            { prop = prop.HVAC_TEMPERATURE_SET,       area = seat.ROW_1_LEFT },
            { prop = prop.HU_HVAC_TEMPERATURE_DRIVER, area = 0 },
        },
    },
    {
        sig = vss_hvac_passenger.Temperature,
        consumers = {
            { prop = prop.HVAC_TEMPERATURE_SET,          area = seat.ROW_1_RIGHT },
            { prop = prop.HU_HVAC_TEMPERATURE_PASSENGER, area = 0 },
        },
    },

    -- Launcher nav-rail temperature badge. Read-only mirror of the driver
    -- setpoint (no separate CAN signal for ambient in this DBC). Same sig
    -- as the driver HVAC_TEMPERATURE_SET entry above — kept as a separate
    -- mapping so the read_only policy applies independently.
    {
        sig = vss_hvac_driver.Temperature, read_only = true,
        consumers = { { prop = prop.ENV_OUTSIDE_TEMPERATURE, area = 0 } },
    },

    -- ── HVAC fan speed (VSS 1..5 ↔ VHAL 0..4) ──
    {
        sig = vss_hvac_driver.FanSpeed,
        to_vss = fan_vhal_to_vss, to_vhal = fan_vss_to_vhal,
        consumers = {
            { prop = prop.HVAC_FAN_SPEED,    area = seat.ROW_1_LEFT },
            { prop = prop.HU_HVAC_FAN_SPEED, area = 0 },
        },
    },

    -- ── HVAC fan direction (VSS uint8 ↔ VHAL bitmask) ──
    {
        sig = vss_hvac_driver.AirDistribution,
        to_vss  = function(v) return DIR_VHAL_TO_VSS[v] or 0 end,
        to_vhal = function(v) return DIR_VSS_TO_VHAL[v] or fan_dir.FACE end,
        consumers = { { prop = prop.HVAC_FAN_DIRECTION, area = seat.ROW_1_LEFT } },
    },

    -- ── HVAC AC / recirc / auto power states ──
    {
        sig = vss_hvac.IsAirConditioningActive,
        consumers = {
            { prop = prop.HVAC_AC_ON,                      area = seat.ROW_1_LEFT },
            { prop = prop.HU_HVAC_POWER_STATE_CONDITIONER, area = 0 },
        },
    },
    {
        sig = vss_hvac.IsRecirculationActive,
        consumers = {
            { prop = prop.HVAC_RECIRC_ON,                  area = seat.ROW_1_LEFT },
            { prop = prop.HU_HVAC_POWER_STATE_CIRCULATION, area = 0 },
        },
    },
    -- VSS 6.0 has no exact equivalent of AAOS HVAC_AUTO_ON ("climate auto
    -- mode"); IsAutoPowerOptimize is the closest boolean toggle in the
    -- HVAC branch (semantically about power-optimization auto, not
    -- temperature auto — accepted for demo).
    {
        sig = vss_hvac.IsAutoPowerOptimize,
        consumers = {
            { prop = prop.HVAC_AUTO_ON,                  area = seat.ROW_1_LEFT },
            { prop = prop.HU_HVAC_POWER_STATE_AUTO_MODE, area = 0 },
        },
    },

    -- ── HVAC defroster front + rear ──
    {
        sig = vss_hvac.IsFrontDefrosterActive,
        consumers = {
            { prop = prop.HVAC_DEFROSTER,                area = win.FRONT_WINDSHIELD },
            { prop = prop.HU_HVAC_DEFROSTER_STATE_FRONT, area = 0 },
        },
    },
    {
        sig = vss_hvac.IsRearDefrosterActive,
        consumers = {
            { prop = prop.HVAC_DEFROSTER,               area = win.REAR_WINDSHIELD },
            { prop = prop.HU_HVAC_DEFROSTER_STATE_REAR, area = 0 },
        },
    },

    -- ── Seat heat/cool (VSS int8 -3..+3 == VHAL int32 -3..+3) ──
    -- VSS 6.0 renamed Seat.*.Heating → HeatingCooling.
    {
        sig = vss_seat_driver.HeatingCooling,
        consumers = {
            { prop = prop.HVAC_SEAT_TEMPERATURE,            area = seat.ROW_1_LEFT },
            { prop = prop.HU_HVAC_SEAT_WARMER_LEVEL_DRIVER, area = 0 },
        },
    },
    {
        sig = vss_seat_passenger.HeatingCooling,
        consumers = {
            { prop = prop.HVAC_SEAT_TEMPERATURE,               area = seat.ROW_1_RIGHT },
            { prop = prop.HU_HVAC_SEAT_WARMER_LEVEL_PASSENGER, area = 0 },
        },
    },

    -- ── Driver door lock (bidirectional) ──
    -- VSS IsLocked is a strict Bool — AAOS car_service writes the prop
    -- as int32 (0/1) so we coerce before :actuate, otherwise KUKSA
    -- returns InvalidArgument and the write never reaches CAN.
    {
        sig = vss_door_driver.IsLocked,
        to_vss  = function(v) return v == true or v == 1 end,
        to_vhal = function(v) return v and 1 or 0 end,
        consumers = { { prop = prop.DOOR_LOCK, area = door.ROW_1_LEFT or 1 } },
    },

    -- ── Driver seatbelt (standard AOSP buckled flag) ──
    -- SeatBelt ECU outputs the boolean buckled state on CAN; Body Gateway
    -- routes it to VSS IsBelted. Warning FSM (if shown) is derived
    -- client-side (cluster from IsBelted + Speed).
    {
        sig = vss_seat_driver.IsBelted, read_only = true,
        consumers = { { prop = prop.SEAT_BELT_BUCKLED, area = seat.ROW_1_LEFT } },
    },

    -- ── Tire pressure per wheel (one VSS sig per wheel, same propId) ──
    { sig = vss_tire_fl.Pressure, read_only = true, consumers = { { prop = prop.TIRE_PRESSURE, area = wheel.LEFT_FRONT  or 1 } } },
    { sig = vss_tire_fr.Pressure, read_only = true, consumers = { { prop = prop.TIRE_PRESSURE, area = wheel.RIGHT_FRONT or 2 } } },
    { sig = vss_tire_rl.Pressure, read_only = true, consumers = { { prop = prop.TIRE_PRESSURE, area = wheel.LEFT_REAR   or 4 } } },
    { sig = vss_tire_rr.Pressure, read_only = true, consumers = { { prop = prop.TIRE_PRESSURE, area = wheel.RIGHT_REAR  or 8 } } },

    -- ── Engine RPM (vendor mirror that VA app actually reads) ──
    {
        sig = vss_engine.Speed, read_only = true,
        consumers = { { prop = prop.VENDOR_ENGINE_RPM, area = 0 } },
    },

    -- ── Battery: current SoC (%) ──
    {
        sig = vss_battery.StateOfCharge.Current, read_only = true,
        consumers = { { prop = prop.EV_BATTERY_LEVEL, area = 0 } },
    },
}

-- ════════════════════════════════════════════════════════════════════
--  Build indices from `mappings`:
--    • sig_groups    — path → { sig, items = [m, ...] }   (VSS→VHAL: subscribe
--                       each unique path once; if multiple mappings share a
--                       path — e.g. ENV_OUTSIDE_TEMPERATURE mirrors
--                       vss_hvac_driver.Temperature read-only — items fans
--                       them out in declaration order)
--    • entry_by_prop — propId → areaId → mapping  (VHAL→VSS + on_get)
--
--  Invariants (asserted at script load):
--    • Same (propId, areaId) MUST map to exactly one mapping. Silent
--      overwrite would mask a misconfigured vendor mirror.
--    • Same VSS sig MAY appear in multiple mappings (cross-prop mirror
--      with diverging read_only is the only legitimate reason); per-
--      mapping read_only must partition cleanly across consumer propIds.
-- ════════════════════════════════════════════════════════════════════
local sig_groups    = {}
local entry_by_prop = {}
for _, m in ipairs(mappings) do
    local path = m.sig.path
    sig_groups[path] = sig_groups[path] or { sig = m.sig, items = {} }
    table.insert(sig_groups[path].items, m)

    for _, c in ipairs(m.consumers) do
        entry_by_prop[c.prop] = entry_by_prop[c.prop] or {}
        assert(entry_by_prop[c.prop][c.area] == nil,
            string.format("duplicate consumer (prop=0x%X, area=%s)",
                c.prop, tostring(c.area)))
        entry_by_prop[c.prop][c.area] = m
    end
end

-- ════════════════════════════════════════════════════════════════════
--  AGL forward policy (Vehicle KUKSA → AGL internal KUKSA)
--
--  Forward EVERY signal the gateway sees, unchanged. No allowlist.
--  Both sides speak COVESA VSS 6.0 — the AGL guest's stock catalog
--  (vss_6.0-agl.json, 1291 leaves) is a strict superset of the Vehicle
--  catalog (1267 leaves), so every Vehicle path exists on AGL.
--
--  The only thing that can fail a write is a datatype mismatch ("Wrong
--  type"). Per `scripts/diff_agl_vss.py` (vehicle-vss.json ↔ agl-vss.json)
--  there is exactly one across the whole catalog — CombustionEngine.Speed
--  (Vehicle float vs AGL uint16), an EV-build orphan with no producer.
--  Deny that one path; forward the rest.
-- ════════════════════════════════════════════════════════════════════
local AGL_DENY = {
    ["Vehicle.Powertrain.CombustionEngine.Speed"] = true,  -- Vehicle float vs AGL uint16
}

-- ════════════════════════════════════════════════════════════════════
--  Vehicle KUKSA → downstream (two parallel branches off ONE subscription)
--
--    AGL branch : forward every changed path to AGL KUKSA, raw.
--    VHAL branch: if the path is mapped, fan out to its VHAL consumers
--                 with the per-mapping transform + change-only dedup.
--  Neither branch gates the other.
-- ════════════════════════════════════════════════════════════════════

-- Push one (already-transformed-for-VHAL) value to every VHAL consumer
-- of the mapping. Dedup is applied per (prop, area) by push_if_changed.
local function dispatch_to_vhal(mapping, value_for_vhal)
    for _, c in ipairs(mapping.consumers) do
        local pushed = push_if_changed(c.prop, c.area, value_for_vhal)
        log(string.format("[igw]   → vhal 0x%08X area=0x%X %s = %s",
            c.prop, c.area, pushed and "pushed" or "skip", tostring(value_for_vhal)))
    end
end

local function on_vehicle_change(path, raw_value)
    -- VHAL branch FIRST — latency-critical path to Android IVI.
    -- Must never be gated by AGL network I/O.
    local group = sig_groups[path]
    if group then
        for _, m in ipairs(group.items) do
            local value_for_vhal = raw_value
            if m.to_vhal and value_for_vhal ~= nil then
                value_for_vhal = m.to_vhal(value_for_vhal)
            end
            dispatch_to_vhal(m, value_for_vhal)
        end
    end
    -- AGL branch SECOND — best-effort forward; Cluster tolerates higher latency.
    -- agl_kuksa:publish is a gRPC call over eth (10.99.0.3:55555); if it
    -- blocks, it must not delay the VHAL push above.
    if agl_kuksa and raw_value ~= nil and not AGL_DENY[path] then
        agl_kuksa:publish(path, raw_value)
    end
end

-- Subscribe to EVERY Vehicle leaf (not just the VHAL-mapped subset) so the
-- AGL branch sees all signals — powertrain included. Leaf paths come from
-- the broker's ListMetadata-built VSS tree.
local function collect_leaf_paths(node, out)
    for _, child in pairs(node) do
        if type(child) == "table" then
            if type(child.path) == "string" then
                out[#out + 1] = child.path
            else
                collect_leaf_paths(child, out)
            end
        end
    end
end
local all_paths = {}
collect_leaf_paths(vss_root, all_paths)

vehicle_kuksa:on_change(function(ev) on_vehicle_change(ev.path, ev.value) end)
vehicle_kuksa:subscribe(all_paths)
log(string.format("[igw] subscribed %d Vehicle paths (AGL forward-all + VHAL fan-out)", #all_paths))

-- ════════════════════════════════════════════════════════════════════
--  VHAL → Vehicle KUKSA (upstream actuate)
--
--  Android writes a VHAL prop → translate to its VSS sig → actuate the
--  shared Vehicle KUKSA. (AGL write-back would converge here too — see
--  the extension point below.)
-- ════════════════════════════════════════════════════════════════════
local function actuate_kuksa(prop_id, area_id, raw_from_vhal)
    local areas = entry_by_prop[prop_id]
    if not areas then return end
    local m = areas[area_id]
    if not m or m.read_only then return end
    local value = raw_from_vhal
    if m.to_vss and value ~= nil then value = m.to_vss(value) end
    if value == nil then return end

    -- 🛡️ AI Safety Guard G1 checks in IVI Gateway (Zonal Architecture)
    if prop_id == prop.DOOR_LOCK then
        local speed_kph = vss_root.Speed:get() or 0
        local is_unlock = (value == false or value == 0)
        if is_unlock and speed_kph > 0 then
            log(string.format("[SAFETY GUARD G1.1 BLOCKED] Vehicle Speed = %.1f km/h > 0. REFUSING DOOR UNLOCK!", speed_kph))
            return
        end
    elseif prop_id == prop.HVAC_TEMPERATURE_SET or prop_id == prop.HU_HVAC_TEMPERATURE_DRIVER or prop_id == prop.HU_HVAC_TEMPERATURE_PASSENGER then
        local temp = tonumber(value) or 22.0
        if temp < 16.0 or temp > 32.0 then
            log(string.format("[SAFETY GUARD G1.2 BLOCKED] Requested temp %.1f degC out of safe range (16-32 degC)!", temp))
            return
        end
    end

    m.sig:actuate(value)
    log(string.format("vhal->vss: 0x%08X area=%s → %s = %s",
        prop_id, tostring(area_id), m.sig.path, tostring(value)))
end

pins.vhal:on_change(function(msg) actuate_kuksa(msg.id, msg.area, msg.value) end)

-- EXTENSION POINT: AGL write-back
-- pins.eth → (parse path+value) → call actuate_kuksa(...) equivalent via VSS path

-- ════════════════════════════════════════════════════════════════════
--  VHAL on_get: synchronous read served from KUKSA. AAOS calls this on
--  subscribe-init (`getProperty`). `read_only` is intentionally NOT
--  honored here — it gates writes, not reads.
--
--  Contract: gateway publishes CHANGES only — no cold-start re-seed.
--  App side MUST call getProperty() on subscribe-init for initial state.
-- ════════════════════════════════════════════════════════════════════
pins.vhal:on_get(function(prop_id, area_id)
    local areas = entry_by_prop[prop_id]
    if not areas then return nil end
    local m = areas[area_id]
    if not m then return nil end
    local value = m.sig:get()
    if value ~= nil and m.to_vhal then value = m.to_vhal(value) end
    return value
end)

-- ════════════════════════════════════════════════════════════════════
--  AGL forward — connected LAST, on purpose.
--
--  The AGL guest databroker (10.99.0.3:55555, over the eth-bridge) is
--  optional and may be down. `nydus.kuksa.connect` now gives up after a
--  bounded metadata-fetch window instead of blocking forever, and we call it
--  HERE — after every Android ↔ Vehicle handler above is already registered —
--  so a down or slow AGL guest can never gate the Android ↔ CAN path
--  (HVAC, doors, etc.). If AGL is reachable the forward starts immediately;
--  if not, it is idle until AGL is up + the node is redeployed.
-- ════════════════════════════════════════════════════════════════════
agl_kuksa = nydus.kuksa.connect("http://10.99.0.3:55555")
log("[igw] AGL forward connected (10.99.0.3:55555)")

local consumer_count = 0
for _, m in ipairs(mappings) do consumer_count = consumer_count + #m.consumers end
log(string.format("infotainment_gateway: VHAL ↔ KUKSA bridge ready (%d sigs, %d VHAL consumers)",
    #mappings, consumer_count))
