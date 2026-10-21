import test from "node:test";
import assert from "node:assert/strict";
import { createChartData, createTimeTicks } from "./chart.js";

const reading = (time, sensorName = "Độ sáng (lux)") => ({
  sensorName,
  value: 1800,
  recordedAt: `2026-10-07T${time}`
});

function assertEvenTicks(timeline, ticks) {
  assert.equal(ticks[0], timeline[0]);
  assert.equal(ticks.at(-1), timeline.at(-1));
  const step = (timeline.at(-1) - timeline[0]) / (ticks.length - 1);
  for (let index = 1; index < ticks.length; index++) {
    assert.ok(Math.abs(ticks[index] - ticks[index - 1] - step) < 1);
  }
}

test("nhãn thời gian chia đều ngay khi trộn dữ liệu cũ và mới có khoảng ngắt", () => {
  const times = ["11:42:42", "11:42:47", "11:42:52", "11:42:57", "11:43:02", "11:43:07", "11:43:32", "11:43:36"];
  const { series, timeline } = createChartData(times.map((time) => reading(time)), ["light"]);
  const ticks = createTimeTicks(timeline);

  assert.equal(ticks.length, 8);
  assertEvenTicks(timeline, ticks);
  // Chỉ nhãn trục thay đổi; không thêm mẫu giả hoặc dời thời gian của điểm dữ liệu.
  assert.deepEqual(series.light.map((point) => point.dateTime.slice(11)), times);
  assert.notEqual(ticks[1], timeline[1]);
});

test("xử lý chart rỗng, chỉ một mốc thời gian và giới hạn một nhãn", () => {
  assert.deepEqual(createTimeTicks([]), []);
  assert.deepEqual(createTimeTicks([1000]), [1000]);
  assert.deepEqual(createTimeTicks([1000, 2000], 1), [1000]);
  assert.deepEqual(createTimeTicks([1000, 1000]), [1000]);
  assert.deepEqual(createTimeTicks([1000, 2000], 0), []);
});

test("ít mẫu vẫn chia nhãn đều trong khoảng thời gian thực", () => {
  assert.deepEqual(createTimeTicks([0, 1000, 10000]), [0, 5000, 10000]);
});

test("chart nhiệt độ và độ ẩm lệch thời điểm vẫn có tối đa tám nhãn chia đều", () => {
  const readings = Array.from({ length: 8 }, (_, index) => [
    reading(`11:42:${String(index * 6).padStart(2, "0")}`, "Nhiệt độ (°C)"),
    reading(`11:42:${String(index * 6 + 2).padStart(2, "0")}`, "Độ ẩm (%)")
  ]).flat();
  const { series, timeline } = createChartData(readings, ["temperature", "humidity"]);
  const ticks = createTimeTicks(timeline);

  assert.equal(series.temperature.length, 8);
  assert.equal(series.humidity.length, 8);
  assert.equal(timeline.length, 16);
  assert.equal(ticks.length, 8);
  assertEvenTicks(timeline, ticks);
});

test("nhãn luôn chia đều trong quá trình socket thay dần tám mẫu cũ", () => {
  const oldTimes = ["11:42:42", "11:42:47", "11:42:52", "11:42:57", "11:43:02", "11:43:07", "11:43:12", "11:43:17"];
  const newTimes = ["11:43:32", "11:43:37", "11:43:42", "11:43:47", "11:43:52", "11:43:57", "11:44:02", "11:44:07"];
  for (let count = 0; count <= newTimes.length; count++) {
    const readings = [...oldTimes, ...newTimes.slice(0, count)].map((time) => reading(time));
    const { series, timeline } = createChartData(readings, ["light"]);
    const ticks = createTimeTicks(timeline);

    assert.equal(series.light.length, 8);
    assert.equal(ticks.length, 8);
    assertEvenTicks(timeline, ticks);
    if (count === newTimes.length) {
      assert.deepEqual(series.light.map((point) => point.dateTime.slice(11)), newTimes);
    }
  }
});
