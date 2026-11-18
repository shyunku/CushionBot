import { useMemo } from "react";
import { useGuild } from "../context";
import Header from "../components/Header";
import { DAYS, FreqCurve, Grass, Heatmap } from "../components/Charts";
import { serverStats } from "../stats";
import { MIN, fmtDur, fmtHM, sod } from "../time";

export default function StatsPage() {
  const { data, now } = useGuild();
  // recompute at most once a minute; the curves do not move faster than that
  const minute = Math.floor(now / MIN);
  const st = useMemo(() => serverStats(data.people, minute * MIN), [data.people, minute]);
  const empty = st.total === 0;

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
          시간대별 접속 빈도 · 서버 전체 <span>최근 30일 · 그 시각 평균 동시 접속자 수</span>
        </h5>
        <FreqCurve slots={st.slots} now={now} height={200} format={(v) => `평균 ${v.toFixed(1)}명`} />
        <Heatmap heat={st.heat} cellHeight={15} format={(_, __, v) => `평균 ${v.toFixed(1)}명`} />
      </div>
      <div className="card">
        <h5>
          날짜별 잔디 <span>최근 1년 · 진할수록 그날 총 접속 시간이 김</span>
        </h5>
        <Grass days={st.grass} />
      </div>
    </>
  );
}
