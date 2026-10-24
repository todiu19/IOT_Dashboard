import "./ProfilePage.css";

function safeUrl(value) {
  if (!value) return null;
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

function ProfileLink({ label, value }) {
  const href = safeUrl(value);
  return (
    <div className="profile-row">
      <strong>{label}</strong>
      {href ? <a href={href} target="_blank" rel="noreferrer">{value}</a> : <span>{value || "—"}</span>}
    </div>
  );
}

export default function ProfilePage({ profile, user }) {
  return (
    <section className="page">
      <header className="page-head"><h1>Profile</h1></header>
      <article className="card profile-grid">
        <aside className="profile-aside">
          <span className="profile-avatar"><i className="fa-solid fa-user" /></span>
          <h2>{profile?.username || user.username}</h2>
        </aside>
        <div>
          <div className="profile-row"><strong>Họ và tên</strong><span>{profile?.fullname || user.fullname || "—"}</span></div>
          <div className="profile-row"><strong>MSV</strong><span>{profile?.msv || "—"}</span></div>
          <div className="profile-row"><strong>Email</strong><span>{profile?.email || user.email || "—"}</span></div>
          <ProfileLink label="GitHub" value={profile?.github} />
          <ProfileLink label="Postman" value={profile?.postman} />
          <ProfileLink label="Figma" value={profile?.figma} />
          <ProfileLink label="Pdf" value={profile?.pdf} />
        </div>
      </article>
    </section>
  );
}
