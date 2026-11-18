import { useCallback, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useGuild, useNow } from "../context";
import Header from "../components/Header";
import Timeline, { MAX_SPAN, MIN_SPAN, SECONDS_SPAN } from "../components/Timeline";
import { solidColor } from "../colors";
import { endOf, onlineByChannel, todayStats } from "../stats";
import { D, H, fmtDur, fmtFull, p2, sod } from "../time";

type Preset = "24h" | "today" | "7d";

function presetRange(p: Preset, now: number): [number, number] {
  if (p === "today") return [sod(now), sod(now) + D];
  if (p === "7d") return [now + 6 * H - 7 * D, now + 6 * H];
  return [now - 18 * H, now + 6 * H];
}

const PRESETS: [Preset, string][] = [
  ["24h", "최근 24시간"],
  ["today", "오늘"],
  ["7d", "최근 7일"],
];

export default function TimelinePage() {
  const { data, now, base, guildId } = useGuild();
  const navigate = useNavigate();
  const [preset, setPreset] = useState<Preset | null>("24h");
  const [custom, setCustom] = useState<[number, number]>(() => presetRange("24h", now));
  const [hiddenByGuild, setHidden] = useState<Record<string, string[]>>({});
  const hidden = useMemo(() => new Set(hiddenByGuild[guildId] ?? []), [hiddenByGuild, guildId]);
  const range = preset ? presetRange(preset, now) : custom;
  const seconds = range[1] - range[0] < SECONDS_SPAN;
  // tick the timeline every second while it is zoomed in to seconds
  const tlNow = Math.max(now, useNow(data.clockOffset, seconds ? 1000 : 5000));
  const fmtRange = (t: number) => (seconds ? `${fmtFull(t)}:${p2(new Date(t).getSeconds())}` : fmtFull(t));

  const onRangeChange = useCallback((r: [number, number]) => {
    setPreset(null);
    setCustom(r);
  }, []);
  const zoom = (f: number) => {
    const mid = (range[0] + range[1]) / 2;
    const half = Math.min(MAX_SPAN, Math.max(MIN_SPAN, (range[1] - range[0]) * f)) / 2;
    onRangeChange([mid - half, mid + half]);
  };
  const toggleChannel = (ch: string) => {
    const next = new Set(hidden);
    if (next.has(ch)) next.delete(ch);
    else next.add(ch);
    setHidden({ ...hiddenByGuild, [guildId]: [...next] });
  };

  const st = todayStats(data.people, now);
  const online = onlineByChannel(data.people);
  const legend = data.channels.filter(
    (ch) => hidden.has(ch) || data.people.some((p) => p.sessions.some((s) => s.ch === ch && endOf(s, now) > range[0] && s.s < range[1])),
  );

  return (
    <>
      <Header />
      <div className="summary">
        <div className="stat">
          <div className="k">지금 접속</div>
          <div className="v">
            <span className="dot" />
            {st.online}명
          </div>
        </div>
        <div className="stat">
          <div className="k">오늘 접속한 사람</div>
          <div className="v">{st.people}명</div>
        </div>
        <div className="stat">
          <div className="k">오늘 총 접속 시간</div>
          <div className="v">{fmtDur(st.total)}</div>
        </div>
        <div className="stat">
          <div className="k">오늘 최장 접속</div>
          <div className="v">
            {st.best ? (
              <>
                <img src={st.best.p.avatar} alt="" />
                {st.best.p.name} · {fmtDur(st.best.t)}
              </>
            ) : (
              "-"
            )}
          </div>
        </div>
      </div>
      <div className="nowstrip">
        <span className="lbl">
          <span className="dot" />
          지금 접속 중
        </span>
        {online.length === 0 && <span className="lbl">아무도 없음</span>}
        {online.map(([ch, list]) => (
          <div key={ch} className="chip">
            <span className="cdot" style={{ background: solidColor(ch) }} />
            <b>{ch}</b>
            <span className="avs">
              {list.map((x) => (
                <img key={x.p.id} src={x.p.avatar} title={x.p.name} alt="" />
              ))}
            </span>
            {list.length}
          </div>
        ))}
      </div>
      <div className="ctrl">
        <div className="ctrl">
          <div className="seg">
            {PRESETS.map(([k, n]) => (
              <button key={k} className={preset === k ? "sel" : ""} onClick={() => setPreset(k)}>
                {n}
              </button>
            ))}
          </div>
          <button className="btn" onClick={() => setPreset("24h")}>
            지금으로
          </button>
          <button className="btn" onClick={() => zoom(0.5)} title="확대">
            ＋
          </button>
          <button className="btn" onClick={() => zoom(1.5)} title="축소">
            －
          </button>
          <span className="range">
            {fmtRange(range[0])} ~ {fmtRange(range[1])}
          </span>
        </div>
        <span className="grow" />
        <div className="legend">
          {legend.map((ch) => (
            <span key={ch} className={`lg ${hidden.has(ch) ? "off" : ""}`} onClick={() => toggleChannel(ch)}>
              <i style={{ background: solidColor(ch) }} />
              {ch}
            </span>
          ))}
        </div>
      </div>
      <div className="tlwrap">
        <Timeline
          people={data.people}
          hidden={hidden}
          range={range}
          now={tlNow}
          onRangeChange={onRangeChange}
          onOpenPerson={(id) => navigate(`${base}/users/${id}`)}
        />
      </div>
    </>
  );
}
