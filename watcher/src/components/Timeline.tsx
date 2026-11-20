import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { Person, Session } from "../types";
import { barColor, chName, solidColor } from "../colors";
import { endOf, isOnline, lastActivity, timeIn } from "../stats";
import { useMobile } from "../useMobile";
import { D, H, MIN, SEC, fmtDur, fmtFull, fmtHM, fmtHMS, kst, p2, sod } from "../time";

// name column width (phones show only avatars there)
const NC_WIDE = 285;
const NC_PHONE = 44;
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
    measureCtx.font = `13px ${getComputedStyle(document.body).fontFamily}`;
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
/** Extra range rendered on each side, as a share of the visible span. */
const OVERSCAN = 0.5;
/**
 * Sessions of one person closer than this many pixels are drawn as one bar. Bars are at least 2px wide,
 * so such neighbours already touch on screen; merging keeps the DOM small when zoomed far out.
 */
const MERGE_PX = 2;

type Run = { members: Session[]; xStart: number; xEnd: number; ch: string };

/** Group a person's sessions (in time order) into runs that would touch on screen. */
function mergeRuns(sessions: Session[], X: (t: number) => number, now: number): Run[] {
  const runs: Run[] = [];
  for (const s of sessions) {
    const xs = X(s.s);
    const xe = X(endOf(s, now));
    const prev = runs[runs.length - 1];
    if (prev && xs - prev.xEnd < MERGE_PX) {
      prev.members.push(s);
      prev.xEnd = Math.max(prev.xEnd, xe);
    } else {
      runs.push({ members: [s], xStart: xs, xEnd: xe, ch: s.ch });
    }
  }
  // a merged run takes the color of the channel it spent the most time in
  for (const run of runs) {
    if (run.members.length < 2) continue;
    const time = new Map<string, number>();
    for (const m of run.members) time.set(m.ch, (time.get(m.ch) ?? 0) + (endOf(m, now) - m.s));
    run.ch = [...time.entries()].sort((x, y) => y[1] - x[1])[0][0];
  }
  return runs;
}
/** Duration of the eased wheel zoom. */
const WHEEL_EASE_MS = 300;
/** Zoom per wheel pixel: ln(1.25) / 100, so one mouse notch scales the span by 1.25. */
const WHEEL_ZOOM_PER_PX = Math.log(1.25) / 100;

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
  // KST has no DST, so fixed steps from the start of the (KST) day stay on clock boundaries
  const start = sod(a);
  const t0 = step < D ? start + Math.max(0, Math.floor((a - start) / step)) * step : start;
  for (let t = t0; t <= b; t += step) {
    if (t < a) continue;
    const k = kst(t);
    const midnight = k.hour === 0 && k.minute === 0 && k.second === 0;
    out.push({
      t,
      label: midnight ? `${k.month + 1}/${k.date}` : step < MIN ? fmtHMS(t) : step < H ? fmtHM(t) : `${k.hour}시`,
      major: midnight,
    });
  }
  return out;
}

