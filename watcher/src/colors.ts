const HUES = [150, 4, 212, 280, 42, 180, 330, 95, 25, 255];

const cache = new Map<string, number>();

/** Give a guild's channels distinct hues (sorted by name so colors stay stable). */
export function setChannelPalette(channels: string[]) {
  cache.clear();
  [...channels].sort((a, b) => a.localeCompare(b)).forEach((c, i) => cache.set(c, HUES[i % HUES.length]));
}

/** Stable hue per channel name. */
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
