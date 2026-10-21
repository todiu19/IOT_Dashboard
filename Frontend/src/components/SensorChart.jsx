import { chartScale, createChartData, createTimeTicks } from "../utils/chart.js";

export { sensorKind, formatDateTime } from "../utils/chart.js";

const PLOT = { left: 42, top: 10, width: 436, height: 186 };
const SERIES_LABELS = { temperature: "Nhiệt độ", humidity: "Độ ẩm", light: "Độ sáng" };
const SERIES_UNITS = { temperature: "°C", humidity: "%", light: "lux" };
const axisLabel = (value) => Number(value.toFixed(1)).toString();

export default function SensorChart({ readings, variant = "climate" }) {
  const kinds = variant === "light" ? ["light"] : ["temperature", "humidity"];
  const { series, timeline, dateLabel } = createChartData(readings, kinds);
  const scales = variant === "light"
    ? { light: chartScale(series.light, 0, 4500, 1125) }
    : {
        temperature: chartScale(series.temperature, 0, 40, 10),
        humidity: chartScale(series.humidity, 0, 100, 25)
      };
  const leftKind = kinds[0];
  const timeStart = timeline[0];
  const timeEnd = timeline.at(-1);
  const xPosition = (timestamp) => PLOT.left + (timeEnd === timeStart
    ? PLOT.width / 2 : (timestamp - timeStart) / (timeEnd - timeStart) * PLOT.width);
  const yPosition = (value, kind) => {
    const { minimum, maximum } = scales[kind];
    return PLOT.top + (maximum - value) / (maximum - minimum) * PLOT.height;
  };
  const labels = createTimeTicks(timeline);

  return (
    <>
      <svg className="chart-svg" viewBox="0 0 510 228" role="img" aria-label={variant === "light" ? "Biểu đồ độ sáng theo thời gian" : "Biểu đồ nhiệt độ và độ ẩm theo thời gian"}>
        {Array.from({ length: 5 }, (_, index) => {
          const y = PLOT.top + index * PLOT.height / 4;
          const leftScale = scales[leftKind];
          const leftValue = leftScale.maximum - index * (leftScale.maximum - leftScale.minimum) / 4;
          const rightValue = variant === "climate"
            ? scales.humidity.maximum - index * (scales.humidity.maximum - scales.humidity.minimum) / 4 : null;
          return (
            <g key={index}>
              <line className="grid" x1={PLOT.left} y1={y} x2={PLOT.left + PLOT.width} y2={y} />
              <text className={`chart-axis axis-${leftKind}`} x={PLOT.left - 10} y={y + 4} textAnchor="end">{axisLabel(leftValue)}</text>
              {rightValue !== null && <text className="chart-axis axis-humidity" x={PLOT.left + PLOT.width + 8} y={y + 4}>{axisLabel(rightValue)}</text>}
            </g>
          );
        })}
        {labels.map((timestamp) => (
          <text className="chart-time" key={timestamp} x={xPosition(timestamp)} y="219" textAnchor="middle">{new Date(timestamp).toTimeString().slice(0, 8)}</text>
        ))}
        {kinds.map((kind) => (
          <g key={kind}>
            <path className={`line ${kind}-line`} d={series[kind].map((point, index) =>
              `${index ? "L" : "M"}${xPosition(point.timestamp).toFixed(2)},${yPosition(point.value, kind).toFixed(2)}`).join(" ")} />
            {series[kind].map((point, index) => (
              <circle className={`chart-point point-${kind}${series[kind].length === 1 ? " is-single" : ""}`} key={`${point.timestamp}-${index}`} cx={xPosition(point.timestamp)} cy={yPosition(point.value, kind)} r="4">
                <title>{SERIES_LABELS[kind]}: {point.value} {SERIES_UNITS[kind]} · {point.dateTime}</title>
              </circle>
            ))}
          </g>
        ))}
        {!timeline.length && <text className="chart-empty" x="260" y="110" textAnchor="middle">Chưa có dữ liệu cảm biến.</text>}
      </svg>
      <p className="chart-date">{dateLabel}</p>
    </>
  );
}
