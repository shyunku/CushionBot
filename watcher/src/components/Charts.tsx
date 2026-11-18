import { useId } from "react";
import { fmtDur } from "../time";

const DAYS = ["일", "월", "화", "수", "목", "금", "토"];

function HourLabels() {
  return (
    <div className="hlabels">
      {Array.from({ length: 24 }, (_, h) => (
        <span key={h}>{h % 3 === 0 ? `${h}시` : ""}</span>
      ))}
    </div>
  );
}

/** Smooth area curve for a day split into equal slots (values 0..1). */
export function FreqCurve({ slots, nowFraction, height = 120 }: { slots: number[]; nowFraction: number; height?: number }) {
  const gid = useId();
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
  const nx = nowFraction * W;
  return (
    <>
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
      <HourLabels />
    </>
  );
}

/** Weekday x hour heatmap (Monday first). */
export function Heatmap({ heat }: { heat: number[][] }) {
  const max = Math.max(1, ...heat.flat());
  return (
    <div className="heat">
      {[1, 2, 3, 4, 5, 6, 0].map((d) => (
        <div key={d} style={{ display: "contents" }}>
          <span>{DAYS[d]}</span>
          {heat[d].map((v, h) => (
            <i key={h} style={{ opacity: 0.08 + (v / max) * 0.92 }} title={`${DAYS[d]} ${h}시 · ${v}일`} />
          ))}
        </div>
      ))}
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
