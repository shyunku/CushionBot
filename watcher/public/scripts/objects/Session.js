import User from "./User.js";

export default class Session {
  constructor(user, data) {
    /** @type {User} */
    this.user = user;

    this.channelName = data.channelName;
    this.joinTime = data.joinTime;
    this.leaveTime = data.leaveTime || Date.now();
    this.online = data.leaveTime == 0;

    if (this.leaveTime < this.joinTime) {
      this.leaveTime = this.joinTime;
    }
  }

  copy() {
    return new Session(this.user, {
      channelName: this.channelName,
      joinTime: this.joinTime,
      leaveTime: this.leaveTime,
      online: this.online,
    });
  }
}
