export type GuildSummary = {
  id: string;
  name: string;
  iconUrl: string | null;
  memberCount: number;
  online: number;
};

/** `channel` is the channel id; names come from Snapshot.channels. */
export type RawSession = { channel: string; join: number; leave: number };

export type Snapshot = {
  guild: Omit<GuildSummary, "online">;
  serverTime: number;
  members: Record<string, { name: string | null; avatarUrl: string | null }>;
  sessions: Record<string, RawSession[]>;
  /** channel id -> name (null for deleted channels) */
  channels: Record<string, string | null>;
};

/** A voice session. `e` is 0 while the person is still connected. */
export type Session = {
  /** position in the person's list; unique per person (join times can repeat in old records) */
  id: number;
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
