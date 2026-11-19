import { useCallback, useEffect, useRef, useState } from "react";
import type { GuildData, GuildSummary, Person, Session, Snapshot } from "./types";
import { setChannelPalette } from "./colors";

async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
  return res.json() as Promise<T>;
}

export const fetchGuilds = () => getJson<GuildSummary[]>("/api/guilds");
export const fetchSnapshot = (id: string) => getJson<Snapshot>(`/api/guilds/${id}/snapshot`);

export function defaultAvatar(id: string) {
  let n = 0;
  try {
    n = Number((BigInt(id) >> 22n) % 6n);
  } catch {
    n = 0;
  }
  return `https://cdn.discordapp.com/embed/avatars/${n}.png`;
}

/** Flag sessions that started while another person was already in the same channel. */
function markWithOthers(people: Person[]) {
  const byChannel = new Map<string, { pid: string; s: Session }[]>();
  for (const p of people)
    for (const s of p.sessions) {
      const list = byChannel.get(s.ch) ?? [];
      list.push({ pid: p.id, s });
      byChannel.set(s.ch, list);
    }
  for (const list of byChannel.values()) {
    list.sort((x, y) => x.s.s - y.s.s);
    let active: { pid: string; start: number; end: number }[] = [];
    for (const { pid, s } of list) {
      active = active.filter((x) => x.end > s.s);
      // strictly earlier joins only: two people joining in the same millisecond do not count
      s.withOthers = active.some((x) => x.pid !== pid && x.start < s.s);
      active.push({ pid, start: s.s, end: s.online ? Infinity : s.e });
    }
  }
}

export function toGuildData(snap: Snapshot): GuildData {
  const people: Person[] = Object.entries(snap.sessions)
    .map(([id, raw]) => {
      const m = snap.members[id];
      return {
        id,
        name: m?.name ?? `알 수 없음 (${id.slice(-4)})`,
        avatar: m?.avatarUrl ?? defaultAvatar(id),
        sessions: raw.map((r) => ({ ch: r.channel, s: r.join, e: r.leave, online: r.leave === 0, withOthers: false })),
      };
    })
    .filter((p) => p.sessions.length > 0);
  const now = snap.serverTime;
  markWithOthers(people);
  const usage = new Map<string, number>();
  for (const p of people) for (const s of p.sessions) usage.set(s.ch, (usage.get(s.ch) ?? 0) + ((s.online ? now : s.e) - s.s));
  const channels = [...usage.entries()].sort((a, b) => b[1] - a[1]).map(([c]) => c);
  setChannelPalette(channels);
  return { info: snap.guild, people, channels, clockOffset: snap.serverTime - Date.now() };
}

type Listener = (type: string, guildId: string | null) => void;
const listeners = new Set<Listener>();
let source: EventSource | null = null;

/** The bot sends a ping message every 15 seconds; silence longer than this means a dead connection. */
const STALE_MS = 50_000;
let lastSeen = 0;
let missedEvents = false;
let watching = false;

/** Ask every subscriber to reload, after events may have been missed. */
function resync() {
  listeners.forEach((l) => l("resync", null));
}

function reconnect() {
  missedEvents = true;
  source?.close();
  source = null;
  ensureSource();
}

function ensureSource() {
  if (source || typeof EventSource === "undefined") return;
  watchConnection();
  lastSeen = Date.now();
  source = new EventSource("/sse");
  source.onopen = () => {
    lastSeen = Date.now();
    if (missedEvents) {
      missedEvents = false;
      resync();
    }
  };
  source.onmessage = (ev) => {
    lastSeen = Date.now();
    try {
      const { type, data } = JSON.parse(ev.data) as { type: string; data: string | null };
      if (type === "ping") return;
      listeners.forEach((l) => l(type, data));
    } catch {
      /* malformed message */
    }
  };
  source.onerror = () => {
    missedEvents = true;
    source?.close();
    source = null;
    setTimeout(ensureSource, 3000);
  };
}

/**
 * Background tabs can be throttled or frozen and come back with a dead connection or missed events.
 * Reconnect when the connection goes quiet, and reload when the tab becomes visible again.
 */
function watchConnection() {
  if (watching) return;
  watching = true;
  const wake = () => {
    if (document.visibilityState !== "visible") return;
    if (!source || source.readyState === EventSource.CLOSED || Date.now() - lastSeen > STALE_MS) reconnect();
    else resync();
  };
  document.addEventListener("visibilitychange", wake);
  window.addEventListener("pageshow", wake);
  window.addEventListener("online", wake);
  window.setInterval(() => {
    if (source && Date.now() - lastSeen > STALE_MS) reconnect();
  }, 20_000);
}

/** Subscribe to watcher change events ("change" / "members" with a guild id). */
export function useWatcherEvents(listener: Listener) {
  const ref = useRef(listener);
  ref.current = listener;
  useEffect(() => {
    const l: Listener = (t, g) => ref.current(t, g);
    listeners.add(l);
    ensureSource();
    return () => {
      listeners.delete(l);
    };
  }, []);
}

function useDebounced(fn: () => void, ms: number) {
  const timer = useRef<number | null>(null);
  const ref = useRef(fn);
  ref.current = fn;
  return useCallback(() => {
    if (timer.current) window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => ref.current(), ms);
  }, [ms]);
}

export function useGuilds() {
  const [guilds, setGuilds] = useState<GuildSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(() => {
    fetchGuilds().then(setGuilds, (e) => setError(String(e)));
  }, []);
  useEffect(load, [load]);
  const reload = useDebounced(load, 2000);
  useWatcherEvents(() => reload());
  return { guilds, error };
}

export function useGuildData(guildId: string) {
  const [state, setState] = useState<{ id: string; data: GuildData | null; error: string | null }>({ id: guildId, data: null, error: null });
  const load = useCallback(() => {
    fetchSnapshot(guildId).then(
      (snap) => setState({ id: guildId, data: toGuildData(snap), error: null }),
      (e) => setState((prev) => ({ id: guildId, data: prev.id === guildId ? prev.data : null, error: String(e) })),
    );
  }, [guildId]);
  useEffect(() => {
    setState((prev) => (prev.id === guildId ? prev : { id: guildId, data: null, error: null }));
    load();
  }, [guildId, load]);
  const reload = useDebounced(load, 800);
  useWatcherEvents((_, g) => {
    if (g == null || g === guildId) reload();
  });
  const current = state.id === guildId ? state : { data: null, error: null };
  return { data: current.data, error: current.error };
}
