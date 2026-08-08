-- 1. ĐỊNH NGHĨA CÁC VHAL PROPERTY ID (VehicleProperties.kt)
local VHAL_PROP = {
    HVAC_TEMPERATURE_SET = 0x15600503, -- 358614275: Nhiệt độ điều hòa (°C) [Driver: Area 0, Passenger: Area 1]
    HVAC_FAN_SPEED       = 0x15400500, -- 356517120: Mức quạt gió (1 - 5)
    HVAC_POWER_ON        = 0x15400501, -- 356517121: Trạng thái A/C (0: Off, 1: On)
    HVAC_DEFROSTER       = 0x15400504, -- 356517124: Sấy kính trước (0: Off, 1: On)
    DOOR_LOCK            = 0x16200b02, -- 371198722: Trạng thái cửa xe (1/true: Locked, 0/false: Unlocked) [Row1Driver: Area 1]
    VEHICLE_SPEED        = 0x11600207, -- 308282375: Tốc độ xe (km/h)
    ENGINE_RPM           = 0x11600205, -- 291504645: Vòng tua mô-tơ/động cơ (rpm)
    GEAR_SELECTION       = 0x11400400, -- 289408000: Số xe (-1: R, 0: N, 126: P, 127: D)
    DRIVE_MODE           = 0x11400401, -- 289408001: Chế độ lái (0: Normal, 1: Sport, 2: Eco)
    EV_BATTERY_LEVEL     = 0x11600600, -- 291505664: Dung lượng Pin % (0.0 - 100.0)
    EV_BATTERY_RANGE     = 0x11600601, -- 291505665: Quãng đường đi còn lại (mét hoặc km)
    DRIVER_SEATBELT      = 0x16400b02, -- 373295874: Dây an toàn tài xế (0: Tháo, 1: Đeo)
    AUDIO_VOLUME         = 0x11400901  -- 289429249: Âm lượng buồng lái (0 - 100)
}

-- Chuẩn hóa Property ID đầu vào 
local function normalize_prop_id(prop_id)
    if type(prop_id) == "number" then
        return math.floor(prop_id)
    elseif type(prop_id) == "string" then
        if prop_id:sub(1, 2) == "0x" or prop_id:sub(1, 2) == "0X" then
            return tonumber(prop_id, 16)
        else
            return tonumber(prop_id)
        end
    end
    return prop_id
end

-- 2. TRẠNG THÁI XE THỜI GIAN THỰC 
local VehicleState = {
    speed = 0.0,            -- Tốc độ xe (km/h)
    motor_rpm = 0.0,        -- Vòng tua máy (rpm)
    hvac_temp_driver = 24.0,-- Nhiệt độ ghế lái (°C)
    hvac_temp_pass = 24.0,  -- Nhiệt độ ghế phụ (°C)
    fan_speed = 2,          -- Mức gió (1-5)
    ac_on = 1,              -- A/C active (0/1)
    defroster_on = 0,       -- Sấy kính (0/1)
    door_locked = 1,        -- Khóa cửa chuẩn AAOS Contract (1: Locked, 0: Unlocked)
    gear = "P",             -- Số hiện tại (P, R, N, D)
    gear_val = 126,         -- Mã số PRND (-1: R, 0: N, 126: P, 127: D)
    drive_mode = "NORMAL",  -- Chế độ lái
    soc_pct = 85.0,         -- Pin EV %
    range_m = 350000,       -- Quãng đường còn lại (m)
    driver_seatbelt = 1,    -- Dây an toàn tài xế (0: Tháo, 1: Đeo)
    volume = 50             -- Âm lượng buồng lái
}

