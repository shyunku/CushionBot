import { useEffect, useRef, useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { useMobile } from "../useMobile";
import { useGuild } from "../context";
import { concurrency } from "../stats";
import { sod } from "../time";
import GuildIcon from "./GuildIcon";
import Sparkline from "./Sparkline";

export function ShareButton() {
  const { share } = useGuild();
  return (
    <button className="btn sharebtn" onClick={share}>
      🔗 공유
    </button>
  );
}

/** On phones the server name opens a list of servers (the icon rail is hidden there). */
function ServerPicker() {
  const { data, guilds, guildId } = useGuild();
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: PointerEvent) => {
      if (!box.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", close);
    return () => document.removeEventListener("pointerdown", close);
  }, [open]);
  return (
    <div className="picker" ref={box}>
      <button className="title" onClick={() => setOpen(!open)}>
        <GuildIcon name={data.info.name} url={data.info.iconUrl} size={32} />
        {data.info.name}
        <span className="car">▾</span>
      </button>
      {open && (
        <div className="plist">
          {(guilds ?? []).map((g) => (
            <Link key={g.id} to={`/servers/${g.id}`} className={`it ${g.id === guildId ? "sel" : ""}`} onClick={() => setOpen(false)}>
              <GuildIcon name={g.name} url={g.iconUrl} size={24} />
              <span className="nm">{g.name}</span>
              {g.online > 0 && <span className="cnt">{g.online}명 접속</span>}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

export default function Header() {
  const { data, now, base, shared } = useGuild();
  const mobile = useMobile();
  const points = concurrency(data.people, sod(now), now, 96, now);
  return (
    <>
      <div className="head">
        {mobile && !shared ? (
          <ServerPicker />
        ) : (
          <div className="title">
            <GuildIcon name={data.info.name} url={data.info.iconUrl} size={32} />
            {data.info.name}
          </div>
        )}
        <span className="meta">
          멤버 {data.info.memberCount}명 · 기록된 사람 {data.people.length}명
        </span>
        <ShareButton />
        {shared && <span className="pill off">공유된 서버만 보기</span>}
        <div className="spark">
          오늘 접속 추이 <Sparkline values={points} width={160} height={32} />
        </div>
      </div>
      <div className="stabs">
        <NavLink end to={base} className={({ isActive }) => (isActive ? "sel" : "")}>
          타임라인
        </NavLink>
        <NavLink to={`${base}/rank`} className={({ isActive }) => (isActive ? "sel" : "")}>
          랭킹
        </NavLink>
        <NavLink to={`${base}/stats`} className={({ isActive }) => (isActive ? "sel" : "")}>
          통계
        </NavLink>
      </div>
    </>
  );
}
