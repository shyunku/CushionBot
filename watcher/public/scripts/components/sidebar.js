import UI from "../utils/ui.js";

class Sidebar extends UI {
  constructor(props) {
    super(props);
  }

  afterUpdate() {
    console.log(this.states.data);
  }

  define() {
    console.log(this.states.data);
    return `
      <div id="sidebar">
        <div class="title">서버 연결 현황</div>
        <div id="today">${Object.values(this.states.data ?? {}).map((guild) => {
          return `
            <div class="today-item server-${guild.id}">
              <div class="header">
                <img src="${guild.iconUrl}" alt="Server Icon" class="icon" />
                <div class="name">${guild.name}</div>
              </div>
              <div class="content">
                <canvas id="today_chart_${guild.id}" class="today-chart" />
              </div>
            </div>
          `;
        })}</div>
      </div>
    `;
  }
}

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
                  <canvas id="today_chart_${guildId}" class="today-chart"/>
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

    const intervals = new Intervals();
    const todaySessions = Object.values(data[guildId])
      .map((userSessionMap) => Object.values(userSessionMap))
      .flat()
      .map((session) => ({ ...session }))
      .map((session) => {
        session.leaveTime = session.leaveTime || Date.now();
        return session;
      })
      .filter((session) => {
        const joinTime = new Date(session.joinTime);
        const leaveTime = new Date(session.leaveTime);
        return (
          joinTime.toLocaleDateString() === new Date().toLocaleDateString() ||
          leaveTime.toLocaleDateString() === new Date().toLocaleDateString()
        );
      })
      .map((session) => {
        let joinTime = new Date(session.joinTime);
        let leaveTime = new Date(session.leaveTime);
        if (joinTime.toLocaleDateString() !== new Date().toLocaleDateString()) {
          joinTime = startOfDay();
        }
        if (leaveTime.toLocaleDateString() !== new Date().toLocaleDateString()) {
          leaveTime = endOfDay();
        }
        return { ...session, joinTime: joinTime.getTime(), leaveTime: leaveTime.getTime() };
      })
      .sort((a, b) => a.joinTime - b.joinTime);

    for (let session of todaySessions) {
      const start = session.joinTime;
      const end = session.leaveTime;
      intervals.add(start, end);
    }

    // console.log(todaySessions);
    // console.log(intervals.toArray());
    const intervalSlice = intervals.toSlice();
    const dataPoints = [];
    for (let i = 0; i < intervalSlice.length; i++) {
      const time = intervalSlice[i];
      dataPoints.push({ x: time - 1, y: intervals.getCount(time - 1) });
      dataPoints.push({ x: time, y: intervals.getCount(time) });
      dataPoints.push({ x: time + 1, y: intervals.getCount(time + 1) });
    }

    if (dataPoints.length === 0) {
      const start = startOfDay().getTime();
      dataPoints.push({ x: start, y: 0 });
      dataPoints.push({ x: Date.now(), y: 0 });
    }

    const ctx = $(`#today_chart_${guildId}`).get(0).getContext("2d");

    // 선 그래디언트 생성
    const gradientLine = ctx.createLinearGradient(0, 0, 0, 120);
    gradientLine.addColorStop(0, "#39af8a");
    gradientLine.addColorStop(1, "#39af8a40");

    // 채우기 그래디언트 생성
    const gradientFill = ctx.createLinearGradient(0, 0, 0, 120);
    gradientFill.addColorStop(0, "#39af8a80");
    gradientFill.addColorStop(1, "#39af8a00");

    const chart = new Chart(ctx, {
      type: "line",
      data: {
        labels: dataPoints.map((e) => new Date(e.x)),
        datasets: [
          {
            data: dataPoints.map((e) => e.y),
            tension: 0.4,
            borderWidth: 2,
            cubicInterpolationMode: "monotone",
            fill: "start",
            borderColor: gradientLine, // 선 그래디언트 적용
            backgroundColor: gradientFill, // 채우기 그래디언트 적용
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: false,
          },
        },
        elements: {
          point: {
            radius: 0,
          },
        },
        scales: {
          x: {
            type: "time",
            grid: {
              display: false,
            },
            ticks: {
              display: false,
            },
            suggestedMax: new Date(),
            suggestedMin: startOfDay(),
          },
          y: {
            grid: {
              display: false,
            },
            ticks: {
              display: true,
              stepSize: 1,
            },
            suggestedMax: 1,
            min: 0,
          },
        },
      },
    });
  }
}

export default Sidebar;
