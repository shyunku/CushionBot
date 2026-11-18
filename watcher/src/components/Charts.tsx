import { useId, useRef, useState, type ReactNode } from "react";
import { D, fmtDur, fmtHM, sod } from "../time";

export const DAYS = ["일", "월", "화", "수", "목", "금", "토"];

function HourLabels() {
  return (
    <div className="hlabels">
      {Array.from({ length: 24 }, (_, h) => (
        <span key={h}>{h % 3 === 0 ? `${h}시` : ""}</span>
      ))}
    </div>
  );
}

type Tip = { x: number; y: number; title: string; body: ReactNode };

/** Small tooltip positioned inside a `position: relative` parent. */
function ChartTip({ tip, width }: { tip: Tip | null; width: number }) {
  if (!tip) return null;
  const flip = tip.x > width - 230;
  return (
    <div className="ctip" style={{ left: flip ? tip.x - 12 : tip.x + 12, top: tip.y, transform: flip ? "translateX(-100%)" : undefined }}>
      <b>{tip.title}</b>
      <div>{tip.body}</div>
    </div>
  );
}

/** Track the mouse inside a chart box and keep a tooltip for it. */
function useChartTip() {
  const box = useRef<HTMLDivElement>(null);
  const [tip, setTip] = useState<Tip | null>(null);
  const show = (e: React.MouseEvent, title: string, body: ReactNode, y?: number) => {
    const r = box.current!.getBoundingClientRect();
    setTip({ x: e.clientX - r.left, y: y ?? e.clientY - r.top - 52, title, body });
  };
  return { box, tip, show, hide: () => setTip(null), width: box.current?.clientWidth ?? 0 };
}

/**
 * Smooth area curve for a day split into equal slots, with the current time marked and a hover
 * tooltip per slot. `format` turns a slot's raw value into the tooltip text.
 */
export function FreqCurve({ slots, now, height = 120, format }: { slots: number[]; now: number; height?: number; format: (v: number) => ReactNode }) {
  const gid = useId();
  const { box, tip, show, hide, width } = useChartTip();
  const [guide, setGuide] = useState<number | null>(null);
  const n = slots.length;
  const smooth = slots.map((_, i) => {
    let acc = 0;
    for (let k = -2; k <= 2; k++) acc += slots[(i + k + n) % n];
    return acc / 5;
  });
  const max = Math.max(0.01, ...smooth);
  const W = 1000;
  const Hh = 100;
  const pts = smooth.map((v, i) => [(i / (n - 1)) * W, Hh - (v / max) * (Hh - 6) - 2]);
  let d = `M${pts[0][0]},${pts[0][1]}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] ?? pts[i];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[i + 2] ?? p2;
    d += ` C${p1[0] + (p2[0] - p0[0]) / 6},${p1[1] + (p2[1] - p0[1]) / 6} ${p2[0] - (p3[0] - p1[0]) / 6},${p2[1] - (p3[1] - p1[1]) / 6} ${p2[0]},${p2[1]}`;
  }
  const nowFrac = (now - sod(now)) / D;
  const nx = nowFrac * W;
  const len = D / n;

  const onMove = (e: React.MouseEvent) => {
    const r = box.current!.getBoundingClientRect();
    const x = e.clientX - r.left;
    const i = Math.max(0, Math.min(n - 1, Math.floor((x / r.width) * n)));
    const t0 = sod(now) + i * len;
    setGuide(x);
    show(e, `${fmtHM(t0)}~${fmtHM(t0 + len)}`, format(slots[i]), 6);
  };

  return (
    <>
      <div
        className="fwrap"
        ref={box}
        onMouseMove={onMove}
        onMouseLeave={() => {
          hide();
          setGuide(null);
        }}
      >
        <svg viewBox={`0 0 ${W} ${Hh}`} preserveAspectRatio="none" style={{ width: "100%", height, display: "block" }}>
          <defs>
            <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="var(--accent)" stopOpacity={0.45} />
              <stop offset="1" stopColor="var(--accent)" stopOpacity={0} />
            </linearGradient>
          </defs>
          {[0.25, 0.5, 0.75].map((y) => (
            <line key={y} x1={0} x2={W} y1={Hh * y} y2={Hh * y} stroke="var(--grid)" vectorEffect="non-scaling-stroke" />
          ))}
          <path d={`${d} L${W},${Hh} L0,${Hh} Z`} fill={`url(#${gid})`} />
          <path d={d} fill="none" stroke="var(--accent)" strokeWidth={2} vectorEffect="non-scaling-stroke" />
          <line x1={nx} x2={nx} y1={0} y2={Hh} stroke="var(--now)" strokeWidth={2} vectorEffect="non-scaling-stroke" />
        </svg>
        <span className="fnow" style={{ left: `${nowFrac * 100}%` }}>
          지금 {fmtHM(now)}
        </span>
        {guide != null && <div className="fguide" style={{ left: guide }} />}
        <ChartTip tip={tip} width={width} />
      </div>
      <HourLabels />
    </>
  );
}

