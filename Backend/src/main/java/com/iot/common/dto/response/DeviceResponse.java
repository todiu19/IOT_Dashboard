package com.iot.common.dto.response;

import com.iot.common.enums.DeviceState;

public record DeviceResponse(Integer id, String name, DeviceState status) {
}
