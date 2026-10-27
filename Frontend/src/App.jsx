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
// hamf dung de tạo tham số URL
function pageQuery(page, size, filters) {
  const params = new URLSearchParams({ page: String(page - 1), size: String(size) });
  Object.entries(filters).forEach(([key, value]) => {
    if (String(value).trim()) params.set(key, String(value).trim());
  });
  return params.toString();
  //vidu: page=1&size=20&search=&sensor=&date=2023-12-25T10:30:00
}

export default function App() {
  // XÁC THỰC & ĐIỀU HƯỚNG GIAO DIỆN
  const [user, setUser] = useState(null); // Thông tin tài khoản đăng nhập hiện tại (null nếu chưa đăng nhập)
  const [checkingSession, setCheckingSession] = useState(true); // Trạng thái đang kiểm tra phiên đăng nhập cũ lúc khởi động app
  const [loginLoading, setLoginLoading] = useState(false); // Trạng thái đang chờ gọi API đăng nhập (hiển thị loading trên nút)
  const [loginError, setLoginError] = useState(""); // Thông báo lỗi khi đăng nhập thất bại
  const [view, setView] = useState("dashboard"); // Tab màn hình hiện tại ("dashboard" | "sensors" | "history" | "profile")
  const [dashboard, setDashboard] = useState(EMPTY_DASHBOARD); // Dữ liệu trang chủ: số đo cảm biến mới nhất, mảng vẽ biểu đồ, danh sách thiết bị
  const [alertRefresh, setAlertRefresh] = useState(0); // Số đếm tăng dần (+1) để kích hoạt vẽ lại biểu đồ thống kê cảnh báo
  const [profile, setProfile] = useState(null); // Dữ liệu thông tin cá nhân chi tiết (mã sinh viên, email, liên kết mạng xã hội...)

  // ĐIỀU KHIỂN THIẾT BỊ & XỬ LÝ ĐỒNG BỘ NGẦM
  const [pendingDeviceIds, setPendingDeviceIds] = useState(new Set()); // Tập hợp ID các thiết bị đang chờ phản hồi từ phần cứng (hiện spinner xoay)
  const pendingDeviceRef = useRef(new Set()); // Lưu ngầm danh sách ID thiết bị đang chờ (tránh lỗi bất đồng bộ stale closure)
  const pendingRequestRef = useRef(new Set()); // Lưu ngầm các request API bật/tắt thiết bị đang gửi qua mạng
  const controlStatesRef = useRef(new Map()); // Lưu ngầm trạng thái điều khiển chi tiết của từng thiết bị (LOADING, ON, OFF, FAILED)
  const confirmedDevicesRef = useRef(new Map()); // Lưu trạng thái thiết bị đã được server xác nhận thành công kèm số phiên bản
  const controlRevision = useRef(0); // Đánh số phiên bản lệnh điều khiển (tránh việc dữ liệu cũ đè lên dữ liệu mới hơn)
  const [controlErrors, setControlErrors] = useState({}); // Lưu câu thông báo lỗi cho từng thiết bị nếu gửi lệnh thất bại
  const dashboardRequest = useRef(0); // Đếm số thứ tự request gọi API dashboard (chống lỗi tranh chấp Race Condition khi mạng chậm)
  const sessionGeneration = useRef(0); // Đánh dấu thế hệ phiên đăng nhập (bỏ qua các phản hồi mạng của phiên cũ sau khi logout)

  // BẢNG DỮ LIỆU CẢM BIẾN (DATA SENSOR)
  const [sensorFilters, setSensorFilters] = useState({ search: "", mode: "" }); // Bộ lọc chính thức gửi lên API (từ khóa search và chế độ lọc mode)
  const [sensorSearch, setSensorSearch] = useState(""); // Giá trị người dùng đang nhập trong ô tìm kiếm cảm biến
  const [sensorFilterMode, setSensorFilterMode] = useState(""); // Chế độ lọc đang chọn trong dropdown (Tất cả, Nhiệt độ, Độ ẩm, Độ sáng, Theo thời gian)
  const [sensorPage, setSensorPage] = useState(1); // Trang hiện tại của bảng cảm biến (mặc định trang 1)
  const [sensorSize, setSensorSize] = useState(20); // Số dòng hiển thị trên mỗi trang bảng cảm biến (mặc định 20 dòng)
  const [sensorResult, setSensorResult] = useState(EMPTY_PAGE); // Kết quả trả về từ API cảm biến: mảng content (các dòng dữ liệu) và totalPages
  const [sensorRefresh, setSensorRefresh] = useState(0); // Số đếm tăng dần (+1) để kích hoạt tải lại bảng cảm biến khi bấm nút Tìm kiếm
  const [sensorError, setSensorError] = useState(""); // Thông báo lỗi nếu gọi API bảng cảm biến thất bại

  // BẢNG LỊCH SỬ THAO TÁC (ACTION HISTORY)     
  const [historyFilters, setHistoryFilters] = useState({ deviceId: "", action: "", status: "", date: "" }); // Bộ lọc lịch sử: thiết bị, hành động, trạng thái, thời gian
  const [historyPage, setHistoryPage] = useState(1); // Trang hiện tại của bảng lịch sử (mặc định trang 1)
  const [historySize, setHistorySize] = useState(20); // Số dòng hiển thị trên mỗi trang bảng lịch sử (mặc định 20 dòng)
  const [historyResult, setHistoryResult] = useState(EMPTY_PAGE); // Kết quả trả về từ API lịch sử: mảng content và totalPages
  const [historyRefresh, setHistoryRefresh] = useState(0); // Số đếm tăng dần (+1) để kích hoạt tải lại bảng lịch sử
  const [historyError, setHistoryError] = useState(""); // Thông báo lỗi nếu gọi API bảng lịch sử thất bại

  // Tự động kiểm tra và khôi phục phiên đăng nhập cũ (nếu có cookie/session) khi vừa mở trang hoặc F5
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

  // Cập nhật trạng thái điều khiển thiết bị (LOADING -> ON/OFF/FAILED) vào bộ nhớ ngầm và giao diện
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

  // Tải toàn bộ dữ liệu trang chủ Dashboard (chỉ số cảm biến mới nhất, dữ liệu biểu đồ, danh sách thiết bị)
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

  // Effect 2: Tự động tải dữ liệu Dashboard lần đầu tiên ngay sau khi người dùng đăng nhập thành công
  useEffect(() => {
    if (!user) return undefined;
    loadDashboard();
    return () => { dashboardRequest.current++; };
  }, [user, loadDashboard]);

  // Effect 3: Thiết lập kết nối thời gian thực WebSocket (STOMP) để nhận dữ liệu cảm biến và thiết bị tức thì
  useEffect(() => {
    if (!user) return undefined;
    let active = true;
    const client = new Client({ brokerURL: getWebSocketUrl(), reconnectDelay: 5000, debug: () => { } });
    client.onConnect = () => {
      if (!active) {
        void client.deactivate();
        return;
      }
      // Đăng ký nhận dữ liệu số đo cảm biến mới từ topic /topic/sensors
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
        } catch (error) {
          console.error("Sensor WS payload không hợp lệ:", error);
        }
      });
      // Đăng ký nhận trạng thái cập nhật thiết bị (bật/tắt) từ topic /topic/devices
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
      // Đăng ký nhận trạng thái phản hồi lệnh điều khiển từ topic /topic/status
      client.subscribe("/topic/status", ({ body }) => {
        if (!active) return;
        try {
          applyControlStatus(JSON.parse(body));
        } catch (error) {
          console.error("Control status WS payload không hợp lệ:", error);
        }
      });
      // Đồng bộ lại dữ liệu sau khi kết nối lại (reconnect) để không bỏ lỡ thông tin khi mất mạng tạm thời
      loadDashboard();
      setAlertRefresh((value) => value + 1);
      setHistoryRefresh((number) => number + 1);
    };
    client.onStompError = (frame) => console.error("STOMP error:", frame.headers.message || frame.body);
    client.activate();
    return () => { active = false; void client.deactivate(); };
  }, [user, applyControlStatus, loadDashboard]);

  // Effect 4: Gọi API tải dữ liệu bảng cảm biến (/api/data-sensor) mỗi khi chuyển trang, đổi số dòng hoặc thay đổi bộ lọc tìm kiếm
  useEffect(() => {
    if (!user || view !== "sensors") return undefined;
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        let queryFilters;
        if (sensorFilters.mode === "time") {
          const time = normalizeTimePrefix(sensorFilters.search);
          if (time === null) {
            setSensorError("Nhập theo thứ tự YYYY-MM-DD HH:mm:ss; có thể dừng ở bất kỳ vị trí nào.");
            return;
          }
          setSensorError("");
          queryFilters = { search: time, mode: "time" };
        } else {
          setSensorError("");
          queryFilters = { search: sensorFilters.search, mode: sensorFilters.mode };
        }
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
    }, sensorFilters.search ? 300 : 0);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [user, view, sensorPage, sensorSize, sensorFilters, sensorRefresh]);

  // Effect 5: Gọi API tải dữ liệu bảng lịch sử thao tác (/api/history) mỗi khi đổi trang hoặc thay đổi bộ lọc
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

  // Effect 6: Tải dữ liệu thông tin cá nhân (/api/profile) khi người dùng mở tab "Profile"
  useEffect(() => {
    if (!user || view !== "profile" || profile?.id === user.id) return undefined;
    const controller = new AbortController();
    apiFetch(API.profile, { signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setProfile(data); })
      .catch((error) => { if (error.name !== "AbortError") console.error("Không tải được Profile:", error); });
    return () => controller.abort();
  }, [user, view, profile]);

  // Hàm: Xử lý đăng nhập tài khoản bằng username và password
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

  // Hàm: Xử lý đăng xuất, gọi API hủy phiên và xóa sạch toàn bộ State trong RAM về mặc định
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

  // Hàm: Xử lý khi người dùng bấm công tắc bật/tắt một thiết bị
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

  // Hàm: Chuyển đổi qua lại giữa các tab màn hình (Dashboard, Sensors, History, Profile)
  function selectView(nextView) {
    if (nextView === "dashboard" && view !== "dashboard") loadDashboard();
    setView(nextView);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  // Hàm: Thiết lập bộ lọc tìm kiếm cho bảng cảm biến và tự động quay về trang 1
  function applySensorFilters(value = sensorSearch) {
    setSensorPage(1);
    setSensorFilters({ search: value, mode: sensorFilterMode });
  }

  // Hàm: Cập nhật giá trị khi người dùng gõ vào ô tìm kiếm bảng cảm biến
  function changeSensorSearch(value) {
    setSensorSearch(value);
    applySensorFilters(value);
  }

  // Hàm: Cập nhật chế độ lọc cảm biến khi người dùng chọn trong menu dropdown
  function changeSensorFilterMode(mode) {
    // Chỉ chọn phạm vi; giữ kết quả hiện tại đến khi nhập hoặc bấm tìm kiếm.
    setSensorFilterMode(mode);
  }

  // Hàm: Cập nhật tiêu chí lọc của bảng lịch sử thao tác (thiết bị, hành động, trạng thái, ngày)
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