export default function Timeline({ people, hidden, range, now: baseNow, clockOffset, onRangeChange, onOpenPerson }: Props) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(1000);
  const [hover, setHover] = useState<{ p: Person; s: Session; x: number; y: number } | null>(null);
  const mobile = useMobile();
  const NC = mobile ? NC_PHONE : NC_WIDE;
  // phones: tapping a bar opens its info as a bottom sheet
  const [sheet, setSheet] = useState<{ p: Person; s: Session } | null>(null);
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
  // The zoom keeps the time under the cursor fixed and eases the span (in log scale) toward the target.
  // A wheel event during an animation retargets from the span on screen with the same anchor, so rapid
  // scrolling stays smooth instead of jumping between the old and new targets.
  const anim = useRef<{ at: number; r: number; fromSpan: number; toSpan: number; t0: number; raf: number; last: [number, number] } | null>(null);
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
      // someone else (preset, buttons, drag) moved the range: let them win
      const cur = rangeRef.current;
      if (cur[0] !== a.last[0] || cur[1] !== a.last[1]) {
        anim.current = null;
        return;
      }
      const p = Math.min(1, (ts - a.t0) / WHEEL_EASE_MS);
      const k = 1 - Math.pow(1 - p, 3);
      const span = Math.exp(Math.log(a.fromSpan) + (Math.log(a.toSpan) - Math.log(a.fromSpan)) * k);
      const next: [number, number] = [a.at - a.r * span, a.at + (1 - a.r) * span];
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
      const w = el.clientWidth - NC - 24;
      const r = Math.min(1, Math.max(0, (x - NC - 12) / w));
      const [ra, rb] = rangeRef.current;
      const span = rb - ra;
      // proportional to the scroll amount: a mouse notch (~100px) is x1.25, touchpads scroll in small steps
      const pixels = e.deltaY * (e.deltaMode === 1 ? 33 : e.deltaMode === 2 ? 400 : 1);
      const factor = Math.exp(Math.max(-300, Math.min(300, pixels)) * WHEEL_ZOOM_PER_PX);
      const baseSpan = anim.current ? anim.current.toSpan : span;
      const toSpan = Math.min(MAX_SPAN, Math.max(MIN_SPAN, baseSpan * factor));
      const at = ra + r * span;
      const last: [number, number] = [ra, rb];
      if (anim.current) {
        Object.assign(anim.current, { at, r, fromSpan: span, toSpan, t0: performance.now(), last });
      } else {
        anim.current = { at, r, fromSpan: span, toSpan, t0: performance.now(), raf: requestAnimationFrame(step), last };
      }
    };
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, [onRangeChange, NC]);

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

  // touch: one finger drags horizontally, two fingers pinch-zoom around their midpoint.
  // Vertical drags are left to the browser (touch-action: pan-y) to scroll the rows.
  const touch = useRef<
    | { kind: "pan"; x0: number; y0: number; r0: [number, number]; dir: "x" | "y" | null }
    | { kind: "pinch"; d0: number; at: number; r0: [number, number] }
    | null
  >(null);
  // pointer x in the same coordinates as X() (inside the timeline's border)
  const plotX = (clientX: number) => clientX - box.current!.getBoundingClientRect().left - box.current!.clientLeft - NC - 12;
  const onTouchStart = (e: React.TouchEvent) => {
    stopAnim();
    const [ra, rb] = rangeRef.current;
    const w = box.current!.clientWidth - NC - 24;
    if (e.touches.length >= 2) {
      const [t1, t2] = [e.touches[0], e.touches[1]];
      const mid = (t1.clientX + t2.clientX) / 2;
      touch.current = { kind: "pinch", d0: Math.max(10, Math.abs(t1.clientX - t2.clientX)), at: ra + (plotX(mid) / w) * (rb - ra), r0: [ra, rb] };
    } else {
      const t = e.touches[0];
      touch.current = { kind: "pan", x0: t.clientX, y0: t.clientY, r0: [ra, rb], dir: null };
    }
  };
  const onTouchMove = (e: React.TouchEvent) => {
    const g = touch.current;
    if (!g) return;
    const w = box.current!.clientWidth - NC - 24;
    if (g.kind === "pinch" && e.touches.length >= 2) {
      const [t1, t2] = [e.touches[0], e.touches[1]];
      const d = Math.max(10, Math.abs(t1.clientX - t2.clientX));
      const span = Math.min(MAX_SPAN, Math.max(MIN_SPAN, ((g.r0[1] - g.r0[0]) * g.d0) / d));
      const f = plotX((t1.clientX + t2.clientX) / 2) / w;
      onRangeChange([g.at - f * span, g.at + (1 - f) * span]);
    } else if (g.kind === "pan" && e.touches.length === 1) {
      const t = e.touches[0];
      const dx = t.clientX - g.x0;
      const dy = t.clientY - g.y0;
      if (!g.dir && Math.hypot(dx, dy) > 6) g.dir = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
      if (g.dir !== "x") return;
      const dt = -(dx / w) * (g.r0[1] - g.r0[0]);
      onRangeChange([g.r0[0] + dt, g.r0[1] + dt]);
    }
  };
  const onTouchEnd = (e: React.TouchEvent) => {
    // lifting one finger of a pinch continues as a pan from the current range
    if (e.touches.length === 1) {
      const t = e.touches[0];
      touch.current = { kind: "pan", x0: t.clientX, y0: t.clientY, r0: [...rangeRef.current], dir: "x" };
    } else if (e.touches.length === 0) touch.current = null;
  };

  const rows = useMemo(() => {
    // render a margin beyond the visible range so bars and their name labels slide in and out at the
    // edges instead of popping when the range moves
    const margin = (b - a) * OVERSCAN;
    const visible = (s: Session) => !hidden.has(s.ch) && endOf(s, now) > a - margin && s.s < b + margin;
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
    <div
      className="tl"
      ref={box}
      onMouseDown={onMouseDown}
      onMouseLeave={() => setHover(null)}
      onTouchStart={onTouchStart}
      onTouchMove={onTouchMove}
      onTouchEnd={onTouchEnd}
      onTouchCancel={onTouchEnd}
    >
      <div className="axis">
        {/* skip labels that would be cut in half by the name column */}
        {tk.filter((k) => X(k.t) > NC + 30).map((k) => (
          <span key={k.t} className={k.major ? "major" : ""} style={{ left: X(k.t) }}>
            {k.label}
          </span>
        ))}
      </div>
      <div className="axis namehead" style={{ width: NC }}>
        <span style={{ left: 12 }}>사람</span>
        <span style={{ left: "auto", right: 10 }}>기간 내 접속</span>
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
              {mergeRuns(sessions, X, now).map((run, ri, runs) => {
                const last = run.members[run.members.length - 1];
                const x1 = Math.max(run.xStart, NC);
                const x2 = Math.min(run.xEnd, width);
                const barShown = x2 > x1;
                // the label follows the run's real end (it may sit under the name column or past the right
                // edge, where the timeline clips it); show it only when it fits before the next run
                const next = runs[ri + 1];
                const labelEnd = run.xEnd + LABEL_GAP + LABEL_FIXED + textWidth(p.name) + LABEL_CLEAR;
                const label = labelEnd <= (next ? next.xStart : Infinity) && labelEnd > NC && run.xEnd < width;
                if (!barShown && !label) return null;
                // a merged run shows the session under the pointer
                const pick = (clientX: number) => {
                  if (run.members.length === 1) return run.members[0];
                  const t = a + (plotX(clientX) / bw) * (b - a);
                  let best = run.members[0];
                  let bestDist = Infinity;
                  for (const m of run.members) {
                    const dist = t < m.s ? m.s - t : t > endOf(m, now) ? t - endOf(m, now) : 0;
                    if (dist < bestDist) {
                      best = m;
                      bestDist = dist;
                    }
                  }
                  return best;
                };
                return (
                  <div key={run.members[0].id}>
                    {barShown && (
                      <div
                        className={`bar ${last.online ? "online" : ""}`}
                        style={{ left: x1, width: Math.max(2, x2 - x1), top: (ROW_H - BAR_H) / 2, height: BAR_H, background: barColor(run.ch) }}
                        onMouseMove={mobile ? undefined : (e) => onHover(e, p, pick(e.clientX))}
                        onMouseLeave={mobile ? undefined : () => setHover(null)}
                        onClick={mobile ? (e) => setSheet({ p, s: pick(e.clientX) }) : undefined}
                      />
                    )}
                    {label && (
                      <div className={`flabel ${last.online ? "on" : ""}`} style={{ left: run.xEnd + LABEL_GAP, top: (ROW_H - 20) / 2 }}>
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
      {hover && !mobile && <SessionTip {...hover} now={now} width={width} seconds={b - a < SECONDS_SPAN} />}
      {sheet && mobile && (
        <>
          <div className="sheetback" onClick={() => setSheet(null)} />
          <SessionTip {...sheet} x={0} y={0} now={now} width={width} seconds={b - a < SECONDS_SPAN} sheet />
        </>
      )}
    </div>
  );
}

function SessionTip({ p, s, x, y, now, width, seconds, sheet }: { p: Person; s: Session; x: number; y: number; now: number; width: number; seconds: boolean; sheet?: boolean }) {
  const when = (t: number) => (seconds ? `${fmtFull(t)}:${p2(kst(t).second)}` : fmtFull(t));
  const total = p.sessions.reduce((acc, it) => acc + (endOf(it, now) - it.s), 0);
  const end = endOf(s, now);
  const flipX = x > width - 280;
  const flipY = y > 420;
  return (
    <div className={sheet ? "stip sheet" : "stip"} style={sheet ? undefined : { left: flipX ? x - 262 : x + 12, top: flipY ? y - 190 : y + 12 }}>
      <div className="u">
        <img src={p.avatar} alt="" />
        {p.name}
        {s.online && <span className="dot" style={{ marginLeft: "auto" }} />}
      </div>
      <span className="chn" style={{ background: solidColor(s.ch) }}>
        🔊 {chName(s.ch)}
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
