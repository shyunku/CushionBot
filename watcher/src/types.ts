export type GuildSummary = {
  id: string;
  name: string;
  iconUrl: string | null;
  memberCount: number;
  online: number;
};

export type RawSession = { channel: string; join: number; leave: number };

export type Snapshot = {
  guild: Omit<GuildSummary, "online">;
  serverTime: number;
  members: Record<string, { name: string | null; avatarUrl: string | null }>;
  sessions: Record<string, RawSession[]>;
};

/** A voice session. `e` is 0 while the person is still connected. */
export type Session = {
  ch: string;
  s: number;
  e: number;
  online: boolean;
  /** someone else was already in the channel when this session started */
  withOthers: boolean;
};

export type Person = {
  id: string;
  name: string;
  avatar: string;
  sessions: Session[];
};

export type GuildData = {
  info: Snapshot["guild"];
  people: Person[];
  /** channel names, most used first */
  channels: string[];
  clockOffset: number;
};
