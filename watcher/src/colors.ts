const HUES = [150, 4, 212, 280, 42, 180, 330, 95, 25, 255];

const cache = new Map<string, number>();
const names = new Map<string, string>();

/** Channel display names by id for the guild on screen (deleted channels have no name). */
export function setChannelNames(map: Record<string, string | null>) {
  names.clear();
  for (const [id, name] of Object.entries(map)) names.set(id, name ?? "삭제된 채널");
}

/** Display name of a channel id. */
export const chName = (channel: string) => names.get(channel) ?? "알 수 없는 채널";

/** Give a guild's channels distinct hues (sorted by id so colors stay stable across renames). */
export function setChannelPalette(channels: string[]) {
  cache.clear();
  [...channels].sort((a, b) => a.localeCompare(b)).forEach((c, i) => cache.set(c, HUES[i % HUES.length]));
}

/** Stable hue per channel id. */
export function hueOf(channel: string) {
  let h = cache.get(channel);
  if (h == null) {
    let x = 0;
    for (const c of channel) x = (x * 31 + c.charCodeAt(0)) >>> 0;
    h = HUES[x % HUES.length];
    cache.set(channel, h);
  }
  return h;
}

export const barColor = (channel: string) => `hsl(${hueOf(channel)}, 48%, 40%)`;
export const solidColor = (channel: string) => `hsl(${hueOf(channel)}, 55%, 45%)`;
