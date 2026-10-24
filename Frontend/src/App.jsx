import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { API, apiFetch, getWebSocketUrl } from "./api.js";
import LoginScreen from "./components/LoginScreen.jsx";
import { sensorKind } from "./components/SensorChart.jsx";
import DashboardPage from "./pages/DashboardPage.jsx";
import DataSensorPage from "./pages/DataSensorPage.jsx";
import ActionHistoryPage from "./pages/ActionHistoryPage.jsx";
import ProfilePage from "./pages/ProfilePage.jsx";
import { normalizeTimePrefix } from "./utils/date.js";
import { mergeControlStatus } from "./utils/controlStatus.js";
import "./App.css";

const EMPTY_DASHBOARD = { latestReadings: [], chartData: [], devices: [] };
const EMPTY_PAGE = { content: [], totalPages: 1 };
const NAV_ITEMS = [
  { view: "dashboard", label: "Dashboard", icon: "fa-house" },
  { view: "sensors", label: "Data Sensor", icon: "fa-microchip" },
  { view: "history", label: "Action History", icon: "fa-clock-rotate-left" },
  { view: "profile", label: "Profile", icon: "fa-user" }
];

function pageQuery(page, size, filters) {
  const params = new URLSearchParams({ page: String(page - 1), size: String(size) });
  Object.entries(filters).forEach(([key, value]) => {
    if (String(value).trim()) params.set(key, String(value).trim());
  });
  return params.toString();
}

