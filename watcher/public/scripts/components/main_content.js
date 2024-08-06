var sessionList = [];
var userSeats = {};
var updateSessionsInterval = null;

const boxLeftPad = 16;

function displayMainContent() {
  const guildId = selectedGuildId;
  const userSessionMap = data[guildId];
  const guildData = guilds[guildId];
  const mainContent = $("#main_content");
  mainContent.empty();
  if (guildId == null) return;

  let intervalText = `${dayjs(targetInterval.start).format("YY.MM.DD")} ~ ${dayjs(targetInterval.end).format(
    "YY.MM.DD"
  )}`;
  if (dayjs(targetInterval.start).format("YY.MM.DD") === dayjs(targetInterval.end).format("YY.MM.DD")) {
    intervalText = `${dayjs(targetInterval.start).format("YY.MM.DD")}`;
  }

  mainContent.append(`
        <div id="channel_title" class="title">${guildData?.name ?? "Unknown"} (${intervalText})</div>
        <div class="options">
            <div class="left">
                <button class="btn" id="prev_day">1일 전</button>
                <button class="btn" id="current_day">오늘</button>
                <button class="btn" id="next_day">1일 후</button>
            </div>
            <div class="right">
                <button class="btn" id="day_unit_3h">3시간</button>
                <button class="btn" id="day_unit_6h">6시간</button>
                <button class="btn" id="day_unit_12h">12시간</button>
                <button class="btn" id="day_unit_1">1일</button>
                <button class="btn" id="day_unit_2">2일</button>
                <button class="btn" id="day_unit_3">3일</button>
                <button class="btn" id="day_unit_7">7일</button>
                <button class="btn" id="day_unit_14">14일</button>
                <button class="btn" id="day_unit_30">30일</button>
                <button class="btn" id="day_unit_90">3달</button>
                <button class="btn" id="day_unit_180">6달</button>
                <button class="btn" id="day_unit_365">1년</button>
            </div>
        </div>
        <div id="main_area">
          <div id="segments" class="content"></div>
          <div id="sessions"></div>
          <div id="time_display">
            <div class="curtime"></div>
            <div class="time-line"></div>
          </div>
        </div>
    `);

  const channelTitleElem = $("#channel_title")[0];
  channelTitleElem.addEventListener("click", () => {
    const route = "/channel/" + guildId;

    // copy to clipboard
    navigator.clipboard.writeText(window.location.origin + route);
  });

  const mainAreaElem = $("#main_area");
  const segmentsElem = $("#segments");

  const mainAreaBox = mainAreaElem[0];
  const boxLeft = boxLeftPad;
  const boxRight = mainAreaBox.getBoundingClientRect().width - boxLeftPad - 100;
  const boxTop = 0;
  const boxWidth = boxRight - boxLeft;

  const totalTime = targetInterval.end.getTime() - targetInterval.start.getTime();
  let isHourUnit = targetInterval.end.getTime() - targetInterval.start.getTime() <= 24 * 60 * 60 * 1000;
  let isDayUnit = targetInterval.end.getTime() - targetInterval.start.getTime() >= 5 * 24 * 60 * 60 * 1000;
  const simulateSegmentCounts = (l, r, w) => {
    return Math.floor(r / w) - Math.floor((l - 1) / w);
  };

  const hourCandidates = [1, 2, 3, 4, 6, 8, 12, 24, 2 * 24, 3 * 24, 4 * 24, 5 * 24, 6 * 24, 7 * 24, 30 * 24];
  let hourUnit = 1;
  for (let i = 0; i < hourCandidates.length; i++) {
    const hour = hourCandidates[i];
    const segmentCount = simulateSegmentCounts(
      targetInterval.start.getTime(),
      targetInterval.end.getTime(),
      hour * 60 * 60 * 1000
    );
    const minWidth = isHourUnit ? 36 : isDayUnit ? 36 : 64;
    const segmentWidth = boxWidth / segmentCount;
    if (segmentWidth >= minWidth) {
      hourUnit = hour;
      break;
    }
  }

  const format = isHourUnit ? "H시" : isDayUnit ? "M/DD" : "M.DD H시";
  const timeUnit = hourUnit * 60 * 60 * 1000;
  const startTime = targetInterval.start.getTime();
  const endTime = targetInterval.end.getTime();
  const segmentStartTime = Math.floor((startTime + 9 * TimeUnit.HOUR) / timeUnit + 1) * timeUnit - 9 * TimeUnit.HOUR;
  const segmentEndTime = Math.floor((endTime + 9 * TimeUnit.HOUR) / timeUnit) * timeUnit - 9 * TimeUnit.HOUR;

  // left bound segment
  segmentsElem.append(`
    <div class="segment u-day left-bound" style="left: ${boxLeft}px; top: ${boxTop}px;">
        <div class="segment-label">${dayjs(targetInterval.start.getTime()).format(format)}</div>
        <div class="segment-content">
            <div class="segment-line"></div>
        </div>
    </div>
  `);

  // segments
  for (let i = segmentStartTime; i <= segmentEndTime; i += timeUnit) {
    const r = (i - startTime) / totalTime;
    const x = boxLeft + r * boxWidth;
    const isDay = i % (24 * 60 * 60 * 1000) === 15 * 60 * 60 * 1000;
    const isMonth = dayjs(i).date() === 1 && dayjs(i).hour() === 0;
    segmentsElem.append(`
      <div class="segment ${isDay ? "u-day" : ""} ${isMonth ? "u-month" : ""}" style="left: ${x}px; top: ${boxTop}px;">
          <div class="segment-label">${dayjs(i).format(format)}</div>
          <div class="segment-content">
              <div class="segment-line"></div>
          </div>
      </div>
    `);
  }

  // right bound segment
  segmentsElem.append(`
    <div class="segment right-bound" style="left: ${boxRight}px; top: ${boxTop}px;">
        <div class="segment-label">${dayjs(targetInterval.end.getTime() + 1).format(format)}</div>
        <div class="segment-content">
            <div class="segment-line"></div>
        </div>
    </div>
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

  const dayUnit3hBtn = $("#day_unit_3h")[0];
  const dayUnit6hBtn = $("#day_unit_6h")[0];
  const dayUnit12hBtn = $("#day_unit_12h")[0];
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
    targetInterval.start = new Date(startOfDay(targetInterval.end).getTime() - (unit - 1) * 24 * 60 * 60 * 1000);
    displayMainContent();
  };

  dayUnit3hBtn.addEventListener("click", () => dayUnitHandler(1 / 8));
  dayUnit6hBtn.addEventListener("click", () => dayUnitHandler(1 / 4));
  dayUnit12hBtn.addEventListener("click", () => dayUnitHandler(1 / 2));
  dayUnit1Btn.addEventListener("click", () => dayUnitHandler(1));
  dayUnit2Btn.addEventListener("click", () => dayUnitHandler(2));
  dayUnit3Btn.addEventListener("click", () => dayUnitHandler(3));
  dayUnit7Btn.addEventListener("click", () => dayUnitHandler(7));
  dayUnit14Btn.addEventListener("click", () => dayUnitHandler(14));
  dayUnit30Btn.addEventListener("click", () => dayUnitHandler(30));
  dayUnit90Btn.addEventListener("click", () => dayUnitHandler(90));
  dayUnit180Btn.addEventListener("click", () => dayUnitHandler(180));
  dayUnit365Btn.addEventListener("click", () => dayUnitHandler(365));

  const lsb = $(`.segment.left-bound .segment-line`)[0];
  const rsb = $(`.segment.right-bound .segment-line`)[0];

  calculateSessions(userSessionMap);
  drawSessions();

  clearInterval(currentTimeDisplayInterval);
  currentTimeDisplayInterval = fastInterval(() => {
    const now = new Date();
    const elapsed = now.getTime() - targetInterval.start.getTime();
    const ratio = elapsed / totalTime;
    const x = boxLeft + ratio * boxWidth;

    const timeElem = $("#time_display")[0];
    timeElem.style.left = `${x + 1}px`;
    timeElem.style.height = `${lsb.getBoundingClientRect().bottom - lsb.getBoundingClientRect().top}px`;
    const curTimeElem = $("#time_display .curtime")[0];
    // curTimeElem.style.left = `${x}px`;
    // curTimeElem.style.top = `${lsb.getBoundingClientRect().top - 13}px`;
    curTimeElem.innerText = dayjs().format("H:mm:ss");
  }, 1000);
}

function calculateSessions(userSessionMap) {
  const now = Date.now();
  const sessions = [];
  for (let userId in userSessionMap) {
    const user = users[userId];
    const userSessions = userSessionMap[userId];
    for (let i = 0; i < userSessions.length; i++) {
      const session = { ...userSessions[i] };
      session.user = user;
      session.id = hashStrAsStr(userId + "-" + session.joinTime);
      session.joinTimeStr = dayjs(session.joinTime).format("YY.MM.DD HH:mm:ss");
      session.leaveTimeStr = session.leaveTime ? dayjs(session.leaveTime).format("YY.MM.DD HH:mm:ss") : null;

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
  for (let userId in filteredUserSessions) {
    const sessions = filteredUserSessions[userId];
    sessions.sort((s1, s2) => s1.joinTime - s2.joinTime);
    for (let i = 0; i < sessions.length; i++) {
      const session = sessions[i];
      session.online = (session.leaveTime || 0) === 0;
      session.leaveTime = session.leaveTime || now;
      if (session.leaveTime < session.joinTime) {
        session.leaveTime = session.joinTime;
      }
      session.next = i < sessions.length - 1 ? sessions[i + 1] : null;
      session.isLast = i === sessions.length - 1;
    }
  }

  sessionList = sessions.sort((s1, s2) => s1.joinTime - s2.joinTime);
  const seatUsers = {};
  userSeats = {};
  const maxYindex = 100;

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

    const lastLeaveTime1 = lastSession1.leaveTime;
    const lastLeaveTime2 = lastSession2.leaveTime;
    if (lastLeaveTime1 !== lastLeaveTime2) {
      return lastLeaveTime2 - lastLeaveTime1;
    }

    if (lastSession1.channelName !== lastSession2.channelName) {
      return (lastSession1.channelName ?? "Unknown").localeCompare(lastSession2.channelName ?? "Unknown");
    }

    const duration1 = s1.reduce((acc, cur) => acc + (cur.leaveTime - cur.joinTime), 0);
    const duration2 = s2.reduce((acc, cur) => acc + (cur.leaveTime - cur.joinTime), 0);
    if (duration1 !== duration2) {
      return duration2 - duration1;
    }

    const user1 = users[u1];
    const user2 = users[u2];
    return (user1.effectiveName ?? "Unknown").localeCompare(user2.effectiveName ?? "Unknown");
  });

  for (let userId of sortedUserIds) {
    if (userSeats[userId] != null) continue;
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
        userSeats[userId] = i;
        if (seatUsers[i] == null) seatUsers[i] = [];
        seatUsers[i].push(userId);
        break;
      }
    }
  }
}

function drawSessions() {
  const mainAreaElem = $("#main_area");

  const mainAreaBox = mainAreaElem[0];
  const boxLeft = boxLeftPad;
  const boxRight = mainAreaBox.getBoundingClientRect().width - boxLeftPad - 100;
  const boxTop = 0;
  const boxWidth = boxRight - boxLeft;

  const sessionsElem = $("#sessions");
  for (let i = 0; i < sessionList.length; i++) {
    const session = sessionList[i];
    const start = new Date(session.joinTime);
    const end = new Date(session.leaveTime);
    const duration = end - start;

    const total = targetInterval.end.getTime() - targetInterval.start.getTime();
    const startX = start.getTime() - targetInterval.start.getTime();
    const endX = end.getTime() - targetInterval.start.getTime();
    const startR = startX / total;
    const endR = endX / total;

    const x = boxLeft + startR * boxWidth;
    const yIndex = userSeats[session.user.id];
    const y = boxTop + 15 + (yIndex + 1) * 50;
    const w = (endR - startR) * boxWidth;
    const color = Color.generateRandomColorWithSeed(session.channelName ?? "Unknown")
      .adjustSaturation(0.8)
      .adjustBrightness(0.3)
      .toString();
    const flipTooltip = x + w / 2 > (boxLeft + boxRight) / 2;

    // hide content if width is too small
    const nextSession = session.next;
    const nextSessionDistance =
      nextSession != null ? ((nextSession.joinTime - session.leaveTime) * boxWidth) / total : Infinity;
    const hideContent = w < 100 && nextSessionDistance < 100;

    sessionsElem.append(`
            <div class="session ss-${session.id} ${
      session.online ? "current" : ""
    }" style="left: ${x}px; top: ${y}px; width: ${w}px; background-color: ${color};">
                <div class="session-content">
                    ${
                      hideContent
                        ? "&nbsp;"
                        : `<img class="icon" src="${session.user.avatarUrl ?? defaultAvatarUrl}" alt="User Icon">
                    <div class="name">${session.user.effectiveName ?? "Unknown"}</div>`
                    }
                    
                </div>     
            </div>
            <div class="session-tooltip st-${session.id} ${flipTooltip ? "flip" : ""}" style="left: ${
      x + w / 2
    }px; top: ${y}px;">
                <div class="user-info">
                    <img class="icon" src="${session.user.avatarUrl ?? defaultAvatarUrl}" alt="User Icon"/>
                    <div class="name">${session.user.effectiveName ?? "Unknown"}</div>
                </div>
                <div class="channel-info">
                    <div class="channel" style="color: ${color};">"${session.channelName}" 채널</div>
                </div>
                <div class="time-info">
                    <div class="time join">${dayjs(session.joinTime).format("YY.MM.DD HH:mm:ss")} 접속</div>
                    ${
                      !session.online
                        ? `
                        <div class="time leave">${dayjs(session.leaveTime).format("YY.MM.DD HH:mm:ss")} 퇴장</div>
                    `
                        : ""
                    }
                    <div class="duration">${durationStr(duration)} 동안 연결</div>
                </div>
            </div>         
        `);

    const sessionElem = $(`.session.ss-${session.id}`)[0];
    const tooltipElem = $(`.session-tooltip.st-${session.id}`)[0];
    sessionElem.addEventListener("mouseover", (e) => {
      tooltipElem.style.display = "flex";
    });
    sessionElem.addEventListener("mousemove", (e) => {
      const box = mainAreaElem[0].getBoundingClientRect();
      const x = e.clientX - box.left;
      const y = e.clientY - box.top;
      tooltipElem.style.left = `${x + 10}px`;
      tooltipElem.style.top = `${y + 10}px`;
    });
    sessionElem.addEventListener("mouseout", () => {
      tooltipElem.style.display = "none";
    });
  }

  clearInterval(updateSessionsInterval);
  updateSessionsInterval = fastInterval(() => {
    for (let session of sessionList) {
      const sessionElem = $(`.session.ss-${session.id}`)[0];

      if (!session.online) continue;
      session.leaveTime = Date.now();
      if (session.leaveTime < session.joinTime) session.leaveTime = session.joinTime;

      const start = new Date(session.joinTime);
      const end = new Date(session.leaveTime);

      const total = targetInterval.end.getTime() - targetInterval.start.getTime();
      const startX = start.getTime() - targetInterval.start.getTime();
      const endX = end.getTime() - targetInterval.start.getTime();
      const startR = startX / total;
      const endR = endX / total;

      const w = (endR - startR) * boxWidth;

      sessionElem.style.width = `${w}px`;

      const durationElem = $(`.session-tooltip.st-${session.id} .duration`)[0];
      durationElem.innerText = `${durationStr(end - start)} 동안 연결`;
    }
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
