$(document).ready(async () => {
  data = {};
  guilds = {};
  users = {};

  selectedGuildId = localStorage.getItem("selected_guild_id") ?? Object.keys(data)?.[0] ?? null;
  localStorage.setItem("selected_guild_id", selectedGuildId);

  // resize detect
  window.addEventListener("resize", () => {
    if (selectedGuildId != null) {
      const serverData = data[selectedGuildId];
      displayMainContent();
    }
  });

  fastInterval(async () => {
    data = await httpGet("/data");
    if (selectedGuildId == null || (selectedGuildId != null && data[selectedGuildId] == null)) {
      selectedGuildId = Object.keys(data)?.[0] ?? null;
      localStorage.setItem("selected_guild_id", selectedGuildId);
    }

    for (let guildId in data) {
      if (guilds[guildId] == null) {
        const guildData = await httpGet(`/guild/${guildId}`);
        guilds[guildId] = guildData;
      }

      const userSessions = data[guildId];
      for (let userId in userSessions) {
        if (users[userId] != null) continue;
        const userData = await httpGet(`/user/${guildId}/${userId}`);
        users[userId] = userData;
      }
    }
    displaySidebar();
    if (selectedGuildId != null) {
      displayMainContent();
    }
  }, 1000 * 15);

  displaySidebar();

  console.log("data", data);
  console.log("guilds", guilds);
  console.log("users", users);
});
