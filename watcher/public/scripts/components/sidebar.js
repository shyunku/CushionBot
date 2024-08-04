function displaySidebar() {
  const todayElem = $("#today");
  todayElem.empty();

  for (let guildId in data) {
    const guildData = guilds[guildId];
    const serverName = guildData?.name ?? "Unknown";
    const guildIconUrl = guildData?.iconUrl ?? null;
    todayElem.append(`
            <div class="today-item server-${guildId}">
                <div class="header">
                    <img src="${guildIconUrl}" alt="Server Icon" class="icon">
                    <div class="name">${serverName}</div>
                </div>
                <div class="content">
                
                </div>
            </div>
        `);
    const todayItemElem = $(`.today-item.server-${guildId}`)[0];
    if (selectedGuildId === guildId) {
      todayItemElem.classList.add("selected");
    }

    todayItemElem.addEventListener("click", () => {
      if (selectedGuildId === guildId) return;
      if (selectedGuildId != null) {
        const prevElem = $(`.today-item.server-${selectedGuildId}`)[0];
        prevElem.classList.remove("selected");
      }
      selectedGuildId = guildId;
      todayItemElem.classList.add("selected");
      localStorage.setItem("selected_guild_id", selectedGuildId);
      displayMainContent();
    });
  }
}
