import test from "node:test";
import assert from "node:assert/strict";
import { mergeControlStatus } from "./controlStatus.js";

const response = (status, historyId = 1, deviceId = 1) => ({ status, historyId, deviceId });

test("trạng thái điều khiển chuyển từ LOADING sang ON/OFF hoặc FAILED", () => {
  for (const status of ["ON", "OFF", "FAILED"]) {
    const initial = new Map();
    const loading = mergeControlStatus(initial, response("LOADING"));
    const done = mergeControlStatus(loading, response(status));
    assert.equal(initial.size, 0);
    assert.equal(loading.get(1).status, "LOADING");
    assert.equal(done.get(1).status, status);
  }
});

test("HTTP LOADING về sau kết quả socket không làm LED quay lại trạng thái chờ", () => {
  for (const status of ["ON", "OFF", "FAILED"]) {
    const done = mergeControlStatus(new Map(), response(status));
    assert.equal(mergeControlStatus(done, response("LOADING")), done);
    assert.equal(mergeControlStatus(done, response(status)), done);
  }
});

test("không đổi lịch sử đã FAILED thành ON do tin đến trễ", () => {
  const failed = mergeControlStatus(new Map(), response("FAILED"));
  assert.equal(mergeControlStatus(failed, response("ON")), failed);
});

test("lệnh mới được chờ riêng, tin của lệnh cũ không kết thúc lệnh mới", () => {
  const old = mergeControlStatus(new Map(), response("ON", 1));
  const newer = mergeControlStatus(old, response("LOADING", 2));
  assert.equal(mergeControlStatus(newer, response("FAILED", 1)), newer);
  assert.equal(mergeControlStatus(newer, response("OFF", 2)).get(1).status, "OFF");
});

test("mỗi thiết bị có trạng thái lệnh riêng và LOADING trùng được bỏ qua", () => {
  const first = mergeControlStatus(new Map(), response("LOADING", 1, 1));
  const both = mergeControlStatus(first, response("LOADING", 2, 2));
  assert.equal(mergeControlStatus(both, response("LOADING", 2, 2)), both);
  const done = mergeControlStatus(both, response("ON", 1, 1));
  assert.equal(done.get(1).status, "ON");
  assert.equal(done.get(2).status, "LOADING");
});

test("không nhận payload trạng thái thiếu ID hoặc trạng thái không hợp lệ", () => {
  for (const value of [null, {}, response("UNKNOWN"), response("ON", 0), response("ON", 1, -1)]) {
    assert.throws(() => mergeControlStatus(new Map(), value));
  }
});
