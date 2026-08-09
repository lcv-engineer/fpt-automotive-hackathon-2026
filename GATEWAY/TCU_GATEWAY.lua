-- tcu_gateway.lua — TCU domain gateway: Vehicle KUKSA ↔ SOME/IP (in-vehicle).
--
-- The TCU Gateway is the data-plane bridge of the Telematics domain, the
-- SOME/IP analogue of body_gateway/powertrain_gateway (which bridge CAN↔KUKSA).
-- It is the SOME/IP SERVICE PROVIDER; the TCU-NAD (TelAF tafSomeipGWSvc) is the
-- client and the sole MQTT egress to the cloud. KUKSA stays source-of-truth;
-- SOME/IP is transport in front of it.
--
-- Service VehicleInfo (0x2000 / instance 0x0001):
--   Telemetry (GW → NAD):
--     event 0x8001 (eventgroup 0x0001) — VssUpdate, one per VSS signal change
--       payload: JSON {"path":"<VSS path>","value":<scalar>}  (self-describing)
--   Command (NAD → GW), request/response:
--     method 0x9001 SetDoorLock — req: uint8 lock(0/1), uint8 mask
--                                 resp: uint8 status (0 = OK)
--
-- Pins:
--   pins.kuksa — shared Vehicle KUKSA Databroker (subscribe + actuate).
--   eth pin (e-eth) — Telematics-switch L2 NIC; vsomeip device + IP read at
--                     runtime (iface_ip), SD multicast 224.0.0.1:30490.

local kuksa = pins.kuksa
assert(kuksa and kuksa.vss, "pins.kuksa.vss missing — Vehicle KUKSA unreachable at start")
local Vehicle = kuksa.vss.Vehicle

