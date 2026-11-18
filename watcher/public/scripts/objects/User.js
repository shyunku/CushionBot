import Http from "../utils/http.js";
import Session from "./Session.js";

export default class User {
  constructor() {
    this.guildId = null;
    this.id = null;
    this.effectiveName = null;
    this.nickname = null;
    this.avatarUrl = null;

    this.sessions = [];
  }

  get totalDuration() {
    return this.sessions.reduce((acc, session) => acc + session.duration, 0);
  }

  setSessions(sessions) {
    this.sessions = [];
    for (let i = 0; i < sessions.length; i++) {
      const session = new Session(this, sessions[i]);
      this.sessions.push(session);
    }
  }

  static async create(guildId, userId) {
    let user = new User();
    user.guildId = guildId;
    user.id = userId;
    try {
      const userData = await Http.get(`/user/${guildId}/${userId}`);
      user.effectiveName = userData.effectiveName;
      user.nickname = userData.nickname;
      user.avatarUrl = userData.avatarUrl;
    } catch (err) {
      user.effectiveName = "Unknown";
      user.nickname = "Unknown";
      user.avatarUrl = null;
    }
    return user;
  }
}
