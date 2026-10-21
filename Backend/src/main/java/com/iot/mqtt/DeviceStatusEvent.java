package com.iot.mqtt;

import com.iot.common.enums.DeviceState;

public record DeviceStatusEvent(Integer deviceId, DeviceState status) {
}
