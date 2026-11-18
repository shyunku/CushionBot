import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useGuild } from "../context";
import Header from "../components/Header";
import { BOUNCE_MIN_SESSIONS, ranked, type MetricKey, type Period } from "../stats";
import { fmtDur } from "../time";

export const METRICS: { key: MetricKey; name: string; fmt: (v: number) => string; note?: string }[] = [
  { key: "total", name: "누적 접속 시간", fmt: fmtDur },
  { key: "count", name: "접속 횟수", fmt: (v) => `${v}회` },
  { key: "days", name: "접속한 날", fmt: (v) => `${v}일` },
  { key: "avg", name: "평균 접속 길이", fmt: fmtDur },
  { key: "longest", name: "최장 연속 접속", fmt: fmtDur },
  { key: "bounce", name: "들낙률", fmt: (v) => `${Math.round(v * 100)}%`, note: `사람 있는 채널에 ${BOUNCE_MIN_SESSIONS}번 이상 들어간 사람만 · 그중 5분 안에 나간 비율` },
];

const PERIODS: [Period, string][] = [
  ["all", "누적"],
  ["30d", "최근 30일"],
  ["7d", "최근 7일"],
];

export default function RankPage() {
  const { data, now, base } = useGuild();
  const { metric } = useParams();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const period: Period = PERIODS.some(([k]) => k === params.get("p")) ? (params.get("p") as Period) : "all";
  const setPeriod = (p: Period) => setParams(p === "all" ? {} : { p }, { replace: true });
  const qs = period === "all" ? "" : `?p=${period}`;
  const open = (id: string) => navigate(`${base}/users/${id}`);
  const full = METRICS.find((m) => m.key === metric);

  const top = (
    <div className="ctrl">
      <div className="seg">
        {PERIODS.map(([k, n]) => (
          <button key={k} className={period === k ? "sel" : ""} onClick={() => setPeriod(k)}>
            {n}
          </button>
        ))}
      </div>
      <span className="range">
        {data.info.name} · 기록된 {data.people.length}명
      </span>
    </div>
  );

  if (full) {
    const r = ranked(data.people, full.key, period, now);
    const max = r[0]?.v || 1;
    return (
      <>
        <Header />
        {top}
        <div className="card lb" style={{ flex: 1, overflow: "auto" }}>
          <h5>
            <Link className="back" to={`${base}/rank${qs}`}>
              <span className="ar">←</span>리더보드
            </Link>
            <span className="fulltitle">{full.name} 전체 랭킹</span>
            <span>
              {r.length}명{full.note ? ` · ${full.note}` : ""}
            </span>
          </h5>
          {r.map((x, i) => (
            <div key={x.p.id} className="r" onClick={() => open(x.p.id)}>
              <span className={`no ${i < 3 ? "m" + (i + 1) : ""}`}>{i + 1}</span>
              <img src={x.p.avatar} alt="" />
              <span className="nm" style={{ minWidth: 120 }}>
                {x.p.name}
              </span>
              <span className="bar2">
                <i style={{ width: `${(x.v / max) * 100}%` }} />
              </span>
              <span className="val" style={{ width: 110 }}>
                {full.fmt(x.v)}
              </span>
            </div>
          ))}
          {r.length === 0 && <div className="note">기록 없음</div>}
        </div>
      </>
    );
  }

  return (
    <>
      <Header />
      {top}
      <div className="boards">
        {METRICS.map((m) => {
          const all = ranked(data.people, m.key, period, now);
          const r = all.slice(0, 5);
          const max = r[0]?.v || 1;
          return (
            <div key={m.key} className="card lb">
              <h5>
                {m.name}
                {all.length > 5 && (
                  <Link className="more" to={`${base}/rank/${m.key}${qs}`}>
                    더보기 ({all.length}명) →
                  </Link>
                )}
              </h5>
              {r.map((x, i) => (
                <div key={x.p.id} className="r" onClick={() => open(x.p.id)}>
                  <span className={`no ${i < 3 ? "m" + (i + 1) : ""}`}>{i + 1}</span>
                  <img src={x.p.avatar} alt="" />
                  <span className="nm">{x.p.name}</span>
                  <span className="bar2">
                    <i style={{ width: `${(x.v / max) * 100}%` }} />
                  </span>
                  <span className="val">{m.fmt(x.v)}</span>
                </div>
              ))}
              {r.length === 0 && <div className="note">기록 없음</div>}
              {m.note && <div className="note">{m.note}</div>}
            </div>
          );
        })}
      </div>
    </>
  );
}
