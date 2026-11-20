import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useGuild } from "../context";
import { ShareButton } from "../components/Header";
import { DAYS, DEFAULT_FREQ_FILTER, DailyBars, FreqCurve, FreqFilters, Heatmap, MiniBars, PERCENT_AXIS } from "../components/Charts";
import { chName, solidColor } from "../colors";
import { chanceNow, endOf, heatFor, isOnline, lastSession, rankOf, slotsFor, userStats } from "../stats";
import { fmtDate, fmtDur, fmtFull, kst } from "../time";

export default function UserPage() {
  const { data, now, base } = useGuild();
  const { uid } = useParams();
  const p = data.people.find((x) => x.id === uid);
  const [filter, setFilter] = useState(DEFAULT_FREQ_FILTER);
  const back = (
    <Link className="back" to={base}>
      <span className="ar">←</span>
      {data.info.name} 타임라인
    </Link>
  );
  if (!p)
    return (
      <>
        {back}
        <div className="empty">이 서버에 이 사람의 접속 기록이 없어요.</div>
      </>
    );

  const st = userStats(p, now);
  const on = isOnline(p);
  const last = lastSession(p);
  const lifetime = p.sessions.reduce((acc, s) => acc + (endOf(s, now) - s.s), 0);
  const totalRank = rankOf(data.people, p, "total", "all", now);
  const bounceRank = rankOf(data.people, p, "bounce", "all", now);
  const topCh = st.channels;
  const chTotal = topCh.reduce((acc, [, v]) => acc + v, 0) || 1;
  const b = st.bounce;
  const today = kst(now).weekday;
  const focus = filter.weekday === "today" ? today : null;
  const freq = slotsFor([p], filter.period, focus, now);
  const heat = heatFor([p], filter.period, now);
  const chance = chanceNow(p, now);

  return (
    <>
      {back}
      <div className="uhead">
        <img src={p.avatar} alt="" />
        <div>
          <div className="nm">
            {p.name}
            <span className={`pill ${on ? "" : "off"}`}>{on ? `접속 중 · ${chName(last.ch)}` : "오프라인"}</span>
            <ShareButton />
          </div>
          <div className="st">
            {data.info.name} · 마지막 접속 {on ? "지금" : last ? fmtFull(endOf(last, now)) : "-"}
          </div>
        </div>
      </div>
      <div className="summary">
        <div className="stat">
          <div className="k">
            누적 총 접속{totalRank && <span className="rankpill">서버 {totalRank.no}위 / {totalRank.of}명</span>}
          </div>
          <div className="v">{fmtDur(lifetime)}</div>
          <div className="k small">{p.sessions[0] ? `${fmtDate(p.sessions[0].s)}부터 기록` : ""}</div>
        </div>
        <div className="stat">
          <div className="k">출석률 (최근 30일)</div>
          <div className="v">{Math.round((st.days / 30) * 100)}%</div>
          <div className="k small">30일 중 {st.days}일</div>
        </div>
        <div className="stat">
          <div className="k">지금 들어올 확률</div>
          <div className="v">{Math.round(chance.chance * 100)}%</div>
          <div className="k small">
            {DAYS[today]}요일 · {chance.days}번 중 {chance.hits}번
          </div>
        </div>
        <div className="stat">
          <div className="k">평균 접속 길이 (최근 30일)</div>
          <div className="v">{fmtDur(st.avg)}</div>
        </div>
        <div className="stat">
          <div className="k">가장 많이 간 채널 (최근 30일)</div>
          <div className="v">
            {topCh[0] ? (
              <>
                <span className="chdot" style={{ background: solidColor(topCh[0][0]) }} />
                {chName(topCh[0][0])}
              </>
            ) : (
              "-"
            )}
          </div>
        </div>
        <div className="stat bounce">
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="k">
              들낙 · 5분 미만{bounceRank && <span className="rankpill">{bounceRank.no}위</span>}
            </div>
            <div className="v">{Math.round(b.ratio * 100)}%</div>
            <div className="k small">
              하루 {b.perDay.toFixed(1)}번 · {b.count}/{b.all}회
            </div>
          </div>
          <div className="minibox">
            <MiniBars values={b.daily} />
            <div className="k tiny">최근 14일</div>
          </div>
        </div>
      </div>
      <div className="card">
        <h5>
          시간대별 접속 빈도 <FreqFilters value={filter} now={now} onChange={setFilter} />
          <span>그 시간에 접속해 있던 날의 비율(%)</span>
        </h5>
        <FreqCurve
          slots={freq.values}
          now={now}
          height={240}
          axis={PERCENT_AXIS}
          format={(v, i) => `${Math.round(v * 100)}% (${freq.dayCounts[i]}일 중 ${Math.round(v * freq.dayCounts[i])}일)`}
        />
        <Heatmap heat={heat.heat} cellHeight={22} now={now} format={(d, h, v) => `${heat.cellDays[d][h]}번 중 ${v}번`} />
      </div>
      <div className="grid2">
        <div className="card">
          <h5>
            일별 접속 시간 <span>최근 14일</span>
          </h5>
          <DailyBars values={st.daily} now={now} height={180} />
          <h5 style={{ marginTop: 14 }}>자주 간 채널</h5>
          {topCh.slice(0, 4).map(([c, v]) => (
            <div key={c} className="chrow">
              <b>{chName(c)}</b>
              <div className="bx">
                <i style={{ width: `${(v / chTotal) * 100}%`, background: solidColor(c) }} />
              </div>
              <span>{Math.round((v / chTotal) * 100)}%</span>
            </div>
          ))}
          {topCh.length === 0 && <div className="note">최근 30일 기록 없음</div>}
        </div>
        <div className="card scroll">
          <h5>최근 접속</h5>
          <div className="slist">
            {[...p.sessions]
              .reverse()
              .slice(0, 14)
              .map((s) => (
                <div key={s.id}>
                  <i style={{ background: solidColor(s.ch) }} />
                  <span className="t">{fmtFull(s.s)}</span>
                  {chName(s.ch)}
                  <span className="d">{s.online ? "접속 중" : fmtDur(s.e - s.s)}</span>
                </div>
              ))}
          </div>
        </div>
      </div>
    </>
  );
}
