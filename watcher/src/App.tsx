import { useCallback, useEffect, useMemo, useState } from "react";
import { Navigate, Route, Routes, useLocation, useParams } from "react-router-dom";
import { useGuildData, useGuilds } from "./api";
import { GuildContext, useNow } from "./context";
import Rail from "./components/Rail";
import Skeleton from "./components/Skeleton";
import TimelinePage from "./pages/TimelinePage";
import RankPage from "./pages/RankPage";
import UserPage from "./pages/UserPage";

const LAST_GUILD = "watcher.lastGuild";

function readLastGuild() {
  try {
    return localStorage.getItem(LAST_GUILD);
  } catch {
    return null;
  }
}

function Home() {
  const { guilds, error } = useGuilds();
  if (error) return <div className="fatal">서버 목록을 불러오지 못했어요. ({error})</div>;
  if (!guilds) return <div className="app"><div className="rail" /><div className="main"><div className="content"><Skeleton /></div></div></div>;
  if (guilds.length === 0) return <div className="fatal">기록된 서버가 없어요.</div>;
  const last = readLastGuild();
  const target = guilds.find((g) => g.id === last) ?? guilds[0];
  return <Navigate to={`/servers/${target.id}`} replace />;
}

function GuildShell({ shared }: { shared: boolean }) {
  const { gid = "" } = useParams();
  const location = useLocation();
  const { guilds } = useGuilds();
  const { data, error } = useGuildData(gid);
  const now = useNow(data?.clockOffset ?? 0, 5000);
  const [toast, setToast] = useState<string | null>(null);
  const base = shared ? `/channel/${gid}` : `/servers/${gid}`;

  useEffect(() => {
    if (shared) return;
    try {
      localStorage.setItem(LAST_GUILD, gid);
    } catch {
      /* storage unavailable */
    }
  }, [gid, shared]);

  const share = useCallback(() => {
    const sub = location.pathname.slice(base.length);
    const url = `${window.location.origin}/channel/${gid}${sub}`;
    const done = () => {
      setToast("링크를 복사했어요");
      window.setTimeout(() => setToast(null), 1600);
    };
    // navigator.clipboard only exists on HTTPS/localhost; the dashboard is usually served over plain HTTP
    const legacyCopy = () => {
      const ta = document.createElement("textarea");
      ta.value = url;
      ta.setAttribute("readonly", "");
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      let ok = false;
      try {
        ok = document.execCommand("copy");
      } catch {
        ok = false;
      }
      ta.remove();
      if (ok) done();
      else window.prompt("링크", url);
    };
    if (navigator.clipboard?.writeText && window.isSecureContext) navigator.clipboard.writeText(url).then(done, legacyCopy);
    else legacyCopy();
  }, [location.pathname, base, gid]);

  const guildSummary = guilds?.find((g) => g.id === gid) ?? null;
  const ctx = useMemo(() => (data ? { guildId: gid, data, now, base, shared, guildSummary, share } : null), [gid, data, now, base, shared, guildSummary, share]);

  return (
    <div className="app">
      {!shared && <Rail guilds={guilds} selected={gid} />}
      <div className="main">
        <div className="content">
          {error && !data ? (
            <div className="fatal">이 서버의 기록을 불러오지 못했어요. ({error})</div>
          ) : !ctx ? (
            <Skeleton />
          ) : (
            <GuildContext.Provider value={ctx}>
              <Routes>
                <Route index element={<TimelinePage />} />
                <Route path="rank" element={<RankPage />} />
                <Route path="rank/:metric" element={<RankPage />} />
                <Route path="users/:uid" element={<UserPage />} />
                <Route path="*" element={<Navigate to={base} replace />} />
              </Routes>
            </GuildContext.Provider>
          )}
        </div>
      </div>
      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/servers/:gid/*" element={<GuildShell shared={false} />} />
      <Route path="/channel/:gid/*" element={<GuildShell shared />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
