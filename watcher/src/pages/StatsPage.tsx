import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useGuild } from "../context";
import Header from "../components/Header";
import { DAYS, DEFAULT_FREQ_FILTER, FreqCurve, FreqFilters, Grass, Heatmap, countAxis } from "../components/Charts";
import { chanceNow, heatFor, isOnline, leaveChance, serverStats, slotsFor } from "../stats";
import type { Person } from "../types";
import { MIN, fmtDur, fmtHM, kst, sod } from "../time";

export default function StatsPage() {
  const { data, now, base } = useGuild();
  const [filter, setFilter] = useState(DEFAULT_FREQ_FILTER);
  // recompute at most once a minute; the curves do not move faster than that
  const minute = Math.floor(now / MIN);
  const st = useMemo(() => serverStats(data.people, minute * MIN), [data.people, minute]);
  const empty = st.total === 0;
  const today = kst(now).weekday;
  const focus = filter.weekday === "today" ? today : null;
  const freq = useMemo(() => slotsFor(data.people, filter.period, focus, minute * MIN), [data.people, filter.period, focus, minute]);
  const heat = useMemo(() => {
    const h = heatFor(data.people, filter.period, minute * MIN);
    return h.heat.map((row, d) => row.map((v, hour) => (h.cellDays[d][hour] ? v / h.cellDays[d][hour] : 0)));
  }, [data.people, filter.period, minute]);
  // who is likely to come in (people not on now) and to leave soon (people on now)
  const enter = useMemo(
    () =>
      data.people
        .filter((p) => !isOnline(p))
        .map((p) => ({ p, ...chanceNow(p, minute * MIN) }))
        .filter((x) => x.chance > 0)
        .sort((a, b) => b.chance - a.chance)
        .slice(0, 10),
    [data.people, minute],
  );
  const leave = useMemo(
    () =>
      data.people
        .filter(isOnline)
        .map((p) => ({ p, ...leaveChance(p, minute * MIN) }))
        .filter((x) => x.days > 0)
        .sort((a, b) => b.chance - a.chance)
        .slice(0, 10),
    [data.people, minute],
  );
  const chanceRows = (rows: { p: Person; chance: number }[]) =>
    rows.map((x, i) => (
      <Link key={x.p.id} className="cr" to={`${base}/users/${x.p.id}`}>
        <span className="no">{i + 1}</span>
        <img src={x.p.avatar} alt="" />
        <span className="nm">{x.p.name}</span>
        <span className="bar2">
          <i style={{ width: `${x.chance * 100}%` }} />
        </span>
        <span className="val">{Math.round(x.chance * 100)}%</span>
      </Link>
    ));

  return (
    <>
      <Header />
      <div className="summary">
        <div className="stat">
          <div className="k">최근 30일 총 접속</div>
          <div className="v">{fmtDur(st.total)}</div>
        </div>
        <div className="stat">
          <div className="k">하루 평균 접속 인원</div>
          <div className="v">{st.avgPeople.toFixed(1)}명</div>
        </div>
        <div className="stat">
          <div className="k">가장 붐비는 시간대</div>
          <div className="v">{empty ? "-" : `${fmtHM(sod(now) + st.peakSlot * 10 * MIN)} 전후`}</div>
        </div>
        <div className="stat">
          <div className="k">가장 붐비는 요일</div>
          <div className="v">{empty ? "-" : `${DAYS[st.peakDay]}요일`}</div>
        </div>
      </div>
      <div className="card">
        <h5>
          시간대별 접속 빈도 · 서버 전체 <FreqFilters value={filter} now={now} onChange={setFilter} />
          <span>그 시각 평균 동시 접속자 수(명)</span>
        </h5>
        <FreqCurve slots={freq.values} now={now} height={200} axis={countAxis(Math.max(...freq.values))} format={(v) => `평균 ${v.toFixed(1)}명`} />
        <Heatmap heat={heat} cellHeight={22} now={now} format={(_, __, v) => `평균 ${v.toFixed(1)}명`} />
      </div>
      <div className="card grasscard">
        <h5>
          날짜별 잔디 <span>진할수록 그날 총 접속 시간이 김</span>
        </h5>
        <Grass days={st.grass} />
      </div>
      <div className="chancerow">
        <div className="card chance">
          <h5>
            지금 들어올 확률 <span>{DAYS[today]}요일</span>
          </h5>
          {chanceRows(enter)}
          {enter.length === 0 && <div className="note">이 시간대에 들어올 만한 사람이 없음</div>}
        </div>
        <div className="card chance">
          <h5>
            지금 나갈 확률 <span>30분 안 · 접속 중인 사람</span>
          </h5>
          {chanceRows(leave)}
          {leave.length === 0 && <div className="note">지금 접속 중인 사람이 없거나 기록이 부족함</div>}
        </div>
      </div>
    </>
  );
}
