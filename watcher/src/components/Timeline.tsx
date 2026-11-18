import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { Person, Session } from "../types";
import { barColor, solidColor } from "../colors";
import { endOf, isOnline, lastActivity, timeIn } from "../stats";
import { D, H, MIN, SEC, fmtDur, fmtFull, fmtHM, fmtHMS, p2 } from "../time";

const NC = 190; // name column width
const ROW_H = 40;
const BAR_H = 24;
export const MIN_SPAN = 10 * SEC;
export const MAX_SPAN = 400 * D;
/** Below this visible span the timeline shows seconds (axis, now tag, tooltip). */
export const SECONDS_SPAN = 10 * MIN;
// after-bar label: 8px gap + 20px avatar + 6px gap + text, and keep 6px clear of the next bar
const LABEL_GAP = 8;
const LABEL_FIXED = 20 + 6;
const LABEL_CLEAR = 6;

let measureCtx: CanvasRenderingContext2D | null = null;
const widthCache = new Map<string, number>();
function textWidth(text: string) {
  let w = widthCache.get(text);
  if (w === undefined) {
    measureCtx ??= document.createElement("canvas").getContext("2d");
    if (!measureCtx) return text.length * 12;
    measureCtx.font = `12px ${getComputedStyle(document.body).fontFamily}`;
    w = Math.ceil(measureCtx.measureText(text).width);
    widthCache.set(text, w);
  }
  return w;
}

type Props = {
  people: Person[];
  hidden: Set<string>;
  range: [number, number];
  now: number;
  /** bot clock minus browser clock */
  clockOffset: number;
  onRangeChange: (r: [number, number]) => void;
  onOpenPerson: (id: string) => void;
};

const STEPS = [SEC, 2 * SEC, 5 * SEC, 10 * SEC, 15 * SEC, 30 * SEC, MIN, 5 * MIN, 10 * MIN, 30 * MIN, H, 2 * H, 3 * H, 6 * H, 12 * H, D, 2 * D, 7 * D, 14 * D, 30 * D];
/** Grid step that keeps ticks at least 64px apart. */
function tickStep(a: number, b: number, width: number) {
  return STEPS.find((s) => width / ((b - a) / s) >= 64) ?? 30 * D;
}
/** From this grid step down, the timeline redraws every frame so the now line moves smoothly. */
const LIVE_STEP = 10 * MIN;
/** Duration of the eased wheel zoom. */
const WHEEL_EASE_MS = 300;