export default function App() {
  const [user, setUser] = useState(null);
  const [checkingSession, setCheckingSession] = useState(true);
  const [loginLoading, setLoginLoading] = useState(false);
  const [loginError, setLoginError] = useState("");
  const [view, setView] = useState("dashboard");
  const [dashboard, setDashboard] = useState(EMPTY_DASHBOARD);
  const [alertRefresh, setAlertRefresh] = useState(0);
  const [profile, setProfile] = useState(null);
  const [pendingDeviceIds, setPendingDeviceIds] = useState(new Set());
  const pendingDeviceRef = useRef(new Set());
  const pendingRequestRef = useRef(new Set());
  const controlStatesRef = useRef(new Map());
  const confirmedDevicesRef = useRef(new Map());
  const controlRevision = useRef(0);
  const [controlErrors, setControlErrors] = useState({});
  const dashboardRequest = useRef(0);
  const sessionGeneration = useRef(0);

  const [sensorFilters, setSensorFilters] = useState({ search: "", sensor: "", date: "" });
  const [sensorSearch, setSensorSearch] = useState("");
  const [sensorFilterMode, setSensorFilterMode] = useState("");
  const [sensorPage, setSensorPage] = useState(1);
  const [sensorSize, setSensorSize] = useState(20);
  const [sensorResult, setSensorResult] = useState(EMPTY_PAGE);
  const [sensorRefresh, setSensorRefresh] = useState(0);
  const [sensorError, setSensorError] = useState("");

  const [historyFilters, setHistoryFilters] = useState({ deviceId: "", action: "", status: "", date: "" });
  const [historyPage, setHistoryPage] = useState(1);
  const [historySize, setHistorySize] = useState(20);
  const [historyResult, setHistoryResult] = useState(EMPTY_PAGE);
  const [historyRefresh, setHistoryRefresh] = useState(0);
  const [historyError, setHistoryError] = useState("");

  useEffect(() => {
    let active = true;
    async function restoreSession() {
      try {
        const currentProfile = await apiFetch(API.profile);
        if (active) {
          setProfile(currentProfile);
          setUser(currentProfile);
        }
      } catch {
        if (active) {
          setProfile(null);
          setUser(null);
        }
      } finally {
        if (active) setCheckingSession(false);
      }
    }
    restoreSession();
    return () => { active = false; };
  }, []);

  const applyControlStatus = useCallback((response) => {
    const next = mergeControlStatus(controlStatesRef.current, response);
    if (next === controlStatesRef.current) return;
    controlStatesRef.current = next;
    controlRevision.current++;
    if (response.status === "LOADING") pendingDeviceRef.current.add(response.deviceId);
    else pendingDeviceRef.current.delete(response.deviceId);
    setPendingDeviceIds(new Set(pendingDeviceRef.current));
    setControlErrors((current) => {
      const errors = { ...current };
      if (response.status === "FAILED") errors[response.deviceId] = "Chưa nhận được xác nhận từ thiết bị.";
      else delete errors[response.deviceId];
      return errors;
    });
    if (response.status === "ON" || response.status === "OFF") {
      confirmedDevicesRef.current.set(response.deviceId, { status: response.status, revision: controlRevision.current });
      setDashboard((current) => ({
        ...current,
        devices: current.devices.map((device) => device.id === response.deviceId ? { ...device, status: response.status } : device)
      }));
    }
    setHistoryRefresh((number) => number + 1);
  }, []);

  const loadDashboard = useCallback(async () => {
    const request = ++dashboardRequest.current;
    const revision = controlRevision.current;
    try {
      const data = await apiFetch(API.dashboard);
      if (request !== dashboardRequest.current) return;
      setDashboard({
        latestReadings: Array.isArray(data?.latestReadings) ? data.latestReadings : [],
        chartData: Array.isArray(data?.chartData) ? data.chartData : [],
        devices: Array.isArray(data?.devices) ? data.devices.map((device) => {
          const confirmed = confirmedDevicesRef.current.get(device.id);
          return confirmed?.revision > revision ? { ...device, status: confirmed.status } : device;
        }) : []
      });
      if (revision === controlRevision.current) {
        const pending = Array.isArray(data?.pendingControls) ? data.pendingControls : [];
        const ids = new Set(pending.map((item) => item.deviceId));
        let controls = new Map(controlStatesRef.current);
        for (const [id, control] of controls) {
          if (control.status === "LOADING" && !ids.has(id) && !pendingRequestRef.current.has(id)) controls.delete(id);
        }
        for (const control of pending) controls = mergeControlStatus(controls, control);
        controlStatesRef.current = controls;
        pendingDeviceRef.current = new Set([
          ...pendingRequestRef.current,
          ...Array.from(controls.values()).filter((control) => control.status === "LOADING").map((control) => control.deviceId)
        ]);
        setPendingDeviceIds(new Set(pendingDeviceRef.current));
      }
    } catch (error) {
      if (request === dashboardRequest.current) console.error("Không tải được Dashboard:", error);
    }
  }, []);

  useEffect(() => {
    if (!user) return undefined;
    loadDashboard();
    return () => { dashboardRequest.current++; };
  }, [user, loadDashboard]);

  useEffect(() => {
    if (!user) return undefined;
    let active = true;
    const client = new Client({ brokerURL: getWebSocketUrl(), reconnectDelay: 5000, debug: () => {} });
    client.onConnect = () => {
      if (!active) {
        void client.deactivate();
        return;
      }
      client.subscribe("/topic/sensors", ({ body }) => {
        if (!active) return;
        try {
          const reading = JSON.parse(body);
          const kind = sensorKind(reading.sensorName);
          if (kind && Number.isFinite(Number(reading.value))) {
            setDashboard((current) => ({
              ...current,
              latestReadings: [...current.latestReadings.filter((item) => sensorKind(item.sensorName) !== kind), reading],
              chartData: [...current.chartData, reading].slice(-50)
            }));
          }
          // setSensorRefresh((number) => number + 1); 
        } catch (error) {
          console.error("Sensor WS payload không hợp lệ:", error);
        }
      });
      client.subscribe("/topic/devices", ({ body }) => {
        if (!active) return;
        try {
          const device = JSON.parse(body);
          if (device.id === 4 && confirmedDevicesRef.current.get(device.id)?.status !== device.status) {
            setAlertRefresh((value) => value + 1);
          }
          controlRevision.current++;
          confirmedDevicesRef.current.set(device.id, { status: device.status, revision: controlRevision.current });
          setDashboard((current) => ({
            ...current,
            devices: current.devices.map((item) => item.id === device.id ? { ...item, status: device.status } : item)
          }));
        } catch (error) {
          console.error("Status WS payload không hợp lệ:", error);
        }
      });
      client.subscribe("/topic/status", ({ body }) => {
        if (!active) return;
        try {
          applyControlStatus(JSON.parse(body));
        } catch (error) {
          console.error("Control status WS payload không hợp lệ:", error);
        }
      });
      // Đồng bộ lại sau reconnect để không bỏ lỡ kết quả khi socket bị ngắt.
      loadDashboard();
      setAlertRefresh((value) => value + 1);
      setHistoryRefresh((number) => number + 1);
    };
    client.onStompError = (frame) => console.error("STOMP error:", frame.headers.message || frame.body);
    client.activate();
    return () => { active = false; void client.deactivate(); };
  }, [user, applyControlStatus, loadDashboard]);

  useEffect(() => {
    if (!user || view !== "sensors") return undefined;
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        const time = normalizeTimePrefix(sensorFilters.date);
        if (time === null) {
          setSensorError("Nhập theo thứ tự YYYY-MM-DD HH:mm:ss; có thể dừng ở bất kỳ vị trí nào.");
          return;
        }
        setSensorError("");
        const queryFilters = { search: sensorFilters.search, sensor: sensorFilters.sensor, time };
        const data = await apiFetch(`${API.sensors}?${pageQuery(sensorPage, sensorSize, queryFilters)}`, { signal: controller.signal });
        if (controller.signal.aborted) return;
        const totalPages = Math.max(1, Number(data?.totalPages) || 0);
        if (sensorPage > totalPages) {
          setSensorPage(totalPages);
          return;
        }
        setSensorResult({ content: Array.isArray(data?.content) ? data.content : [], totalPages });
      } catch (error) {
        if (error.name !== "AbortError" && !controller.signal.aborted) setSensorError(error.message);
      }
    }, sensorFilters.search || sensorFilters.date ? 300 : 0);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [user, view, sensorPage, sensorSize, sensorFilters, sensorRefresh]);

  useEffect(() => {
    if (!user || view !== "history") return undefined;
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        const time = normalizeTimePrefix(historyFilters.date);
        if (time === null) {
          setHistoryError("Nhập theo thứ tự YYYY-MM-DD HH:mm:ss; có thể dừng ở bất kỳ vị trí nào.");
          return;
        }
        setHistoryError("");
        const queryFilters = { deviceId: historyFilters.deviceId, action: historyFilters.action, status: historyFilters.status, time };
        const data = await apiFetch(`${API.history}?${pageQuery(historyPage, historySize, queryFilters)}`, { signal: controller.signal });
        if (controller.signal.aborted) return;
        const totalPages = Math.max(1, Number(data?.totalPages) || 0);
        if (historyPage > totalPages) {
          setHistoryPage(totalPages);
          return;
        }
        setHistoryResult({ content: Array.isArray(data?.content) ? data.content : [], totalPages });
      } catch (error) {
        if (error.name !== "AbortError" && !controller.signal.aborted) setHistoryError(error.message);
      }
    }, historyFilters.date ? 300 : 0);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [user, view, historyPage, historySize, historyFilters, historyRefresh]);

  useEffect(() => {
    if (!user || view !== "profile" || profile?.id === user.id) return undefined;
    const controller = new AbortController();
    apiFetch(API.profile, { signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setProfile(data); })
      .catch((error) => { if (error.name !== "AbortError") console.error("Không tải được Profile:", error); });
    return () => controller.abort();
  }, [user, view, profile]);

  async function handleLogin(username, password) {
    setLoginLoading(true);
    setLoginError("");
    try {
      const loggedInUser = await apiFetch(API.login, {
        method: "POST",
        body: JSON.stringify({ username, password })
      });
      setView("dashboard");
      setUser(loggedInUser);
    } catch (error) {
      setLoginError(error.message || "Không thể đăng nhập.");
    } finally {
      setLoginLoading(false);
    }
  }

  async function handleLogout() {
    try {
      await apiFetch(API.logout, { method: "POST" });
    } catch (error) {
      console.error("Không thể đăng xuất khỏi máy chủ:", error);
      alert("Không thể đăng xuất. Vui lòng thử lại.");
      return;
    }
    sessionGeneration.current++;
    dashboardRequest.current++;
    pendingDeviceRef.current.clear();
    pendingRequestRef.current.clear();
    controlStatesRef.current.clear();
    confirmedDevicesRef.current.clear();
    controlRevision.current++;
    setPendingDeviceIds(new Set());
    setControlErrors({});
    setDashboard(EMPTY_DASHBOARD);
    setProfile(null);
    setSensorResult(EMPTY_PAGE);
    setHistoryResult(EMPTY_PAGE);
    setSensorFilters({ search: "", sensor: "", date: "" });
    setSensorSearch("");
    setSensorFilterMode("");
    setHistoryFilters({ deviceId: "", action: "", status: "", date: "" });
    setSensorPage(1);
    setHistoryPage(1);
    setSensorError("");
    setHistoryError("");
    setUser(null);
    setView("dashboard");
  }

  async function handleControlDevice(device) {
    if (pendingDeviceRef.current.has(device.id)) return;
    const generation = sessionGeneration.current;
    const action = device.status === "ON" ? "OFF" : "ON";
    pendingDeviceRef.current.add(device.id);
    pendingRequestRef.current.add(device.id);
    controlRevision.current++;
    setControlErrors((current) => { const next = { ...current }; delete next[device.id]; return next; });
    setPendingDeviceIds(new Set(pendingDeviceRef.current));
    try {
      const result = await apiFetch(API.controlDevice(device.id), {
        method: "POST",
        body: JSON.stringify({ action })
      });
      if (generation === sessionGeneration.current) applyControlStatus(result);
    } catch (error) {
      if (generation === sessionGeneration.current) {
        console.error("Điều khiển device thất bại:", error);
        alert(error.message || "Không thể điều khiển thiết bị.");
        loadDashboard();
      }
    } finally {
      if (generation === sessionGeneration.current) {
        pendingRequestRef.current.delete(device.id);
        if (controlStatesRef.current.get(device.id)?.status !== "LOADING") pendingDeviceRef.current.delete(device.id);
        setPendingDeviceIds(new Set(pendingDeviceRef.current));
      }
    }
  }

  function selectView(nextView) {
    if (nextView === "dashboard" && view !== "dashboard") loadDashboard();
    setView(nextView);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function applySensorFilters(value = sensorSearch) {
    setSensorPage(1);
    setSensorFilters(sensorFilterMode === "time"
      ? { search: "", sensor: "", date: value }
      : { search: value, sensor: sensorFilterMode, date: "" });
  }

  function changeSensorSearch(value) {
    setSensorSearch(value);
    applySensorFilters(value);
  }

  function changeSensorFilterMode(mode) {
    // Chỉ chọn phạm vi; giữ kết quả hiện tại đến khi nhập hoặc bấm tìm kiếm.
    setSensorFilterMode(mode);
  }

  function changeHistoryFilter(key, value) {
    setHistoryPage(1);
    setHistoryFilters((current) => ({ ...current, [key]: value }));
  }

  if (checkingSession) {
    return <section className="login-screen"><p>Đang kiểm tra phiên đăng nhập...</p></section>;
  }
  if (!user) {
    return <LoginScreen onLogin={handleLogin} error={loginError} loading={loginLoading} />;
  }

  return (
    <div id="appShell" className="app-shell">
      <header className="topbar">
        <div className="topbar-left"><button className="brand" type="button" onClick={() => selectView("dashboard")}>IOT &amp; Ứng Dụng</button></div>
        <div className="user-menu"><span className="user-name">{user.fullname}</span><span className="avatar" aria-hidden="true" /></div>
      </header>
      <aside className="sidebar">
        <nav className="nav-list" aria-label="Điều hướng chính">
          {NAV_ITEMS.map((item) => (
            <button key={item.view} className={`nav-button${view === item.view ? " active" : ""}`} type="button" onClick={() => selectView(item.view)}>
              <i className={`fa-solid ${item.icon}`} /><span>{item.label}</span>
            </button>
          ))}
        </nav>
        <button className="logout-button" type="button" onClick={handleLogout}><i className="fa-solid fa-right-from-bracket" /><span>Đăng xuất</span></button>
      </aside>
      <main className="main">
        {view === "dashboard" && <DashboardPage dashboard={dashboard} pendingDeviceIds={pendingDeviceIds} controlErrors={controlErrors} onControlDevice={handleControlDevice} alertRefresh={alertRefresh} />}
        {view === "sensors" && <DataSensorPage
          search={sensorSearch}
          filterMode={sensorFilterMode}
          onSearchChange={changeSensorSearch}
          onFilterModeChange={changeSensorFilterMode}
          onRefresh={() => { applySensorFilters(); setSensorRefresh((number) => number + 1); }}
          result={sensorResult}
          error={sensorError}
          page={sensorPage}
          size={sensorSize}
          onPageChange={setSensorPage}
          onSizeChange={(size) => { setSensorSize(size); setSensorPage(1); }}
        />}
        {view === "history" && <ActionHistoryPage
          filters={historyFilters}
          devices={dashboard.devices}
          onFilterChange={changeHistoryFilter}
          onRefresh={() => setHistoryRefresh((number) => number + 1)}
          result={historyResult}
          error={historyError}
          page={historyPage}
          size={historySize}
          onPageChange={setHistoryPage}
          onSizeChange={(size) => { setHistorySize(size); setHistoryPage(1); }}
        />}
        {view === "profile" && <ProfilePage profile={profile} user={user} />}
      </main>
    </div>
  );
}
