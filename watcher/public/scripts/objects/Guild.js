import { endOfDay, isToday, startOfDay } from "../utils/datetime.js";
import Http from "../utils/http.js";
import { Intervals } from "./platform/Intervals.js";
import User from "./User.js";

export default class Guild {
  constructor() {
    this.id = null;
    this.name = null;
    this.iconUrl = null;
    this.memberCount = 0;

    this.users = {};
  }

  get connectedUserCount() {
    return Object.keys(this.users).reduce((acc, userId) => {
      const user = this.users[userId];
      const online = user.sessions.some((session) => session.online);
      return acc + (online ? 1 : 0);
    }, 0);
  }

  getIntervals() {
    const intervals = new Intervals();
    const userSessions = Object.values(this.users)
      .map((u) => u.sessions)
      .flat();
    const todaySessions = userSessions
      .map((session) => session.copy())
      .filter((session) => {
        return isToday(session.joinTime) || isToday(session.leaveTime);
      })
      .map((session) => {
        let joinTime = session.joinTime;
        let leaveTime = session.leaveTime;
        if (!isToday(joinTime)) {
          joinTime = startOfDay().getTime();
        }
        if (!isToday(leaveTime)) {
          leaveTime = endOfDay().getTime();
        }
        return { ...session, joinTime, leaveTime };
      })
      .sort((a, b) => a.joinTime - b.joinTime);

    for (let session of todaySessions) {
      const start = session.joinTime;
      const end = session.leaveTime;
      intervals.add(start, end);
    }

    return intervals;
  }

  async updateSessions(userSessions, onProgress) {
    let count = 0;
    for (let userId in userSessions) {
      let user = this.users[userId];
      if (user == null) {
        const newUser = await User.create(this.id, userId);
        this.users[userId] = newUser;
        user = newUser;
      }

      const sessions = userSessions[userId];
      user.setSessions(sessions);
      onProgress?.(++count, Object.keys(userSessions).length);
    }
  }

  static async create(guildId) {
    const guildData = await Http.get(`/guild/${guildId}`);
    const guild = new Guild();
    guild.id = guildId;
    guild.name = guildData.name;
    guild.iconUrl = guildData.iconUrl;
    guild.memberCount = guildData.memberCount;

    return guild;
  }

  toString() {
    return JSON.stringify(this);
  }
}