/** Weekday x hour heatmap (Monday first) with a hover tooltip per cell. */
export function Heatmap({ heat, cellHeight = 13, format }: { heat: number[][]; cellHeight?: number; format: (day: number, hour: number, v: number) => ReactNode }) {
  const { box, tip, show, hide, width } = useChartTip();
  const max = Math.max(0.0001, ...heat.flat());
  return (
    <div className="heatbox" ref={box} onMouseLeave={hide}>
      <div className="heat">
        {[1, 2, 3, 4, 5, 6, 0].map((d) => (
          <div key={d} style={{ display: "contents" }}>
            <span>{DAYS[d]}</span>
            {heat[d].map((v, h) => (
              <i key={h} style={{ opacity: 0.08 + (v / max) * 0.92, height: cellHeight }} onMouseMove={(e) => show(e, `${DAYS[d]}요일 ${h}시`, format(d, h, v))} />
            ))}
          </div>
        ))}
      </div>
      <ChartTip tip={tip} width={width} />
    </div>
  );
}

export type GrassDay = { day: number; total: number; people: number };

const CELL = 10;
const GAP = 2;
const PITCH = CELL + GAP;

/** GitHub-style daily grid (columns are weeks, rows Sunday..Saturday). */
export function Grass({ days }: { days: GrassDay[] }) {
  const { box, tip, show, hide, width } = useChartTip();
  if (days.length === 0) return null;
  const max = Math.max(1, ...days.map((x) => x.total));
  const lead = new Date(days[0].day).getDay();
  const weeks = Math.ceil((lead + days.length) / 7);
  const months: { col: number; label: string }[] = [];
  let lastMonth = -1;
  days.forEach((x, i) => {
    const m = new Date(x.day).getMonth();
    if (m !== lastMonth) {
      lastMonth = m;
      months.push({ col: Math.floor((lead + i) / 7), label: `${m + 1}월` });
    }
  });
  return (
    <div className="grassbox" ref={box} onMouseLeave={hide}>
      <div className="gmonths">
        {months.map((m) => (
          <span key={`${m.col}-${m.label}`} style={{ left: `${(m.col / weeks) * 100}%` }}>
            {m.label}
          </span>
        ))}
      </div>
      <div className="grasswrap">
        <div className="wd">
          {DAYS.map((x, i) => (
            <span key={x}>{i % 2 ? x : ""}</span>
          ))}
        </div>
        <svg className="grass" viewBox={`0 0 ${weeks * PITCH - GAP} ${7 * PITCH - GAP}`}>
          {days.map((x, i) => {
            const d = new Date(x.day);
            const k = lead + i;
            return (
              <rect
                key={x.day}
                x={Math.floor(k / 7) * PITCH}
                y={(k % 7) * PITCH}
                width={CELL}
                height={CELL}
                rx={2}
                className={x.total ? "" : "empty"}
                style={x.total ? { opacity: 0.2 + (x.total / max) * 0.8 } : undefined}
                onMouseMove={(e) =>
                  show(e, `${d.getMonth() + 1}월 ${d.getDate()}일 (${DAYS[d.getDay()]})`, x.total ? `총 ${fmtDur(x.total)} · ${x.people}명` : "접속 없음")
                }
              />
            );
          })}
        </svg>
      </div>
      <div className="glegend">
        적음
        {[0.2, 0.4, 0.6, 0.8, 1].map((o) => (
          <i key={o} style={{ opacity: o }} />
        ))}
        많음
      </div>
      <ChartTip tip={tip} width={width} />
    </div>
  );
}

export function DailyBars({ values, height = 90 }: { values: number[]; height?: number }) {
  const max = Math.max(1, ...values);
  return (
    <div className="dbars" style={{ height }}>
      {values.map((v, i) => (
        <div key={i} style={{ height: `${Math.max(2, (v / max) * 100)}%` }} title={fmtDur(v)} />
      ))}
    </div>
  );
}

export function MiniBars({ values }: { values: number[] }) {
  const max = Math.max(1, ...values);
  return (
    <div className="dbars mini">
      {values.map((v, i) => (
        <div key={i} style={{ height: `${Math.max(4, (v / max) * 100)}%` }} title={`${v}번`} />
      ))}
    </div>
  );
}
