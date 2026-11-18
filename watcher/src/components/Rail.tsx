import { Link } from "react-router-dom";
import type { GuildSummary } from "../types";
import GuildIcon from "./GuildIcon";

export default function Rail({ guilds, selected }: { guilds: GuildSummary[] | null; selected: string }) {
  return (
    <nav className="rail">
      <Link to="/" className="logo" title="Cushion Watch">C</Link>
      <div className="sep" />
      {(guilds ?? []).map((g) => (
        <Link key={g.id} to={`/servers/${g.id}`} className={`srv ${g.id === selected ? "sel" : ""}`} title={g.name}>
          <GuildIcon name={g.name} url={g.iconUrl} size={44} round />
          {g.online > 0 && <span className="live">{g.online}</span>}
        </Link>
      ))}
    </nav>
  );
}
