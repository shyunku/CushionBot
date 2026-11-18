export const SEC = 1e3;
export const MIN = 60e3;
export const H = 60 * MIN;
export const D = 24 * H;

export const p2 = (n: number) => String(n).padStart(2, "0");

export function sod(t: number) {
  const d = new Date(t);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

export function fmtDur(ms: number) {
  const m = Math.round(ms / MIN);
  if (m < 1) return `${Math.max(0, Math.round(ms / 1000))}초`;
  const h = Math.floor(m / 60);
  return h ? `${h}시간 ${m % 60}분` : `${m}분`;
}

export function fmtHM(t: number) {
  const d = new Date(t);
  return `${p2(d.getHours())}:${p2(d.getMinutes())}`;
}

export function fmtHMS(t: number) {
  const d = new Date(t);
  return `${p2(d.getHours())}:${p2(d.getMinutes())}:${p2(d.getSeconds())}`;
}

export function fmtFull(t: number) {
  const d = new Date(t);
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${p2(d.getHours())}:${p2(d.getMinutes())}`;
}

export function fmtDate(t: number) {
  const d = new Date(t);
  return `${d.getFullYear()}.${p2(d.getMonth() + 1)}.${p2(d.getDate())}`;
}
