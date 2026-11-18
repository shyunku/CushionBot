// Find (and optionally delete) voice sessions in datafiles/access_logs.json that run longer than
// a threshold. Such sessions were left open while the bot was stopped and later closed at restart,
// so they count the downtime as connected time.
//
// Usage (from the repository root, with the bot stopped: ./stop.sh):
//   node scripts/clean-long-sessions.mjs                 list sessions longer than 24 hours
//   node scripts/clean-long-sessions.mjs --hours 12      use another threshold
//   node scripts/clean-long-sessions.mjs --delete 1,3    delete listed sessions by number
//   node scripts/clean-long-sessions.mjs --delete all    delete every listed session
//
// Deleting writes a backup next to the file first and keeps the file's modified time, because the
// bot uses that time to close sessions that were still open when it stopped.
import { copyFileSync, readFileSync, statSync, utimesSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const args = process.argv.slice(2);
const opt = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
};
const hours = Number(opt("--hours") ?? 24);
const del = opt("--delete");
const file = resolve(opt("--file") ?? "datafiles/access_logs.json");
if (!(hours > 0)) throw new Error("--hours must be a positive number");

const H = 3600e3;
const kst = (t) => new Date(t + 9 * H).toISOString().slice(0, 16).replace("T", " ");
const dur = (ms) => `${Math.floor(ms / (24 * H))}d ${Math.floor((ms % (24 * H)) / H)}h`;

const stat = statSync(file);
const data = JSON.parse(readFileSync(file, "utf8"));
const found = [];
for (const [guildId, users] of Object.entries(data)) {
  for (const [userId, sessions] of Object.entries(users)) {
    for (const s of sessions) {
      if (s.joinTime && s.leaveTime && s.leaveTime - s.joinTime > hours * H) {
        found.push({ guildId, userId, s });
      }
    }
  }
}
found.sort((a, b) => b.s.leaveTime - b.s.joinTime - (a.s.leaveTime - a.s.joinTime));

console.log(`${file}\nsessions longer than ${hours}h: ${found.length}\n`);
found.forEach(({ guildId, userId, s }, i) => {
  console.log(
    `${String(i + 1).padStart(3)}. guild ${guildId}  user ${userId}  channel ${s.channelId}\n` +
      `     ${kst(s.joinTime)} ~ ${kst(s.leaveTime)} KST  (${dur(s.leaveTime - s.joinTime)})`,
  );
});

if (!del) {
  if (found.length) console.log("\nNothing changed. Stop the bot, then pass --delete <numbers|all> to delete.");
  process.exit(0);
}

const picks = del === "all" ? found.map((_, i) => i + 1) : del.split(",").map((x) => Number(x.trim()));
for (const n of picks) {
  if (!Number.isInteger(n) || n < 1 || n > found.length) throw new Error(`no session numbered ${n}`);
}
const remove = new Set(picks.map((n) => found[n - 1].s));
for (const users of Object.values(data)) {
  for (const [userId, sessions] of Object.entries(users)) {
    users[userId] = sessions.filter((s) => !remove.has(s));
  }
}

const backup = `${file}.bak-${Date.now()}`;
copyFileSync(file, backup);
writeFileSync(file, JSON.stringify(data, null, 2));
utimesSync(file, stat.atime, stat.mtime);
console.log(`\nDeleted ${remove.size} sessions. Backup: ${backup}`);
