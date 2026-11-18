import { NavLink } from "react-router-dom";
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

export default function Header() {
  const { data, now, base, shared } = useGuild();
  const points = concurrency(data.people, sod(now), now, 96, now);
  return (
    <>
      <div className="head">
        <div className="title">
          <GuildIcon name={data.info.name} url={data.info.iconUrl} size={32} />
          {data.info.name}
        </div>
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
