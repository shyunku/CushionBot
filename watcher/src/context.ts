import { createContext, useContext, useEffect, useState } from "react";
import type { GuildData, GuildSummary } from "./types";

export type GuildCtx = {
  guildId: string;
  data: GuildData;
  now: number;
  /** "/servers/{id}" or "/channel/{id}" (shared link) */
  base: string;
  shared: boolean;
  guildSummary: GuildSummary | null;
  /** every server, for the phone server picker */
  guilds: GuildSummary[] | null;
  share: () => void;
};

export const GuildContext = createContext<GuildCtx | null>(null);

export function useGuild() {
  const ctx = useContext(GuildContext);
  if (!ctx) throw new Error("GuildContext missing");
  return ctx;
}

/** Current time aligned to the bot's clock, refreshed every `ms`. */
export function useNow(offset: number, ms = 5000) {
  const [now, setNow] = useState(() => Date.now() + offset);
  useEffect(() => {
    setNow(Date.now() + offset);
    const t = window.setInterval(() => setNow(Date.now() + offset), ms);
    return () => window.clearInterval(t);
  }, [offset, ms]);
  return now;
}