-- 3. TẦNG BẢO VỆ AN TOÀN TẤT ĐỊNH 
-- Kiểm tra trực tiếp trên Property ID và Target Value
function check_safety_guard(prop_id, area_id, target_val)
    local norm_id = normalize_prop_id(prop_id)
    
    print(string.format("[SAFETY GUARD] Kiểm tra: PropId=0x%X | Area=%d | TargetVal=%s | Speed=%.1f km/h | Gear=%s", 
        norm_id or 0, area_id or 0, tostring(target_val), VehicleState.speed, VehicleState.gear))
    
    -- Quy tắc G1.1: CHẶN MỞ CỬA KHI XE ĐANG CHẠY (Speed > 0)
    -- Chuẩn AAOS: target_val == 0 hoặc false là MỞ CỬA (UNLOCKED). 1 hoặc true là KHÓA CỬA (LOCKED).
    if norm_id == VHAL_PROP.DOOR_LOCK then
        local is_unlock_request = (target_val == 0 or target_val == false or target_val == "0" or target_val == "UNLOCKED" or target_val == "OPEN")
        if is_unlock_request and VehicleState.speed > 0 then
            return false, string.format("CẢNH BÁO AN TOÀN (G1.1): Xe đang chạy ở tốc độ %.1f km/h. TỪ CHỐI MỞ CỬA XE!", VehicleState.speed)
        end
    end
    
    -- Quy tắc G1.2: GIỚI HẠN NHIỆT ĐỘ ĐIỀU HÒA AN TOÀN (16.0°C - 32.0°C)
    if norm_id == VHAL_PROP.HVAC_TEMPERATURE_SET then
        local temp = tonumber(target_val) or 22.0
        if temp < 16.0 or temp > 32.0 then
            return false, string.format("CẢNH BÁO AN TOÀN (G1.2): Nhiệt độ yêu cầu (%.1f°C) nằm ngoài dải an toàn (16°C - 32°C).", temp)
        end
    end
    
    -- Quy tắc G1.3: GIỚI HẠN MỨC GIÓ ĐIỀU HÒA (0 - 5)
    if norm_id == VHAL_PROP.HVAC_FAN_SPEED then
        local speed_val = tonumber(target_val) or 1
        if speed_val < 0 or speed_val > 5 then
            return false, string.format("CẢNH BÁO (G1.3): Mức gió (%d) nằm ngoài dải cho phép (0-5).", speed_val)
        end
    end

    -- Quy tắc G1.4: GIỚI HẠN ÂM LƯỢNG (0 - 100)
    if norm_id == VHAL_PROP.AUDIO_VOLUME then
        local vol = tonumber(target_val) or 50
        if vol < 0 or vol > 100 then
            return false, "CẢNH BÁO (G1.4): Mức âm lượng vượt dải cho phép 0 - 100."
        end
    end

    -- Quy tắc G2.1: CHUYỂN SỐ AN TOÀN (Không sang số R hoặc P khi xe đang chạy trên 10 km/h)
    if norm_id == VHAL_PROP.GEAR_SELECTION then
        local val_num = tonumber(target_val)
        if (target_val == "R" or target_val == "P" or val_num == -1 or val_num == 126) and VehicleState.speed > 10.0 then
            return false, string.format("CẢNH BÁO AN TOÀN (G2.1): Không thể cài số %s khi xe đang di chuyển tới %.1f km/h!", tostring(target_val), VehicleState.speed)
        end
    end

    -- Quy tắc G2.2: CẢNH BÁO DÂY AN TOÀN KHI XE CHẠY NHANH
    if norm_id == VHAL_PROP.DRIVER_SEATBELT then
        local belt_val = tonumber(target_val) or 0
        if belt_val == 0 and VehicleState.speed > 20.0 then
            return false, string.format("CẢNH BÁO AN TOÀN (G2.2): Xe đang chạy %.1f km/h, không được tháo dây an toàn!", VehicleState.speed)
        end
    end

    return true, "Hợp lệ"
end

-- 4. HÀM GHI VHAL PROPERTY VÀO PIN OUTPUT (Gửi tới Skycraft AAOS HMI)
function set_vhal_property(prop_id, area_id, value)
    local norm_id = normalize_prop_id(prop_id)
    print(string.format("[VHAL DISPATCH] Prop: 0x%X | Area: %d | Value: %s", norm_id or 0, area_id or 0, tostring(value)))
    
    local payload = {
        property_id = norm_id,
        area_id = area_id or 0,
        value = value,
        timestamp = os.time()
    }

    if pins and pins.vhal and pins.vhal.write then
        pins.vhal:write(payload)
    elseif pins and pins.vhal0 and pins.vhal0.push then
        pins.vhal0:push(norm_id, area_id or 0, value)
    elseif Pin and Pin.VHAL_OUT and Pin.VHAL_OUT.write then
        Pin.VHAL_OUT:write(payload)
    end
