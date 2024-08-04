var data, guilds, users;
var selectedGuildId = null;
var currentTimeDisplayInterval = null;
var targetInterval = { start: startOfDay(), end: endOfDay() };
const defaultAvatarUrl = "https://cdn.discordapp.com/embed/avatars/0.png";

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
  }, 1000 * 10);

  displaySidebar();

  console.log("data", data);
  console.log("guilds", guilds);
  console.log("users", users);
});

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

function displayMainContent() {
  const guildId = selectedGuildId;
  const userSessionMap = data[guildId];
  const guildData = guilds[guildId];
  const mainContent = $("#main_content");
  mainContent.empty();
  if (guildId == null) return;
  mainContent.append(`
        <div class="title">${guildData?.name ?? "Unknown"} (${datetimeStr(targetInterval.start)} ~ ${datetimeStr(
    targetInterval.end
  )})</div>
        <div class="options">
            <div class="left">
                <button class="btn" id="prev_day">1일 전</button>
                <button class="btn" id="current_day">오늘</button>
                <button class="btn" id="next_day">1일 후</button>
            </div>
            <div class="right">
                <button class="btn" id="day_unit_1">1일 단위</button>
                <button class="btn" id="day_unit_2">2일 단위</button>
                <button class="btn" id="day_unit_3">3일 단위</button>
                <button class="btn" id="day_unit_7">7일 단위</button>
                <button class="btn" id="day_unit_14">14일 단위</button>
                <button class="btn" id="day_unit_30">30일 단위</button>
                <button class="btn" id="day_unit_90">3달 단위</button>
                <button class="btn" id="day_unit_180">6달 단위</button>
                <button class="btn" id="day_unit_365">1년 단위</button>
            </div>
        </div>
        <div class="content">
            ${Array(25)
              .fill(0)
              .map((_, i) => {
                return `
                    <div class="hour">
                        <div class="hour-label">${`${i}`.padStart(2, "0")}:00</div>
                        <div class="hour-content">
                            <div class="hour-line h-${i}"></div>
                        </div>
                    </div>
                `;
              })
              .join("")}
        </div>
        <div id="sessions"></div>
    `);

  const prevDayBtn = $("#prev_day")[0];
  const todayBtn = $("#current_day")[0];
  const nextDayBtn = $("#next_day")[0];

  prevDayBtn.addEventListener("click", () => {
    targetInterval.start.setDate(targetInterval.start.getDate() - 1);
    targetInterval.end.setDate(targetInterval.end.getDate() - 1);
    displayMainContent();
  });
  todayBtn.addEventListener("click", () => {
    targetInterval.start = startOfDay();
    targetInterval.end = endOfDay();
    displayMainContent();
  });
  nextDayBtn.addEventListener("click", () => {
    targetInterval.start.setDate(targetInterval.start.getDate() + 1);
    targetInterval.end.setDate(targetInterval.end.getDate() + 1);
    displayMainContent();
  });

  const dayUnit1Btn = $("#day_unit_1")[0];
  const dayUnit2Btn = $("#day_unit_2")[0];
  const dayUnit3Btn = $("#day_unit_3")[0];
  const dayUnit7Btn = $("#day_unit_7")[0];
  const dayUnit14Btn = $("#day_unit_14")[0];
  const dayUnit30Btn = $("#day_unit_30")[0];
  const dayUnit90Btn = $("#day_unit_90")[0];
  const dayUnit180Btn = $("#day_unit_180")[0];
  const dayUnit365Btn = $("#day_unit_365")[0];

  const dayUnitHandler = (unit) => {
    targetInterval.start.setDate(targetInterval.end.getDate() - unit);
    displayMainContent();
  };

  dayUnit1Btn.addEventListener("click", () => dayUnitHandler(1));
  dayUnit2Btn.addEventListener("click", () => dayUnitHandler(2));
  dayUnit3Btn.addEventListener("click", () => dayUnitHandler(3));
  dayUnit7Btn.addEventListener("click", () => dayUnitHandler(7));
  dayUnit14Btn.addEventListener("click", () => dayUnitHandler(14));
  dayUnit30Btn.addEventListener("click", () => dayUnitHandler(30));
  dayUnit90Btn.addEventListener("click", () => dayUnitHandler(90));
  dayUnit180Btn.addEventListener("click", () => dayUnitHandler(180));
  dayUnit365Btn.addEventListener("click", () => dayUnitHandler(365));

  const h0 = $(`.hour-line.h-0`)[0];
  const h24 = $(`.hour-line.h-24`)[0];

  const padding = 5;

  const lx = h0.getBoundingClientRect().left;
  const ly = h0.getBoundingClientRect().top + padding;
  const rx = h24.getBoundingClientRect().right;
  const ry = h24.getBoundingClientRect().bottom - padding;

  const sessions = [];
  for (let userId in userSessionMap) {
    const user = users[userId];
    const userSessions = userSessionMap[userId];
    for (let i = 0; i < userSessions.length; i++) {
      const session = { ...userSessions[i] };
      session.user = user;
      session.id = hashStrAsStr(userId + "-" + session.joinTime);
      session.joinTimeStr = timeStr(session.joinTime);
      session.leaveTimeStr = session.leaveTime ? timeStr(session.leaveTime) : null;

      const start = new Date(session.joinTime);
      const end = session.leaveTime !== 0 ? new Date(session.leaveTime) : new Date();

      const startEqual = isInInterval(start);
      const endEqual = end != null && isInInterval(end);
      if (startEqual || endEqual) {
        if (!startEqual) {
          session.joinTime = new Date(targetInterval.start).getTime();
        }
        if (!endEqual && end != null) {
          session.leaveTime = new Date(targetInterval.end).getTime();
        }
        sessions.push(session);
      }
    }
  }

  const filteredUserSessions = {};
  for (let session of sessions) {
    if (filteredUserSessions[session.user.id] == null) {
      filteredUserSessions[session.user.id] = [];
    }
    filteredUserSessions[session.user.id].push(session);
  }

  const sorted = sessions.sort((s1, s2) => s1.joinTime - s2.joinTime);
  const seatUsers = {};
  const userSeat = {};
  const maxYindex = 28;

  const overlap = (u1, u2) => {
    const s1 = userSessionMap[u1] ?? [];
    const s2 = userSessionMap[u2] ?? [];
    for (let i = 0; i < s1.length; i++) {
      for (let j = 0; j < s2.length; j++) {
        const x1 = s1[i].joinTime;
        const x2 = s1[i].leaveTime || Infinity;
        const y1 = s2[j].joinTime;
        const y2 = s2[j].leaveTime || Infinity;
        if (x2 >= y1 && y2 >= x1) {
          return true;
        }
      }
    }
  };

  const userIdList = Object.keys(userSessionMap);
  const sortedUserIds = userIdList.sort((u1, u2) => {
    const s1 = filteredUserSessions?.[u1] ?? [];
    const s2 = filteredUserSessions?.[u2] ?? [];
    if (s1.length === 0) return 1;
    if (s2.length === 0) return -1;

    const lastSession1 = s1[s1.length - 1];
    const lastSession2 = s2[s2.length - 1];

    const lastLeaveTime1 = lastSession1.leaveTime || Date.now();
    const lastLeaveTime2 = lastSession2.leaveTime || Date.now();
    if (lastLeaveTime1 !== lastLeaveTime2) {
      return lastLeaveTime2 - lastLeaveTime1;
    }

    if (lastSession1.channelName !== lastSession2.channelName) {
      return (lastSession1.channelName ?? "Unknown").localeCompare(lastSession2.channelName ?? "Unknown");
    }

    const duration1 = s1.reduce((acc, cur) => acc + ((cur.leaveTime || Date.now()) - cur.joinTime), 0);
    const duration2 = s2.reduce((acc, cur) => acc + ((cur.leaveTime || Date.now()) - cur.joinTime), 0);
    if (duration1 !== duration2) {
      return duration2 - duration1;
    }

    const user1 = users[u1];
    const user2 = users[u2];
    return (user1.effectiveName ?? "Unknown").localeCompare(user2.effectiveName ?? "Unknown");
  });

  for (let userId of sortedUserIds) {
    if (userSeat[userId] != null) continue;
    const emptySeatExists = Array(maxYindex)
      .fill(0)
      .map((_, i) => i)
      .some((i) => seatUsers[i] == null || seatUsers[i].length === 0);
    for (let i = 0; i < maxYindex; i++) {
      const users = seatUsers[i] ?? [];
      if (emptySeatExists && users.length > 0) continue;
      let ok = true;
      for (let u of users) {
        if (overlap(userId, u)) {
          ok = false;
          break;
        }
      }
      if (ok) {
        userSeat[userId] = i;
        if (seatUsers[i] == null) seatUsers[i] = [];
        seatUsers[i].push(userId);
        break;
      }
    }
  }

  const sessionsElem = $("#sessions");
  for (let i = 0; i < sorted.length; i++) {
    const session = sorted[i];
    const start = new Date(session.joinTime);
    const end = session.leaveTime > 0 ? new Date(session.leaveTime) : new Date();
    const isCurrent = (session.leaveTime || 0) === 0;
    const duration = end - start;

    const total = targetInterval.end.getTime() - targetInterval.start.getTime();
    const startX = start.getTime() - targetInterval.start.getTime();
    const endX = end.getTime() - targetInterval.start.getTime();
    const startR = startX / total;
    const endR = endX / total;

    const x = lx + startR * (rx - lx);
    const yIndex = userSeat[session.user.id];
    const y = ly + ((yIndex + 1) * (ry - ly)) / (maxYindex + 1);
    const w = (endR - startR) * (rx - lx);
    const color = getColorByStringWithBrightness(session.channelName, 0.3, 0.5);

    sessionsElem.append(`
            <div class="session ss-${session.id} ${
      isCurrent ? "current" : ""
    }" style="left: ${x}px; top: ${y}px; width: ${w}px; background-color: ${color};">
                <div class="session-content">
                    <img class="icon" src="${session.user.avatarUrl ?? defaultAvatarUrl}" alt="User Icon">
                    <div class="name">${session.user.effectiveName ?? "Unknown"}</div>
                </div>     
            </div>
            <div class="session-tooltip st-${session.id}" style="left: ${x + w / 2}px; top: ${y}px;">
                <div class="user-info">
                    <img class="icon" src="${session.user.avatarUrl ?? defaultAvatarUrl}" alt="User Icon"/>
                    <div class="name">${session.user.effectiveName ?? "Unknown"}</div>
                </div>
                <div class="channel-info">
                    <div class="channel" style="color: ${color};">"${session.channelName}" 채널 (${session.id})</div>
                </div>
                <div class="time-info">
                    <div class="time join">${timeStr(session.joinTime)} 접속</div>
                    ${
                      session.leaveTime
                        ? `
                        <div class="time leave">${timeStr(session.leaveTime)} 퇴장</div>
                    `
                        : ""
                    }
                    <div class="duration">${durationStr(duration)} 동안 연결</div>
                </div>
            </div>         
        `);

    const sessionElem = $(`.session.ss-${session.id}`)[0];
    const tooltipElem = $(`.session-tooltip.st-${session.id}`)[0];
    sessionElem.addEventListener("mouseover", () => {
      tooltipElem.style.display = "flex";
    });
    sessionElem.addEventListener("mouseout", () => {
      tooltipElem.style.display = "none";
    });
  }

  mainContent.append(`
        <div id="time_display">
            <div class="curtime">${timeStr()}</div>
        </div>
    `);

  if (currentTimeDisplayInterval != null) {
    clearInterval(currentTimeDisplayInterval);
  }
  currentTimeDisplayInterval = fastInterval(() => {
    const total = 24 * 60 * 60 * 1000;
    const now = new Date();
    const elapsed =
      now.getHours() * 60 * 60 * 1000 + now.getMinutes() * 60 * 1000 + now.getSeconds() * 1000 + now.getMilliseconds();
    const ratio = elapsed / total;
    const x = lx + ratio * (rx - lx);

    const timeElem = $("#time_display")[0];
    timeElem.style.top = `${h0.getBoundingClientRect().top - 5}px`;
    timeElem.style.left = `${x}px`;
    timeElem.style.height = `${h0.getBoundingClientRect().bottom - h0.getBoundingClientRect().top}px`;
    const curTimeElem = $("#time_display .curtime")[0];
    curTimeElem.style.left = `${x}px`;
    curTimeElem.style.top = `${h0.getBoundingClientRect().top - 13}px`;
    curTimeElem.innerText = timeStr();
  }, 1000);
}

/**
 *
 * @param {Date | number} time
 * @returns
 */
function isInInterval(time) {
  const t = new Date(time);
  return t >= targetInterval.start && t <= targetInterval.end;
}

async function httpGet(url) {
  return new Promise((resolve, reject) => {
    try {
      if (!url.startsWith("/")) {
        url = `/${url}`;
      }
      url = `http://${botHost}:${botPort}${url}`;

      $.ajax({
        url,
        type: "GET",
        success: (data) => {
          resolve(data);
        },
        error: (err) => {
          reject(err);
        },
      });
    } catch (err) {
      reject(err);
    }
  });
}
