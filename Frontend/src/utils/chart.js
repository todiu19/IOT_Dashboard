export function sensorKind(name) {
  const normalized = String(name || "").toLowerCase();
  if (normalized.includes("nhiệt") || normalized.includes("temperature")) return "temperature";
  if (normalized.includes("ẩm") || normalized.includes("humidity")) return "humidity";
  if (normalized.includes("sáng") || normalized.includes("light") || normalized.includes("lux")) return "light";
  return null;
}

export function formatDateTime(value) {
  return value?.replace("T", " ").slice(0, 19) ?? "";
}

export function createChartData(readings, kinds, limit = 8) {
  const series = Object.fromEntries(kinds.map((kind) => [kind, []]));
  for (const reading of readings) {
    const kind = sensorKind(reading.sensorName);
    if (!kinds.includes(kind) || reading.value == null || reading.value === "") continue;
    const value = Number(reading.value);
    const dateTime = formatDateTime(reading.recordedAt);
    const timestamp = new Date(dateTime.replace(" ", "T")).getTime();
    if (!Number.isFinite(value) || !Number.isFinite(timestamp)) continue;
    series[kind].push({ value, dateTime, timestamp });
  }

  for (const kind of kinds) {
    // Mỗi cảm biến giữ các mẫu mới nhất, không phụ thuộc thứ tự dữ liệu API/WS.
    series[kind] = series[kind].sort((a, b) => a.timestamp - b.timestamp).slice(-limit);
  }
  const points = Object.values(series).flat().sort((a, b) => a.timestamp - b.timestamp);
  const timeline = [...new Set(points.map((point) => point.timestamp))];
  const firstDate = points[0]?.dateTime.slice(0, 10);
  const lastDate = points.at(-1)?.dateTime.slice(0, 10);
  const dateLabel = firstDate && lastDate
    ? firstDate === lastDate ? lastDate : `${firstDate} – ${lastDate}`
    : "--";
  return { series, timeline, dateLabel };
}

export function createTimeTicks(timeline, maxLabels = 8) {
  if (!timeline.length || maxLabels < 1) return [];
  const start = timeline[0];
  const end = timeline.at(-1);
  const count = Math.min(timeline.length, maxLabels);
  if (count === 1 || start === end) return [start];

  // Nhãn trục chia đều theo thời gian, độc lập với khoảng cách giữa các mẫu đo.
  return Array.from({ length: count }, (_, index) =>
    start + (end - start) * index / (count - 1));
}

export function chartScale(points, minimum, maximum, step) {
  for (const point of points) {
    minimum = Math.min(minimum, Math.floor(point.value / step) * step);
    maximum = Math.max(maximum, Math.ceil(point.value / step) * step);
  }
  return { minimum, maximum };
}
