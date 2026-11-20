export const SEC = 1e3;
export const MIN = 60e3;
export const H = 60 * MIN;
export const D = 24 * H;

/** Korea has no daylight saving time, so KST is always UTC+9. */
const KST_OFFSET = 9 * H;

export const p2 = (n: number) => String(n).padStart(2, "0");

/** Calendar fields of a time in KST. */
export function kst(t: number) {
  const d = new Date(t + KST_OFFSET);
  return {
    year: d.getUTCFullYear(),
    month: d.getUTCMonth(),
    date: d.getUTCDate(),
    weekday: d.getUTCDay(),
    hour: d.getUTCHours(),
    minute: d.getUTCMinutes(),
    second: d.getUTCSeconds(),
  };
}

/** Start of the KST day containing t. */
export function sod(t: number) {
  return Math.floor((t + KST_OFFSET) / D) * D - KST_OFFSET;
}

/** "N초" / "N분" / "N시간 M분", and "N일 M시간" from 24 hours on. */
export function fmtDur(ms: number) {
  const m = Math.round(ms / MIN);
  if (m < 1) return `${Math.max(0, Math.round(ms / 1000))}초`;
  const h = Math.floor(m / 60);
  if (h >= 24) return `${Math.floor(h / 24)}일 ${h % 24}시간`;
  return h ? `${h}시간 ${m % 60}분` : `${m}분`;
}

export function fmtHM(t: number) {
  const k = kst(t);
  return `${p2(k.hour)}:${p2(k.minute)}`;
}

export function fmtHMS(t: number) {
  const k = kst(t);
  return `${p2(k.hour)}:${p2(k.minute)}:${p2(k.second)}`;
}

export function fmtFull(t: number) {
  const k = kst(t);
  return `${k.month + 1}월 ${k.date}일 ${p2(k.hour)}:${p2(k.minute)}`;
}

export function fmtDate(t: number) {
  const k = kst(t);
  return `${k.year}.${p2(k.month + 1)}.${p2(k.date)}`;
}