end

-- 5. HÀM PHÁT TÍN HIỆU CAN BUS (Outbound CAN: VHAL -> CAN Bus Node)
function publish_can_signal(signal_name, value)
    print(string.format("[CAN DISPATCH] Publish CAN Signal: %s = %s", signal_name, tostring(value)))
    
    local can_pin = (pins and (pins.can0 or pins.can)) or (Pin and Pin.CAN_OUT)
    if can_pin and can_pin.db and can_pin.db[signal_name] then
        can_pin.db[signal_name]:publish(value)
    end
end

-- 6. BỘ ĐIỀU PHỐI VHAL PROPERTY CHÍNH (VHAL PROPERTY DISPATCHER:
-- Đây chính là hàm tiếp nhận chính từ Android Service FW / AAOS Native client
function process_vhal_set_property(property_id, area_id, value)
    local norm_id = normalize_prop_id(property_id)
    area_id = area_id or 0

    print(string.format("[VHAL SERVER] Xử lý setProperty: 0x%X | Area: %d | Value: %s", norm_id or 0, area_id, tostring(value)))

    local is_safe, msg = check_safety_guard(norm_id, area_id, value)
    if not is_safe then
        print(string.format("[VHAL SERVER BLOCKED] %s", msg))
        return { status = "BLOCKED_BY_SAFETY", message = msg }
    end

    if norm_id == VHAL_PROP.HVAC_TEMPERATURE_SET then
        local temp = tonumber(value) or 22.0
        VehicleState.hvac_temp_driver = temp
        if area_id == 1 then VehicleState.hvac_temp_pass = temp end
        set_vhal_property(VHAL_PROP.HVAC_TEMPERATURE_SET, area_id, temp)
        publish_can_signal("Driver_Temperature", temp)
        publish_can_signal("TargetTemperature", temp)
        return { status = "SUCCESS", message = string.format("Đã cập nhật nhiệt độ HVAC %.1f°C.", temp), value = temp }

    elseif norm_id == VHAL_PROP.HVAC_FAN_SPEED then
        local speed = tonumber(value) or 1
        VehicleState.fan_speed = speed
        set_vhal_property(VHAL_PROP.HVAC_FAN_SPEED, area_id, speed)
        publish_can_signal("Driver_FanSpeed", speed)
        publish_can_signal("FanSpeed", speed)
        return { status = "SUCCESS", message = string.format("Đã chỉnh quạt gió mức %d.", speed), value = speed }

    elseif norm_id == VHAL_PROP.HVAC_POWER_ON then
        local ac_state = (value == 1 or value == true or value == "1") and 1 or 0
        VehicleState.ac_on = ac_state
        set_vhal_property(VHAL_PROP.HVAC_POWER_ON, area_id, ac_state)
        publish_can_signal("IsAirConditioningActive", ac_state)
        return { status = "SUCCESS", message = (ac_state == 1) and "Đã bật A/C." or "Đã tắt A/C.", value = ac_state }

    elseif norm_id == VHAL_PROP.HVAC_DEFROSTER then
        local def_state = (value == 1 or value == true or value == "1") and 1 or 0
        VehicleState.defroster_on = def_state
        set_vhal_property(VHAL_PROP.HVAC_DEFROSTER, area_id, def_state)
        publish_can_signal("IsFrontDefrosterActive", def_state)
        return { status = "SUCCESS", message = (def_state == 1) and "Đã bật sấy kính." or "Đã tắt sấy kính.", value = def_state }

    elseif norm_id == VHAL_PROP.DOOR_LOCK then
        -- ĐẢO CỰC THEO CONTRACT AAOS: 1/true = LOCKED (Khóa), 0/false = UNLOCKED (Mở)
        local lock_val = (value == 1 or value == true or value == "1" or value == "LOCK" or value == "LOCKED") and 1 or 0
        VehicleState.door_locked = lock_val
        set_vhal_property(VHAL_PROP.DOOR_LOCK, area_id, lock_val)
        publish_can_signal("Row1Driver_IsLocked", lock_val)
        publish_can_signal("DoorLockStatus", lock_val)
        return { status = "SUCCESS", message = (lock_val == 1) and "Đã khóa cửa xe." or "Đã mở cửa xe.", value = lock_val }

    elseif norm_id == VHAL_PROP.AUDIO_VOLUME then
        local vol = tonumber(value) or 50
        VehicleState.volume = vol
        set_vhal_property(VHAL_PROP.AUDIO_VOLUME, area_id, vol)
        return { status = "SUCCESS", message = string.format("Đã chỉnh âm lượng %d.", vol), value = vol }

    elseif norm_id == VHAL_PROP.GEAR_SELECTION then
        local gear_map = { [-1] = "R", [0] = "N", [126] = "P", [127] = "D" }
        local gear_val = tonumber(value) or 126
        local gear_name = gear_map[gear_val] or "P"
        VehicleState.gear = gear_name
        VehicleState.gear_val = gear_val
        set_vhal_property(VHAL_PROP.GEAR_SELECTION, area_id, gear_val)
        publish_can_signal("SelectedGear", gear_val)
        return { status = "SUCCESS", message = string.format("Đã chuyển sang số %s.", gear_name), value = gear_val }

    elseif norm_id == VHAL_PROP.DRIVE_MODE then
        local mode_val = tonumber(value) or 0
        VehicleState.drive_mode = (mode_val == 1) and "SPORT" or ((mode_val == 2) and "ECO" or "NORMAL")
        set_vhal_property(VHAL_PROP.DRIVE_MODE, area_id, mode_val)
        publish_can_signal("PerformanceMode", mode_val)
        return { status = "SUCCESS", message = string.format("Đã chuyển chế độ lái %s.", VehicleState.drive_mode), value = mode_val }
    end

    return { status = "UNKNOWN_PROPERTY", message = string.format("Property 0x%X chưa được định nghĩa.", norm_id or 0) }
end

-- LẮNG NGHE SỰ KIỆN TỪ SKYCRAFT VHAL PIN 
local vhal_pin = (pins and (pins.vhal0 or pins.vhal)) or (Pin and Pin.VHAL_IN)
if vhal_pin and vhal_pin.on_change then
    vhal_pin:on_change(function(ev)
        if ev then
            process_vhal_set_property(ev.id or ev.property_id, ev.area or ev.area_id or 0, ev.value)
        end
    end)
end

-- 7. LẮNG NGHE TÍN HIỆU TỪ MẠNG CAN 
function init_can_inbound_listeners()
    local can_pin = pins and (pins.can0 or pins.can)
    if not (can_pin and can_pin.db) then
        print("[CAN INBOUND] Cảnh báo: Chưa tìm thấy CAN Pin với file DBC. Chạy chế độ VHAL Standalone.")
        return
    end

    print("[CAN INBOUND] Kích hoạt bộ lắng nghe 2 chiều cho body_can.dbc & powertrain_can.dbc...")

    -- 1. Powertrain: Tốc độ xe (Speed_kph hoặc VehicleSpeed)
    local speed_sig = can_pin.db.Speed_kph or can_pin.db.VehicleSpeed
    if speed_sig then
        speed_sig:on_change(function(val)
            VehicleState.speed = val
            print(string.format("[CAN INBOUND] Tốc độ xe: %.1f km/h", val))
            set_vhal_property(VHAL_PROP.VEHICLE_SPEED, 0, val)
        end)
    end

    -- 2. Powertrain: Tốc độ mô tơ (Speed_rpm hoặc EngineRPM)
    local rpm_sig = can_pin.db.Speed_rpm or can_pin.db.EngineRPM
    if rpm_sig then
        rpm_sig:on_change(function(val)
            VehicleState.motor_rpm = val
            set_vhal_property(VHAL_PROP.ENGINE_RPM, 0, val)
        end)
    end

    -- 3. Powertrain: Dung lượng Pin EV (SoC_pct)
    if can_pin.db.SoC_pct then
        can_pin.db.SoC_pct:on_change(function(val)
            VehicleState.soc_pct = val
            print(string.format("[CAN INBOUND] Pin EV: %.1f%%", val))
            set_vhal_property(VHAL_PROP.EV_BATTERY_LEVEL, 0, val)
        end)
    end

    -- 4. Powertrain: Quãng đường còn lại (Range_m)
    if can_pin.db.Range_m then
        can_pin.db.Range_m:on_change(function(val)
            VehicleState.range_m = val
            set_vhal_property(VHAL_PROP.EV_BATTERY_RANGE, 0, val)
        end)
    end

    -- 5. Powertrain: Số xe (SelectedGear)
    if can_pin.db.SelectedGear then
        can_pin.db.SelectedGear:on_change(function(val)
            VehicleState.gear_val = val
            set_vhal_property(VHAL_PROP.GEAR_SELECTION, 0, val)
        end)
    end

    -- 6. Body: Nhiệt độ điều hòa (Driver_Temperature hoặc TargetTemperature)
    local temp_sig = can_pin.db.Driver_Temperature or can_pin.db.TargetTemperature
    if temp_sig then
        temp_sig:on_change(function(val)
            VehicleState.hvac_temp_driver = val
            print(string.format("[CAN INBOUND] Nhiệt độ HVAC: %.1f°C", val))
            set_vhal_property(VHAL_PROP.HVAC_TEMPERATURE_SET, 0, val)
        end)
    end

    -- 7. Body: Mức gió (Driver_FanSpeed hoặc FanSpeed)
    local fan_sig = can_pin.db.Driver_FanSpeed or can_pin.db.FanSpeed
    if fan_sig then
        fan_sig:on_change(function(val)
            VehicleState.fan_speed = val
            set_vhal_property(VHAL_PROP.HVAC_FAN_SPEED, 0, val)
        end)
    end

    -- 8. Body: Trạng thái Khóa cửa (Row1Driver_IsLocked hoặc DoorLockStatus)
    local door_sig = can_pin.db.Row1Driver_IsLocked or can_pin.db.DoorLockStatus
    if door_sig then
        door_sig:on_change(function(val)
            VehicleState.door_locked = val
            print(string.format("[CAN INBOUND] Khóa cửa: %d", val))
            set_vhal_property(VHAL_PROP.DOOR_LOCK, 0, val)
        end)
    end

    -- 9. Body: Dây an toàn (VCU_Seatbelt_Sts hoặc DriverSeatBelt)
    local seatbelt_sig = can_pin.db.VCU_Seatbelt_Sts or can_pin.db.DriverSeatBelt
    if seatbelt_sig then
        seatbelt_sig:on_change(function(val)
            VehicleState.driver_seatbelt = val
            set_vhal_property(VHAL_PROP.DRIVER_SEATBELT, 0, val)
        end)
    end
end

-- 8. THAM CHIẾU HÀM KHỞI TẠO VÀ SIMULATOR TELEMETRY TRỰC TIẾP
function on_telemetry_received(data)
    if data.speed ~= nil then
        VehicleState.speed = data.speed
        set_vhal_property(VHAL_PROP.VEHICLE_SPEED, 0, data.speed)
    end
    if data.driver_seatbelt ~= nil then
        VehicleState.driver_seatbelt = data.driver_seatbelt
        set_vhal_property(VHAL_PROP.DRIVER_SEATBELT, 0, data.driver_seatbelt)
    end
    print(string.format("[TELEMETRY] Cập nhật xe: Speed = %.1f km/h | SeatBelt = %d", VehicleState.speed, VehicleState.driver_seatbelt))
end

-- Tự động kích hoạt bộ lắng nghe CAN khi script nạp vào Script Node
init_can_inbound_listeners()
print("[VHAL SERVER] Đã nạp thành công VHAL Server Script cho VIVA Project! Tiếp nhận (propertyId, areaId, value).")