-- In-vehicle pin on the TCU Switch. The kernel interface is e-<pinname>
-- ("e-eth"); its IP is DHCP-assigned, so read it at runtime (Lua is sandboxed
-- and can't, so nydus.vsomeip.iface_ip — a Rust helper — does it).
local IFACE       = "e-eth"
-- Fail loud (not a silent loopback bind) if the IP isn't up yet — the node
-- restarts and retries, which is better than vsomeip quietly binding 127.0.0.1.
local LOCAL_IP    = nydus.vsomeip.iface_ip(IFACE)
assert(LOCAL_IP, string.format(
    "[tcu-gw] FATAL: no IPv4 on '%s' yet — vsomeip cannot bind; check in-vehicle pin + DHCP", IFACE))
local SOMEIP_PORT = 30509
local SD_PORT     = 30490

local SVC          = 0x2000
local INST         = 0x0001
local EVENT_STATUS = 0x8001
local EVENTGROUP   = 0x0001
local METHOD_DOOR  = 0x0001

-- ── vsomeip configuration (before new()) ────────────────────────────
nydus.vsomeip.add_multicast_route("224.0.0.1", IFACE)

nydus.vsomeip.configure(string.format([[{
    "unicast": "%s",
    "netmask": "255.255.255.0",
    "device": "%s",
    "logging": { "level": "info", "console": "true" },
    "applications": [ { "name": "tcu-gateway", "id": "0x2000" } ],
    "services": [ { "service": "0x2000", "instance": "0x0001", "unreliable": "%d" } ],
    "routing": "tcu-gateway",
    "service-discovery": {
        "enable": "true",
        "multicast": "224.0.0.1",
        "port": "%d",
        "protocol": "udp",
        "initial_delay_min": "10",
        "initial_delay_max": "100",
        "repetitions_base_delay": "200",
        "repetitions_max": "3",
        "ttl": "3",
        "offer_debounce_time": "500",
        "find_debounce_time": "500"
    }
}]], LOCAL_IP, IFACE, SOMEIP_PORT, SD_PORT))

log("[tcu-gw] creating vsomeip application...")
local app = nydus.vsomeip.new("tcu-gateway")

app:offer(SVC, INST)
app:offer_event(SVC, INST, EVENT_STATUS, EVENTGROUP)
log(string.format("[tcu-gw] VehicleInfo 0x%04X offered on %s:%d (event 0x%04X)",
    SVC, LOCAL_IP, SOMEIP_PORT, EVENT_STATUS))

-- ── Forward VSS → SOME/IP (generic, per-signal) ─────────────────────
-- Each subscribed VSS signal is forwarded individually as it changes, as
-- one event 0x8001 carrying {path, value}. The VSS path is self-describing
-- (it IS the identifier) so there's no per-signal event ID nor fixed struct
-- to keep in sync — adding a signal = adding a path below.
-- Signal set = the "VSS Path" column of docs/signal_mapping.csv.

local FORWARD_PATHS = {
    "Vehicle.Speed",
    "Vehicle.TraveledDistance",
    "Vehicle.LowVoltageSystemState",
    "Vehicle.Powertrain.ElectricMotor.Speed",
    "Vehicle.Powertrain.CombustionEngine.Speed",
    "Vehicle.Powertrain.Range",
    "Vehicle.Powertrain.TractionBattery.StateOfCharge.Current",
    "Vehicle.Powertrain.TractionBattery.CurrentVoltage",
    "Vehicle.Powertrain.TractionBattery.CurrentCurrent",
    "Vehicle.Powertrain.TractionBattery.Temperature.Average",
    "Vehicle.Powertrain.Transmission.SelectedGear",
    "Vehicle.Powertrain.Transmission.PerformanceMode",
    "Vehicle.Cabin.HVAC.Station.Row1.Driver.Temperature",
    "Vehicle.Cabin.HVAC.Station.Row1.Passenger.Temperature",
    "Vehicle.Cabin.HVAC.Station.Row1.Driver.FanSpeed",
    "Vehicle.Cabin.HVAC.Station.Row1.Driver.AirDistribution",
    "Vehicle.Cabin.HVAC.IsAirConditioningActive",
    "Vehicle.Cabin.HVAC.IsRecirculationActive",
    "Vehicle.Cabin.HVAC.IsAutoPowerOptimize",
    "Vehicle.Cabin.HVAC.IsFrontDefrosterActive",
    "Vehicle.Cabin.HVAC.IsRearDefrosterActive",
    "Vehicle.Cabin.Seat.Row1.DriverSide.HeatingCooling",
    "Vehicle.Cabin.Seat.Row1.PassengerSide.HeatingCooling",
    "Vehicle.Cabin.Seat.Row1.DriverSide.IsBelted",
    "Vehicle.Cabin.Seat.Row1.DriverSide.OccupancyStatus",
    "Vehicle.Cabin.Door.Row1.DriverSide.IsLocked",
    "Vehicle.Cabin.Door.Row1.PassengerSide.IsLocked",
    "Vehicle.Cabin.Door.Row2.DriverSide.IsLocked",
    "Vehicle.Cabin.Door.Row2.PassengerSide.IsLocked",
    "Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.Pressure",
    "Vehicle.Chassis.Axle.Row1.Wheel.Right.Tire.Pressure",
    "Vehicle.Chassis.Axle.Row2.Wheel.Left.Tire.Pressure",
    "Vehicle.Chassis.Axle.Row2.Wheel.Right.Tire.Pressure",
}

-- JSON-encode a scalar VSS value (string gets quoted; bool/number bare).
local function json_value(v)
    local t = type(v)
    if t == "boolean" or t == "number" then return tostring(v) end
    if t == "string" then return '"' .. v .. '"' end
    return "null"
end

kuksa:subscribe(FORWARD_PATHS)
kuksa:on_change(function(ev)
    local payload = string.format('{"path":"%s","value":%s}', ev.path, json_value(ev.value))
    app:notify(SVC, INST, EVENT_STATUS, payload)
    log(string.format("[tcu-gw] fwd %s = %s", ev.path, tostring(ev.value)))
end)

-- ── Command: SOME/IP method 0x9001 SetDoorLock → KUKSA ──────────────
-- req payload: uint8 lock (1 = lock, 0 = unlock), uint8 mask (door bitmap).
-- Writes the KUKSA door-lock actuator target; the Body domain performs the
-- physical actuation and the resulting IsLocked value re-enters KUKSA (the
-- ack-of-reality). The method response only confirms accept + KUKSA write.

local doors = {
    Vehicle.Cabin.Door.Row1.DriverSide,
    Vehicle.Cabin.Door.Row1.PassengerSide,
    Vehicle.Cabin.Door.Row2.DriverSide,
    Vehicle.Cabin.Door.Row2.PassengerSide,
}

app:on_request(SVC, INST, METHOD_DOOR, function(req)
    local lock = string.unpack(">I1", req.payload)
    local locked = (lock ~= 0)
    for _, d in ipairs(doors) do
        d.IsLocked:actuate(locked)
    end
    log(string.format("[tcu-gw] SetDoorLock lock=%d → KUKSA (all doors)", lock))
    app:respond(req, string.pack(">I1", 0)) -- status 0 = accepted + written
end)

log("[tcu-gw] ready — provider offering VehicleInfo, SD on 224.0.0.1:" .. SD_PORT)
