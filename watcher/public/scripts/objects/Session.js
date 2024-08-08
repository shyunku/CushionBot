export default class Session {
  constructor(data) {
    this.channelName = data.channelName;
    this.joinTime = data.joinTime;
    this.leaveTime = data.leaveTime || Date.now();
    this.online = data.leaveTime == 0;

    if (this.leaveTime < this.joinTime) {
      this.leaveTime = this.joinTime;
    }
  }
}
