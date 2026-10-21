const STATUSES = new Set(["LOADING", "ON", "OFF", "FAILED"]);

export function mergeControlStatus(controls, response) {
  if (!Number.isInteger(response?.deviceId) || response.deviceId < 1
      || !Number.isInteger(response?.historyId) || response.historyId < 1
      || !STATUSES.has(response.status)) {
    throw new Error("Phản hồi trạng thái điều khiển không hợp lệ.");
  }
  const previous = controls.get(response.deviceId);
  if (previous && (previous.historyId > response.historyId
      || (previous.historyId === response.historyId
          && (previous.status !== "LOADING" || previous.status === response.status)))) {
    return controls;
  }
  return new Map(controls).set(response.deviceId, response);
}
