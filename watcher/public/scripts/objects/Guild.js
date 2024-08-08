import Http from "../utils/http.js";
import User from "./User.js";

export default class Guild {
  constructor() {
    this.id = null;
    this.name = null;
    this.iconUrl = null;
    this.memberCount = 0;

    this.users = {};
  }

  async updateSessions(userSessions) {
    for (let userId in userSessions) {
      let user = this.users[userId];
      if (user == null) {
        const newUser = await User.create(this.id, userId);
        this.users[userId] = newUser;
        user = newUser;
      }

      const sessions = userSessions[userId];
      user.setSessions(sessions);
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
}
