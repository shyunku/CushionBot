import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { Person, Session } from "../types";
import { barColor, solidColor } from "../colors";
import { endOf, isOnline, lastActivity, timeIn } from "../stats";
import { D, H, MIN, fmtDur, fmtFull, fmtHM } from "../time";

const NC = 190; // name column width
const ROW_H = 40;
const BAR_H = 24;
const MIN_SPAN = 10 * MIN;
const MAX_SPAN = 400 * D;

type Props = {
  people: Person[];
  hidden: Set<string>;
  range: [number, number];
  now: number;
  onRangeChange: (r: [number, number]) => void;
  onOpenPerson: (id: string) => void;
};

function ticks(a: number, b: number, width: number) {
  const steps = [10 * MIN, 30 * MIN, H, 2 * H, 3 * H, 6 * H, 12 * H, D, 2 * D, 7 * D, 14 * D, 30 * D];
  const step = steps.find((s) => width / ((b - a) / s) >= 64) ?? 30 * D;
  const out: { t: number; label: string; major: boolean }[] = [];
  const start = new Date(a);
  start.setHours(0, 0, 0, 0);
  for (let t = start.getTime(); t <= b; ) {
    if (t >= a) {
      const d = new Date(t);
      const midnight = d.getHours() === 0 && d.getMinutes() === 0;
      out.push({
        t,
        label: midnight ? `${d.getMonth() + 1}/${d.getDate()}` : step < H ? fmtHM(t) : `${d.getHours()}시`,
        major: midnight,
      });
    }
    if (step >= D) {
      const d = new Date(t);
      d.setDate(d.getDate() + Math.round(step / D));
      t = d.getTime();
    } else {
      const d = new Date(t);
      const next = new Date(t + step);
      // keep sub-day ticks on local wall-clock boundaries across DST changes
      t = next.getDate() !== d.getDate() ? new Date(next.getFullYear(), next.getMonth(), next.getDate()).getTime() : t + step;
    }
  }
  return out;
}

