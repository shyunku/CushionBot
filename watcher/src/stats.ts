import type { Person, Session } from "./types";
import { D, H, MIN, sod } from "./time";

export const SHORT_SESSION = 5 * MIN;
export const BOUNCE_MIN_SESSIONS = 5;

export const endOf = (s: Session, now: number) => (s.online ? now : s.e);
export const isOnline = (p: Person) => p.sessions.some((s) => s.online);
export const lastSession = (p: Person) => p.sessions[p.sessions.length - 1];

/** Time a session spends inside [a, b). */
export function overlap(s: Session, a: number, b: number, now: number) {
  return Math.max(0, Math.min(endOf(s, now), b) - Math.max(s.s, a));
}

export function timeIn(p: Person, a: number, b: number, now: number) {
  let t = 0;
  for (const s of p.sessions) t += overlap(s, a, b, now);
  return t;
}

export function lastActivity(p: Person, now: number) {
  const s = lastSession(p);
  return s ? endOf(s, now) : 0;
}

export function todayStats(people: Person[], now: number) {
  const s0 = sod(now);
  let total = 0;
  let count = 0;
  let best: { p: Person; t: number } | null = null;
  for (const p of people) {
    const t = timeIn(p, s0, now, now);
    if (t > 0) count++;
    total += t;
    if (t > 0 && (!best || t > best.t)) best = { p, t };
  }
  return { total, people: count, best, online: people.filter(isOnline).length };
}

/** Number of people connected at evenly spaced points between from and to. */
export function concurrency(people: Person[], from: number, to: number, steps: number, now: number) {
  const out: number[] = [];
  for (let i = 0; i <= steps; i++) {
    const t = from + ((to - from) * i) / steps;
    let n = 0;
    for (const p of people) if (p.sessions.some((s) => s.s <= t && t <= endOf(s, now))) n++;
    out.push(n);
  }
  return out;
}

export function onlineByChannel(people: Person[]) {
  const m = new Map<string, { p: Person; s: Session }[]>();
  for (const p of people)
    for (const s of p.sessions)
      if (s.online) {
        const list = m.get(s.ch) ?? [];
        list.push({ p, s });
        m.set(s.ch, list);
      }
  return [...m.entries()].sort((a, b) => b[1].length - a[1].length);
}

/** Fraction of the last `days` days in which the person was connected during each of `n` slots of the day. */
export function slotFrequency(p: Person, n: number, days: number, now: number) {
  const len = D / n;
  const hits = Array.from({ length: n }, () => new Set<number>());
  const from = sod(now) - (days - 1) * D;
  for (const s of p.sessions) {
    const a = Math.max(s.s, from);
    const b = endOf(s, now);
    if (b <= a) continue;
    for (let day = sod(a); day < b; day += D) {
      const lo = Math.max(a, day), hi = Math.min(b, day + D);
      if (hi <= lo) continue;
      const i0 = Math.floor((lo - day) / len), i1 = Math.min(n - 1, Math.floor((hi - 1 - day) / len));
      for (let i = i0; i <= i1; i++) hits[i].add(day);
    }
  }
  return hits.map((h) => h.size / days);
}

/** 7 x 24 counts of days (by weekday) with a connection in each hour, over the last `days` days. */
export function weekHourHeat(p: Person, days: number, now: number) {
  const heat = Array.from({ length: 7 }, () => Array(24).fill(0) as number[]);
  const seen = new Set<string>();
  const from = sod(now) - (days - 1) * D;
  for (const s of p.sessions) {
    const a = Math.max(s.s, from), b = endOf(s, now);
    for (let t = Math.floor(a / H) * H; t < b; t += H) {
      if (t + H <= a) continue;
      const d = new Date(t);
      const key = `${sod(t)}-${d.getHours()}`;
      if (seen.has(key)) continue;
      seen.add(key);
      heat[d.getDay()][d.getHours()]++;
    }
  }
  return heat;
}

export function userStats(p: Person, now: number, days = 30) {
  const from = sod(now) - (days - 1) * D;
  const ss = p.sessions.filter((s) => endOf(s, now) > from);
  const total = ss.reduce((acc, s) => acc + overlap(s, from, now, now), 0);
  const daySet = new Set(ss.map((s) => sod(Math.max(s.s, from))));
  const daily = Array.from({ length: 14 }, (_, i) => {
    const a = sod(now) - (13 - i) * D;
    return timeIn(p, a, a + D, now);
  });
  const ch = new Map<string, number>();
  for (const s of ss) ch.set(s.ch, (ch.get(s.ch) ?? 0) + overlap(s, from, now, now));
  const shorts = ss.filter((s) => !s.online && s.e - s.s < SHORT_SESSION);
  const bounceDaily = Array.from({ length: 14 }, (_, i) => {
    const a = sod(now) - (13 - i) * D;
    return shorts.filter((s) => s.s >= a && s.s < a + D).length;
  });
  return {
    total,
    days: daySet.size,
    avg: ss.length ? total / ss.length : 0,
    daily,
    channels: [...ch.entries()].sort((a, b) => b[1] - a[1]),
    bounce: {
      count: shorts.length,
      all: ss.length,
      ratio: ss.length ? shorts.length / ss.length : 0,
      perDay: daySet.size ? shorts.length / daySet.size : 0,
      daily: bounceDaily,
    },
  };
}

export type Period = "all" | "30d" | "7d";
export type MetricKey = "total" | "count" | "days" | "avg" | "longest" | "bounce";

export function metricsOf(p: Person, period: Period, now: number): Record<MetricKey, number | null> {
  const from = period === "30d" ? now - 30 * D : period === "7d" ? now - 7 * D : 0;
  const ss = p.sessions.filter((s) => endOf(s, now) > from);
  const total = ss.reduce((acc, s) => acc + overlap(s, from, now, now), 0);
  const shorts = ss.filter((s) => !s.online && s.e - s.s < SHORT_SESSION).length;
  return {
    total,
    count: ss.length,
    days: new Set(ss.map((s) => sod(Math.max(s.s, from)))).size,
    avg: ss.length ? total / ss.length : 0,
    longest: ss.reduce((acc, s) => Math.max(acc, endOf(s, now) - s.s), 0),
    bounce: ss.length >= BOUNCE_MIN_SESSIONS ? shorts / ss.length : null,
  };
}

export function ranked(people: Person[], key: MetricKey, period: Period, now: number) {
  return people
    .map((p) => ({ p, v: metricsOf(p, period, now)[key] }))
    .filter((x): x is { p: Person; v: number } => x.v != null && x.v > 0)
    .sort((a, b) => b.v - a.v);
}

export function rankOf(people: Person[], person: Person, key: MetricKey, period: Period, now: number) {
  const r = ranked(people, key, period, now);
  const i = r.findIndex((x) => x.p.id === person.id);
  return i < 0 ? null : { no: i + 1, of: r.length };
}
