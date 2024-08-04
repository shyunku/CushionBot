var data, guilds, users;
var selectedGuildId = null;
var currentTimeDisplayInterval = null;
var targetInterval = { start: startOfDay(), end: endOfDay() };
const defaultAvatarUrl = "https://cdn.discordapp.com/embed/avatars/0.png";
