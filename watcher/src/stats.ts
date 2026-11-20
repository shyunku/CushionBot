import type { Person, Session } from "./types";
import { D, H, MIN, kst, sod } from "./time";

export const SHORT_SESSION = 5 * MIN;
export const BOUNCE_MIN_SESSIONS = 5;

/** Sessions that count toward the bounce rate: joining a channel where someone already was. */
const bounceBase = (ss: Session[]) => ss.filter((s) => s.withOthers);
/** Bounces: left within SHORT_SESSION after joining someone. */
const bounces = (ss: Session[]) => ss.filter((s) => s.withOthers && !s.online && s.e - s.s < SHORT_SESSION);

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
      const k = kst(t);
      const key = `${sod(t)}-${k.hour}`;
      if (seen.has(key)) continue;
      seen.add(key);
      heat[k.weekday][k.hour]++;
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
  const shorts = bounces(ss);
  const base = bounceBase(ss).length;
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
      all: base,
      ratio: base ? shorts.length / base : 0,
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
  const shorts = bounces(ss).length;
  const base = bounceBase(ss).length;
  return {
    total,
    count: ss.length,
    days: new Set(ss.map((s) => sod(Math.max(s.s, from)))).size,
    avg: ss.length ? total / ss.length : 0,
    longest: ss.reduce((acc, s) => Math.max(acc, endOf(s, now) - s.s), 0),
    bounce: base >= BOUNCE_MIN_SESSIONS ? shorts / base : null,
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

/** How many times each weekday (0 = Sunday) occurs in the last `days` days. */
export function weekdayCounts(days: number, now: number) {
  const out = Array(7).fill(0) as number[];
  for (let i = 0; i < days; i++) out[kst(sod(now) - i * D).weekday]++;
  return out;
}

/** Server-wide statistics over the last `days` days plus a per-day grass for the whole record. */
export function serverStats(people: Person[], now: number, days = 30) {
  const n = 144;
  const from = sod(now) - (days - 1) * D;
  const active = people.filter((p) => p.sessions.some((s) => endOf(s, now) > from));
  // average number of people connected during each 10-minute slot / weekday-hour
  const slots = Array(n).fill(0) as number[];
  const heatSum = Array.from({ length: 7 }, () => Array(24).fill(0) as number[]);
  for (const p of active) {
    slotFrequency(p, n, days, now).forEach((v, i) => (slots[i] += v));
    weekHourHeat(p, days, now).forEach((row, d) => row.forEach((v, h) => (heatSum[d][h] += v)));
  }
  const wc = weekdayCounts(days, now);
  const heat = heatSum.map((row, d) => row.map((v) => (wc[d] ? v / wc[d] : 0)));

  // per-day totals for the last year (records are kept for a year)
  const start = sod(now) - 364 * D;
  const dayCount = Math.round((sod(now) - start) / D) + 1;
  const totals = Array(dayCount).fill(0) as number[];
  const peopleSets = Array.from({ length: dayCount }, () => new Set<string>());
  for (const p of people) {
    for (const s of p.sessions) {
      const a = Math.max(s.s, start), b = endOf(s, now);
      for (let day = sod(a); day < b; day += D) {
        const t = Math.min(b, day + D) - Math.max(a, day);
        if (t <= 0) continue;
        const i = Math.round((day - start) / D);
        if (i < 0 || i >= dayCount) continue;
        totals[i] += t;
        peopleSets[i].add(p.id);
      }
    }
  }
  const grass = totals.map((total, i) => ({ day: start + i * D, total, people: peopleSets[i].size }));
  // DST-safe day starts

  const recent = grass.slice(-days);
  const total = active.reduce((acc, p) => acc + timeIn(p, from, now, now), 0);
  const avgPeople = recent.reduce((acc, g) => acc + g.people, 0) / days;
  const peakSlot = slots.indexOf(Math.max(...slots));
  const dayLoad = heat.map((row) => row.reduce((acc, v) => acc + v, 0));
  const peakDay = dayLoad.indexOf(Math.max(...dayLoad));
  return { slots, heat, weekdays: wc, grass, total, avgPeople, peakSlot, peakDay, active: active.length };
}

export type FreqPeriod = "30d" | "90d" | "all";

/**
 * Day starts covered by a period: the last 30 days, the last 90 days, or every day since the first record
 * (records keep a year). The 90-day and full periods do not reach back before the first record.
 */
export function periodDays(people: Person[], period: FreqPeriod, now: number) {
  let count = 30;
  if (period !== "30d") {
    const first = Math.min(now, ...people.map((p) => p.sessions[0]?.s ?? now));
    count = Math.min(period === "90d" ? 90 : 365, Math.round((sod(now) - sod(first)) / D) + 1);
  }
  return Array.from({ length: count }, (_, i) => sod(now) - i * D);
}

/**
 * Per 10-minute slot of the day: (person, day) pairs connected in that slot divided by the number of days.
 * For one person this is the share of days they were on; for a whole server it is the average number of people.
 */
export function slotsFor(people: Person[], period: FreqPeriod, weekday: number | null, now: number) {
  const n = 144;
  const len = D / n;
  const days = periodDays(people, period, now).filter((d) => weekday == null || kst(d).weekday === weekday);
  const daySet = new Set(days);
  const out = Array(n).fill(0) as number[];
  for (const p of people) {
    const hit = new Set<string>();
    for (const s of p.sessions) {
      const end = endOf(s, now);
      for (let t = Math.floor(s.s / len) * len; t < end; t += len) {
        const day = sod(t);
        if (!daySet.has(day)) continue;
        const i = Math.floor((t - day) / len);
        const key = `${day}:${i}`;
        if (!hit.has(key)) {
          hit.add(key);
          out[i]++;
        }
      }
    }
  }
  // today counts only for slots that have already started; later slots would read low otherwise
  const today = sod(now);
  const nowSlot = Math.floor((now - today) / len);
  const dayCounts = out.map((_, i) => days.length - (daySet.has(today) && i > nowSlot ? 1 : 0));
  return { values: out.map((v, i) => v / Math.max(1, dayCounts[i])), dayCounts };
}

/**
 * Weekday x hour counts of (person, day) pairs connected in that hour, plus how many days each cell
 * covers (today counts only for hours that have started).
 */
export function heatFor(people: Person[], period: FreqPeriod, now: number) {
  const days = periodDays(people, period, now);
  const daySet = new Set(days);
  const heat = Array.from({ length: 7 }, () => Array(24).fill(0) as number[]);
  const weekdays = Array(7).fill(0) as number[];
  for (const d of days) weekdays[kst(d).weekday]++;
  for (const p of people) {
    const seen = new Set<string>();
    for (const s of p.sessions) {
      const end = endOf(s, now);
      for (let t = Math.floor(s.s / H) * H; t < end; t += H) {
        const day = sod(t);
        if (!daySet.has(day)) continue;
        const k = kst(t);
        const key = `${day}:${k.hour}`;
        if (seen.has(key)) continue;
        seen.add(key);
        heat[k.weekday][k.hour]++;
      }
    }
  }
  const nowK = kst(now);
  const cellDays = weekdays.map((count, d) => Array.from({ length: 24 }, (_, h) => count - (d === nowK.weekday && h > nowK.hour ? 1 : 0)));
  return { heat, cellDays };
}

/** Window around the current time used for "chance to be on now". */
export const CHANCE_WINDOW = 30 * MIN;
/** "Chance to leave now" counts a leave within this time. */
export const LEAVE_WINDOW = 30 * MIN;
/** Sessions this close are one stay (a channel move ends a session and starts the next at once). */
const MOVE_GAP = 1000;
/** Chances look back this many days (about three months)... */
const CHANCE_DAYS = 91;
/** ...and count the days within this many days twice, so recent habits weigh more. */
const CHANCE_RECENT_DAYS = 30;

/** The same weekday as today over the last CHANCE_DAYS days (today excluded), with their weights. */
function chanceDays(now: number) {
  const today = kst(now).weekday;
  return Array.from({ length: CHANCE_DAYS }, (_, i) => ({ day: sod(now) - (i + 1) * D, weight: i < CHANCE_RECENT_DAYS ? 2 : 1 })).filter(
    (x) => kst(x.day).weekday === today,
  );
}

/**
 * Chance that a person is connected around this time: over the same weekday in the last three months
 * (today excluded, the last 30 days counted twice), the weighted share of days with a session within
 * ±CHANCE_WINDOW of the current time of day. `days`/`hits` are plain counts.
 */
export function chanceNow(p: Person, now: number) {
  const tod = now - sod(now);
  let weight = 0;
  let hitWeight = 0;
  let hits = 0;
  const days = chanceDays(now);
  for (const { day, weight: w } of days) {
    const a = day + tod - CHANCE_WINDOW;
    const b = day + tod + CHANCE_WINDOW;
    weight += w;
    if (p.sessions.some((s) => s.s < b && endOf(s, now) > a)) {
      hitWeight += w;
      hits++;
    }
  }
  return { chance: weight ? hitWeight / weight : 0, hits, days: days.length };
}

/**
 * Chance that a person leaves soon: over the same days as chanceNow, of the days on which the person was
 * connected at the current time of day, the weighted share that ended within LEAVE_WINDOW.
 * `days` is the number of such days (0 = no record to judge by).
 */
export function leaveChance(p: Person, now: number) {
  const tod = now - sod(now);
  let weight = 0;
  let hitWeight = 0;
  let days = 0;
  let hits = 0;
  for (const { day, weight: w } of chanceDays(now)) {
    const t = day + tod;
    const i = p.sessions.findIndex((x) => x.s <= t && endOf(x, now) > t);
    if (i < 0) continue;
    // a channel move ends one session and starts the next at the same moment: follow it to the real leave
    let end = endOf(p.sessions[i], now);
    for (let j = i + 1; j < p.sessions.length && p.sessions[j].s - end <= MOVE_GAP; j++) end = Math.max(end, endOf(p.sessions[j], now));
    days++;
    weight += w;
    if (end <= t + LEAVE_WINDOW) {
      hitWeight += w;
      hits++;
    }
  }
  return { chance: weight ? hitWeight / weight : 0, hits, days };
}
