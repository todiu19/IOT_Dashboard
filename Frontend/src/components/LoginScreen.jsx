import { useState } from "react";

export default function LoginScreen({ onLogin, error, loading }) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  function handleSubmit(event) {
    event.preventDefault();
    onLogin(username.trim(), password);
  }

  return (
    <section className="login-screen">
      <div className="login-card">
        <h1 className="login-brand">IOT &amp; Ứng dụng</h1>
        <p className="login-title">Đăng nhập hệ thống</p>
        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <label className="form-group">
            <span className="form-label">Tên đăng nhập</span>
            <input className="control" name="username" autoComplete="username" placeholder="Nhập username" value={username} onChange={(event) => setUsername(event.target.value)} required />
          </label>
          <label className="form-group">
            <span className="form-label">Mật khẩu</span>
            <span className="password-field">
              <input className="control" name="password" type={showPassword ? "text" : "password"} autoComplete="current-password" placeholder="Nhập password" value={password} onChange={(event) => setPassword(event.target.value)} required />
              <button className="password-toggle" type="button" aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"} onClick={() => setShowPassword((value) => !value)}>
                <i className={`fa-solid fa-eye${showPassword ? "-slash" : ""}`} />
              </button>
            </span>
          </label>
          {error && <p className="login-error" role="alert">{error}</p>}
          <button className="primary-button login-submit" type="submit" disabled={loading}>{loading ? "Đang đăng nhập..." : "Đăng nhập"}</button>
        </form>
      </div>
    </section>
  );
}
