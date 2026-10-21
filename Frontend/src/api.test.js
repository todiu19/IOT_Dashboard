import test, { beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { API, apiFetch } from "./api.js";

const originalFetch = globalThis.fetch;
const originalDocument = globalThis.document;
const response = (status, payload) => new Response(JSON.stringify(payload), {
  status, headers: { "Content-Type": "application/json" }
});
const success = (data) => response(200, { code: 0, data });
beforeEach(() => {
  globalThis.document = { cookie: "XSRF-TOKEN=legacy-cookie" };
});

afterEach(() => {
  globalThis.fetch = originalFetch;
  if (originalDocument === undefined) delete globalThis.document;
  else globalThis.document = originalDocument;
});

test("API profile gửi cookie và trả hồ sơ để khôi phục đăng nhập", async () => {
  const profile = {
    id: 1, username: "test-user", fullname: "Test User", msv: "TEST001",
    email: "test@example.com", github: null, postman: null, figma: null, pdf: null
  };
  assert.equal(API.profile, "/api/profile");
  assert.equal("me" in API, false);
  assert.equal("csrf" in API, false);
  globalThis.fetch = async (url, options) => {
    assert.equal(new URL(url).pathname, API.profile);
    assert.equal(options.method, "GET");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers.has("X-XSRF-TOKEN"), false);
    return success(profile);
  };
  assert.deepEqual(await apiFetch(API.profile), profile);
});

test("API profile giữ lỗi 401 khi không còn phiên đăng nhập", async () => {
  let calls = 0;
  globalThis.fetch = async () => {
    calls++;
    return response(401, { code: 401, errorCode: "UNAUTHENTICATED", message: "Chưa đăng nhập." });
  };
  await assert.rejects(apiFetch(API.profile), {
    status: 401, errorCode: "UNAUTHENTICATED", message: "Chưa đăng nhập."
  });
  assert.equal(calls, 1);
});

test("login gửi trực tiếp JSON và cookie, không lấy hoặc gửi CSRF", async () => {
  let calls = 0;
  const body = JSON.stringify({ username: "test-user", password: "test-password" });
  globalThis.fetch = async (url, options) => {
    calls++;
    assert.equal(new URL(url).pathname, API.login);
    assert.equal(options.method, "POST");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers.get("Content-Type"), "application/json");
    assert.equal(options.headers.has("X-XSRF-TOKEN"), false);
    assert.equal(options.body, body);
    return success({ id: 1, username: "test-user" });
  };
  assert.deepEqual(await apiFetch(API.login, { method: "post", body }), { id: 1, username: "test-user" });
  assert.equal(calls, 1);
});

test("điều khiển chỉ gửi một POST, không cần đọc document.cookie", async () => {
  delete globalThis.document;
  let calls = 0;
  const body = JSON.stringify({ action: "ON" });
  globalThis.fetch = async (url, options) => {
    calls++;
    assert.equal(new URL(url).pathname, API.controlDevice(1));
    assert.equal(options.method, "POST");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers.has("X-XSRF-TOKEN"), false);
    assert.equal(options.body, body);
    return success({ status: "LOADING" });
  };
  assert.deepEqual(await apiFetch(API.controlDevice(1), { method: "POST", body }), { status: "LOADING" });
  assert.equal(calls, 1);
});

test("logout chỉ gửi một POST với cookie, không cần CSRF", async () => {
  let calls = 0;
  globalThis.fetch = async (url, options) => {
    calls++;
    assert.equal(new URL(url).pathname, API.logout);
    assert.equal(options.method, "POST");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers.has("X-XSRF-TOKEN"), false);
    assert.equal(options.headers.has("Content-Type"), false);
    return response(200, { code: 0 });
  };
  assert.equal(await apiFetch(API.logout, { method: "POST" }), null);
  assert.equal(calls, 1);
});

test("không tự gửi lại lệnh khi lỗi quyền truy cập hoặc lỗi server", async () => {
  for (const status of [403, 500, 503]) {
    let calls = 0;
    globalThis.fetch = async () => {
      calls++;
      return response(status, { code: status, errorCode: "OTHER_ERROR", message: "Lỗi thực tế từ BE." });
    };
    await assert.rejects(apiFetch(API.controlDevice(1), { method: "POST", body: "{}" }), { status, message: "Lỗi thực tế từ BE." });
    assert.equal(calls, 1);
  }
});

test("giữ header tùy chỉnh và AbortSignal của request", async () => {
  const controller = new AbortController();
  globalThis.fetch = async (url, options) => {
    assert.equal(options.headers.get("Authorization"), "Bearer test-token");
    assert.equal(options.signal, controller.signal);
    assert.equal(options.credentials, "include");
    return success({ totalPages: 1, content: [] });
  };
  assert.deepEqual(await apiFetch(API.sensors, {
    headers: { Authorization: "Bearer test-token" }, signal: controller.signal
  }), { totalPages: 1, content: [] });
});

test("response HTTP 204 trả null", async () => {
  globalThis.fetch = async () => new Response(null, { status: 204 });
  assert.equal(await apiFetch(API.logout, { method: "POST" }), null);
});