/** Current time redrawn on every animation frame while `on`; otherwise the shared 5-second clock. */
function useLiveNow(base: number, offset: number, on: boolean) {
  const [t, setT] = useState(base);
  useEffect(() => {
    if (!on) return;
    let raf = 0;
    const loop = () => {
      setT(Date.now() + offset);
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [on, offset]);
  return on ? Math.max(base, t) : base;
}

function ticks(a: number, b: number, width: number) {
  const step = tickStep(a, b, width);
  const out: { t: number; label: string; major: boolean }[] = [];
  const start = new Date(a);
  start.setHours(0, 0, 0, 0);
  // sub-day steps start at the first tick inside the range instead of walking from midnight
  let t0 = start.getTime();
  if (step < D) t0 += Math.max(0, Math.floor((a - t0) / step)) * step;
  for (let t = t0; t <= b; ) {
    if (t >= a) {
      const d = new Date(t);
      const midnight = d.getHours() === 0 && d.getMinutes() === 0;
      out.push({
        t,
        label: midnight && d.getSeconds() === 0 ? `${d.getMonth() + 1}/${d.getDate()}` : step < MIN ? fmtHMS(t) : step < H ? fmtHM(t) : `${d.getHours()}시`,
        major: midnight && d.getSeconds() === 0,
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

export default function Timeline({ people, hidden, range, now: baseNow, clockOffset, onRangeChange, onOpenPerson }: Props) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(1000);
  const [hover, setHover] = useState<{ p: Person; s: Session; x: number; y: number } | null>(null);
  const [, setFontsReady] = useState(false);
  useEffect(() => {
    // label widths are measured with the web font, so re-measure once it has loaded
    document.fonts?.ready.then(() => {
      widthCache.clear();
      setFontsReady(true);
    });
  }, []);
  const [a, b] = range;
  const bw = Math.max(50, width - NC - 24);
  const X = (t: number) => NC + 12 + ((t - a) / (b - a)) * bw;
  const now = useLiveNow(baseNow, clockOffset, tickStep(a, b, bw) <= LIVE_STEP);

  useLayoutEffect(() => {
    const el = box.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setWidth(el.clientWidth));
    ro.observe(el);
    setWidth(el.clientWidth);
    return () => ro.disconnect();
  }, []);

  // wheel zoom around the mouse position (needs a non-passive listener), eased toward the target range
  const rangeRef = useRef(range);
  rangeRef.current = range;
  const anim = useRef<{ from: [number, number]; to: [number, number]; t0: number; raf: number; last: [number, number] } | null>(null);
  const stopAnim = () => {
    if (anim.current) cancelAnimationFrame(anim.current.raf);
    anim.current = null;
  };
  useEffect(() => stopAnim, []);
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const step = (ts: number) => {
      const a = anim.current;
      if (!a) return;
      // someone else (preset, buttons) moved the range: let them win
      const cur = rangeRef.current;
      if (cur[0] !== a.last[0] || cur[1] !== a.last[1]) {
        anim.current = null;
        return;
      }
      const p = Math.min(1, (ts - a.t0) / WHEEL_EASE_MS);
      const k = 1 - Math.pow(1 - p, 3);
      const next: [number, number] = [a.from[0] + (a.to[0] - a.from[0]) * k, a.from[1] + (a.to[1] - a.from[1]) * k];
      a.last = next;
      rangeRef.current = next;
      onRangeChange(next);
      if (p < 1) a.raf = requestAnimationFrame(step);
      else anim.current = null;
    };
    const onWheel = (e: WheelEvent) => {
      const rect = el.getBoundingClientRect();
      const x = e.clientX - rect.left;
      if (x < NC) return;
      e.preventDefault();
      // keep zooming from where the running animation is heading
      const [ra, rb] = anim.current ? anim.current.to : rangeRef.current;
      const span = rb - ra;
      const factor = e.deltaY < 0 ? 0.8 : 1.25;
      const nextSpan = Math.min(MAX_SPAN, Math.max(MIN_SPAN, span * factor));
      const w = el.clientWidth - NC - 24;
      const at = ra + ((x - NC - 12) / w) * span;
      const r = (at - ra) / span;
      const to: [number, number] = [at - r * nextSpan, at + (1 - r) * nextSpan];
      const from: [number, number] = [...rangeRef.current];
      if (anim.current) {
        Object.assign(anim.current, { from, to, t0: performance.now() });
      } else {
        anim.current = { from, to, t0: performance.now(), raf: requestAnimationFrame(step), last: from };
      }
    };
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, [onRangeChange]);

  const onMouseDown = (e: React.MouseEvent) => {
    const el = box.current;
    if (!el || e.button !== 0) return;
    const rect = el.getBoundingClientRect();
    if (e.clientX - rect.left < NC) return;
    stopAnim();
    const startX = e.clientX;
    const [ra, rb] = rangeRef.current;
    const w = el.clientWidth - NC - 24;
    let moved = false;
    const move = (ev: MouseEvent) => {
      const dx = ev.clientX - startX;
      if (!moved && Math.abs(dx) > 2) {
        moved = true;
        el.classList.add("dragging");
      }
      if (!moved) return;
      const dt = -(dx / w) * (rb - ra);
      onRangeChange([ra + dt, rb + dt]);
    };
    const up = () => {
      el.classList.remove("dragging");
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
        {/* skip labels that would be cut in half by the name column */}
        {tk.filter((k) => X(k.t) > NC + 30).map((k) => (
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
            {b - a < SECONDS_SPAN ? fmtHMS(now) : fmtHM(now)}
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
                // show the label only when it fits entirely before the next bar and the right edge
                const next = sessions[si + 1];
                const labelEnd = x2 + LABEL_GAP + LABEL_FIXED + textWidth(p.name) + LABEL_CLEAR;
                const label = labelEnd <= (next ? X(next.s) : Infinity) && labelEnd <= width;
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
      {hover && <SessionTip {...hover} now={now} width={width} seconds={b - a < SECONDS_SPAN} />}
    </div>
  );
}

function SessionTip({ p, s, x, y, now, width, seconds }: { p: Person; s: Session; x: number; y: number; now: number; width: number; seconds: boolean }) {
  const when = (t: number) => (seconds ? `${fmtFull(t)}:${p2(new Date(t).getSeconds())}` : fmtFull(t));
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
        <b>{when(s.s)}</b>
      </div>
      <div className="ln">
        <span>퇴장</span>
        <b>{s.online ? "접속 중" : when(end)}</b>
      </div>
      <div className="ln">
        <span>이번 접속</span>
        <b>{fmtDur(end - s.s)}</b>
      </div>
      <div className="tot">총 접속 {fmtDur(total)}</div>
    </div>
  );
}
