const Modes = {
  INTERVAL: "interval",
  DAY: "day",
  WEEK: "week",
};

var data = {},
  guilds = {},
  users = {};
var selectedGuildId = null;
var currentTimeDisplayInterval = null;
var targetInterval = { start: startOfDay(), end: endOfDay() };
var mode = Modes.INTERVAL;
const defaultAvatarUrl = "https://cdn.discordapp.com/embed/avatars/0.png";
