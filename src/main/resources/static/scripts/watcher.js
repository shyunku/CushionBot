var data, guilds, users;
var selectedGuildId = null;
var currentTimeDisplayInterval = null;
var targetDay = new Date();
const defaultAvatarUrl = "https://cdn.discordapp.com/embed/avatars/0.png";

$(document).ready(async () => {
    data = window.data ?? {};
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
        if (selectedGuildId != null && data[selectedGuildId] == null) {
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
    const todayElem = $('#today');
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
    const mainContent = $('#main_content');
    mainContent.empty();
    if (guildId == null) return;
    mainContent.append(`
        <div class="title">${guildData?.name ?? "Unknown"} (${dateStr(targetDay.getTime())})</div>
        <div class="options">
            <div class="left">
                <button class="btn" id="prev_day">1일 전</button>
                <button class="btn" id="current_day">오늘</button>
                <button class="btn" id="next_day">1일 후</button>
            </div>
            <div class="right"></div>
        </div>
        <div class="content">
            ${Array(25).fill(0).map((_, i) => {
        return `
                    <div class="hour">
                        <div class="hour-label">${`${i}`.padStart(2, "0")}:00</div>
                        <div class="hour-content">
                            <div class="hour-line h-${i}"></div>
                        </div>
                    </div>
                `;
    }).join("")}
        </div>
        <div id="sessions"></div>
    `);

    const prevDayBtn = $('#prev_day')[0];
    const todayBtn = $('#current_day')[0];
    const nextDayBtn = $('#next_day')[0];

    prevDayBtn.addEventListener("click", () => {
        targetDay.setDate(targetDay.getDate() - 1);
        displayMainContent();
    });
    todayBtn.addEventListener("click", () => {
        targetDay = new Date();
        displayMainContent();
    });
    nextDayBtn.addEventListener("click", () => {
        targetDay.setDate(targetDay.getDate() + 1);
        displayMainContent();
    });

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
            const session = {...userSessions[i]};
            session.user = user;
            session.id = hashStrAsStr(userId + "-" + session.joinTime);
            session.joinTimeStr = timeStr(session.joinTime);
            session.leaveTimeStr = session.leaveTime ? timeStr(session.leaveTime) : null;

            const start = new Date(session.joinTime);
            const end = session.leaveTime !== 0 ? new Date(session.leaveTime) : new Date();

            const startEqual = start.getDate() === targetDay.getDate()
                && start.getMonth() === targetDay.getMonth()
                && start.getFullYear() === targetDay.getFullYear();
            const endEqual = end != null && end.getDate() === targetDay.getDate()
                && end.getMonth() === targetDay.getMonth()
                && end.getFullYear() === targetDay.getFullYear();
            if (startEqual || endEqual) {
                if (!startEqual) {
                    session.joinTime = new Date(targetDay.getFullYear(), targetDay.getMonth(), targetDay.getDate(), 0, 0, 0, 0).getTime();
                }
                if (!endEqual && end != null) {
                    session.leaveTime = new Date(targetDay.getFullYear(), targetDay.getMonth(), targetDay.getDate(), 23, 59, 59, 999).getTime();
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

    const sorted = sessions.sort((s1, s2) => (s1.joinTime - s2.joinTime));
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
    }

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
            return (lastSession1.channelName ?? "Unknown").localeCompare((lastSession2.channelName ?? "Unknown"));
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
        const emptySeatExists = Array(maxYindex).fill(0).map((_, i) => i).some((i) => seatUsers[i] == null || seatUsers[i].length === 0);
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

        const total = 24 * 60 * 60 * 1000;
        const startX = start.getHours() * 60 * 60 * 1000 + start.getMinutes() * 60 * 1000 + start.getSeconds() * 1000 + start.getMilliseconds();
        const endX = end.getHours() * 60 * 60 * 1000 + end.getMinutes() * 60 * 1000 + end.getSeconds() * 1000 + end.getMilliseconds();
        const startR = startX / total;
        const endR = endX / total;

        const x = lx + startR * (rx - lx);
        const yIndex = userSeat[session.user.id];
        const y = ly + (yIndex + 1) * (ry - ly) / (maxYindex + 1);
        const w = (endR - startR) * (rx - lx);
        const color = getColorByStringWithBrightness(session.channelName, 0.3, 0.5);

        sessionsElem.append(`
            <div class="session ss-${session.id} ${isCurrent ? "current" : ""}" style="left: ${x}px; top: ${y}px; width: ${w}px; background-color: ${color};">
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
                    ${session.leaveTime ? `
                        <div class="time leave">${timeStr(session.leaveTime)} 퇴장</div>
                    ` : ""}
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
        const elapsed = now.getHours() * 60 * 60 * 1000 + now.getMinutes() * 60 * 1000 + now.getSeconds() * 1000 + now.getMilliseconds();
        const ratio = elapsed / total;
        const x = lx + ratio * (rx - lx);

        const timeElem = $('#time_display')[0];
        timeElem.style.top = `${h0.getBoundingClientRect().top - 5}px`;
        timeElem.style.left = `${x}px`;
        timeElem.style.height = `${h0.getBoundingClientRect().bottom - h0.getBoundingClientRect().top}px`;
        const curTimeElem = $('#time_display .curtime')[0];
        curTimeElem.style.left = `${x}px`;
        curTimeElem.style.top = `${h0.getBoundingClientRect().top - 13}px`;
        curTimeElem.innerText = timeStr();
    }, 1000);
}

async function httpGet(url) {
    return new Promise((resolve, reject) => {
        try {
            $.ajax({
                url: url,
                type: 'GET',
                success: (data) => {
                    resolve(data);
                },
                error: (err) => {
                    reject(err);
                }
            })
        } catch (err) {
            reject(err);
        }
    });
}

function makeRandomColor() {
    const r = Math.floor(Math.random() * 255);
    const g = Math.floor(Math.random() * 255);
    const b = Math.floor(Math.random() * 255);
    return `rgb(${r},${g},${b})`;
}

function getColorByStringWithBrightness(str, lb = 0, rb = 1) {
    const rhash = hashStr("$r" + str.repeat(3));
    const ghash = hashStr("$g" + str.repeat(3));
    const bhash = hashStr("$b" + str.repeat(3));
    let r = Math.floor(rhash * 255);
    let g = Math.floor(ghash * 255);
    let b = Math.floor(bhash * 255);
    const brightness = (r * 299 + g * 587 + b * 114) / (1000 * 255);
    const ob = lb + (rb - lb) * brightness;
    const f = ob / brightness;

    r = Math.min(255, Math.floor(r * f));
    g = Math.min(255, Math.floor(g * f));
    b = Math.min(255, Math.floor(b * f));

    return `rgb(${r},${g},${b})`;
}

function fnv1a(str) {
    let hash = 2166136261;
    for (let i = 0; i < str.length; i++) {
        hash ^= str.charCodeAt(i);
        hash += (hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24);
    }
    return hash >>> 0;
}

function hashStr(str) {
    const seed = fnv1a(str);
    return Math.abs(Math.sin(seed));
}

function hashStrAsStr(str) {
    const seed = fnv1a(str);
    return Math.floor(Math.abs(Math.sin(seed) * 1000000)).toString(16);
}

function fastInterval(fn, interval) {
    fn();
    return setInterval(fn, interval);
}

function dateStr(date = Date.now()) {
    const d = new Date(date);
    const year = d.getFullYear() % 100;
    const month = d.getMonth() + 1;
    const day = d.getDate();
    const md = `${month}`.padStart(2, "0");
    const dd = `${day}`.padStart(2, "0");
    return `${year}.${md}.${dd}`;
}

function timeStr(time = Date.now()) {
    const date = new Date(time);
    const hours = date.getHours();
    const minutes = date.getMinutes();
    const seconds = date.getSeconds();

    const hd = `${hours}`;
    const md = `${minutes}`.padStart(2, "0");
    const sd = `${seconds}`.padStart(2, "0");
    return `${hd}:${md}:${sd}`;
}

function durationStr(duration) {
    const hours = Math.floor(duration / (60 * 60 * 1000));
    const minutes = Math.floor((duration % (60 * 60 * 1000)) / (60 * 1000));
    const seconds = Math.floor((duration % (60 * 1000)) / 1000);

    const hd = `${hours}`;
    const md = `${minutes}`.padStart(2, "0");
    const sd = `${seconds}`.padStart(2, "0");
    return `${hd}:${md}:${sd}`;
}