export default function Timeline({ people, hidden, range, now, onRangeChange, onOpenPerson }: Props) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(1000);
  const [hover, setHover] = useState<{ p: Person; s: Session; x: number; y: number } | null>(null);
  const [a, b] = range;
  const bw = Math.max(50, width - NC - 24);
  const X = (t: number) => NC + 12 + ((t - a) / (b - a)) * bw;

  useLayoutEffect(() => {
    const el = box.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setWidth(el.clientWidth));
    ro.observe(el);
    setWidth(el.clientWidth);
    return () => ro.disconnect();
  }, []);

  // wheel zoom around the mouse position (needs a non-passive listener)
  const rangeRef = useRef(range);
  rangeRef.current = range;
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      const rect = el.getBoundingClientRect();
      const x = e.clientX - rect.left;
      if (x < NC) return;
      e.preventDefault();
      const [ra, rb] = rangeRef.current;
      const span = rb - ra;
      const factor = e.deltaY < 0 ? 0.8 : 1.25;
      const nextSpan = Math.min(MAX_SPAN, Math.max(MIN_SPAN, span * factor));
      const w = el.clientWidth - NC - 24;
      const at = ra + ((x - NC - 12) / w) * span;
      const r = (at - ra) / span;
      const next: [number, number] = [at - r * nextSpan, at + (1 - r) * nextSpan];
      rangeRef.current = next;
      onRangeChange(next);
    };
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, [onRangeChange]);

  const onMouseDown = (e: React.MouseEvent) => {
    const el = box.current;
    if (!el || e.button !== 0) return;
    const rect = el.getBoundingClientRect();
    if (e.clientX - rect.left < NC) return;
    const startX = e.clientX;
    const [ra, rb] = rangeRef.current;
    const w = el.clientWidth - NC - 24;
    let moved = false;
    const move = (ev: MouseEvent) => {
      const dx = ev.clientX - startX;
      if (Math.abs(dx) > 2) moved = true;
      if (!moved) return;
      const dt = -(dx / w) * (rb - ra);
      onRangeChange([ra + dt, rb + dt]);
    };
    const up = () => {
      window.removeEventListener("mousemove", move);
      window.removeEventListener("mouseup", up);
    };
    window.addEventListener("mousemove", move);
    window.addEventListener("mouseup", up);
  };

  const rows = useMemo(() => {
    const visible = (s: Session) => !hidden.has(s.ch) && endOf(s, now) > a && s.s < b;
    return people
      .filter((p) => p.sessions.some(visible))
      .sort((x, y) => Number(isOnline(y)) - Number(isOnline(x)) || lastActivity(y, now) - lastActivity(x, now))
      .map((p) => ({ p, sessions: p.sessions.filter(visible) }));
  }, [people, hidden, a, b, now]);

  const tk = ticks(a, b, bw);
  const nx = X(now);

  const onHover = (e: React.MouseEvent, p: Person, s: Session) => {
    const rect = box.current!.getBoundingClientRect();
    setHover({ p, s, x: e.clientX - rect.left, y: e.clientY - rect.top });
  };

  return (
    <div className="tl" ref={box} onMouseDown={onMouseDown} onMouseLeave={() => setHover(null)}>
      <div className="axis">
        {tk.map((k) => (
          <span key={k.t} className={k.major ? "major" : ""} style={{ left: X(k.t) }}>
            {k.label}
          </span>
        ))}
      </div>
      <div className="axis namehead" style={{ width: NC }}>
        <span style={{ left: 34 }}>사람</span>
        <span style={{ left: "auto", right: 10, transform: "none" }}>기간 내 접속</span>
      </div>
      {tk.map((k) => (
        <div key={`g${k.t}`} className="gridline" style={{ left: X(k.t) }} />
      ))}
      {nx > NC && nx < width && (
        <>
          <div className="nowline" style={{ left: nx - 1 }} />
          <div className="nowtag" style={{ left: nx }}>
            {fmtHM(now)}
          </div>
        </>
      )}
      <div className="rows">
        {rows.length === 0 && <div className="empty">이 기간에는 접속 기록이 없어요.</div>}
        {rows.map(({ p, sessions }, i) => {
          const on = isOnline(p);
          const inRange = timeIn(p, a, Math.min(b, now), now);
          return (
            <div key={p.id} className={`row ${i % 2 ? "alt" : ""}`} style={{ height: ROW_H }}>
              <div className="namecol link" style={{ width: NC }} onClick={() => onOpenPerson(p.id)}>
                <img src={p.avatar} alt="" />
                <span className="nm">{p.name}</span>
                {on && <span className="on" />}
                <span className="sub">{inRange ? fmtDur(inRange) : "-"}</span>
              </div>
              {sessions.map((s, si) => {
                const x1 = Math.max(X(s.s), NC);
                const x2 = Math.min(X(endOf(s, now)), width);
                if (x2 <= x1) return null;
                const next = sessions[si + 1];
                const gap = next ? X(next.s) - x2 : Infinity;
                const label = gap > 90 && x2 + 90 < width;
                return (
                  <div key={s.s}>
                    <div
                      className={`bar ${s.online ? "online" : ""}`}
                      style={{ left: x1, width: Math.max(2, x2 - x1), top: (ROW_H - BAR_H) / 2, height: BAR_H, background: barColor(s.ch) }}
                      onMouseMove={(e) => onHover(e, p, s)}
                      onMouseLeave={() => setHover(null)}
                    />
                    {label && (
                      <div className={`flabel ${s.online ? "on" : ""}`} style={{ left: x2 + 8, top: (ROW_H - 20) / 2 }}>
                        <img src={p.avatar} alt="" />
                        {p.name}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>
      {hover && <SessionTip {...hover} now={now} width={width} />}
    </div>
  );
}

function SessionTip({ p, s, x, y, now, width }: { p: Person; s: Session; x: number; y: number; now: number; width: number }) {
  const total = p.sessions.reduce((acc, it) => acc + (endOf(it, now) - it.s), 0);
  const end = endOf(s, now);
  const flipX = x > width - 280;
  const flipY = y > 420;
  return (
    <div className="stip" style={{ left: flipX ? x - 262 : x + 12, top: flipY ? y - 190 : y + 12 }}>
      <div className="u">
        <img src={p.avatar} alt="" />
        {p.name}
        {s.online && <span className="dot" style={{ marginLeft: "auto" }} />}
      </div>
      <span className="chn" style={{ background: solidColor(s.ch) }}>
        🔊 {s.ch}
      </span>
      <div className="ln">
        <span>입장</span>
        <b>{fmtFull(s.s)}</b>
      </div>
      <div className="ln">
        <span>퇴장</span>
        <b>{s.online ? "접속 중" : fmtFull(end)}</b>
      </div>
      <div className="ln">
        <span>이번 접속</span>
        <b>{fmtDur(end - s.s)}</b>
      </div>
      <div className="tot">총 접속 {fmtDur(total)}</div>
    </div>
  );
}
