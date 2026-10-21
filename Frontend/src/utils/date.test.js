import test from "node:test";
import assert from "node:assert/strict";
import { normalizeTimePrefix } from "./date.js";

test("chấp nhận tiền tố thời gian theo đúng thứ tự năm-tháng-ngày giờ:phút:giây", () => {
  for (const value of [
    "2", "2026", "2026-", "2026-1", "2026-10", "2026-10-0", "2026-10-06",
    "2026-10-06 2", "2026-10-06 22", "2026-10-06 22:4",
    "2026-10-06 22:46", "2026-10-06 22:46:1", "2026-10-06 22:46:12"
  ]) assert.equal(normalizeTimePrefix(value), value);
  assert.equal(normalizeTimePrefix(" 2026-10 "), "2026-10");
  assert.equal(normalizeTimePrefix(""), "");
});

test("từ chối định dạng khác hoặc giá trị thời gian không tồn tại", () => {
  for (const value of [
    "07/10/2026", "14:30", "ngày 7", "2026-13", "2026-02-30", "2025-02-29",
    "2026-10-06 24", "2026-10-06 22:60", "2026-10-06 22:46:60", "abc"
  ]) assert.equal(normalizeTimePrefix(value), null, value);
});
