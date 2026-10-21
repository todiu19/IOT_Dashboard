// Prefix của chuỗi hiển thị: YYYY-MM-DD HH:mm:ss. Có thể dừng ở bất kỳ vị trí nào.
const TIME_PREFIX = /^(?:\d{1,4}|\d{4}-\d{0,2}|\d{4}-\d{2}-\d{0,2}|\d{4}-\d{2}-\d{2} \d{0,2}|\d{4}-\d{2}-\d{2} \d{2}:\d{0,2}|\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{0,2})$/;

export function normalizeTimePrefix(value) {
  const input = String(value ?? "").trim();
  if (!input) return "";
  if (!TIME_PREFIX.test(input)) return null;

  const [year, month, day, hour, minute, second] = input.split(/[- :]/);
  if (year.length === 4 && Number(year) < 1) return null;
  if (month?.length === 2 && (Number(month) < 1 || Number(month) > 12)) return null;
  if (day?.length === 2 && (Number(day) < 1 || Number(day) > 31)) return null;
  if (hour?.length === 2 && Number(hour) > 23) return null;
  if (minute?.length === 2 && Number(minute) > 59) return null;
  if (second?.length === 2 && Number(second) > 59) return null;
  if (year.length === 4 && month?.length === 2 && day?.length === 2) {
    const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
    if (date.getUTCFullYear() !== Number(year) || date.getUTCMonth() !== Number(month) - 1 || date.getUTCDate() !== Number(day)) return null;
  }
  return input;
}
