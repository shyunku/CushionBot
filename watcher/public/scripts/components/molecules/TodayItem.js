import { startOfDay } from "../../utils/datetime.js";
import UI from "../../utils/ui.js";

class TodayItem extends UI {
  constructor(props) {
    super(props);
  }

  afterMount() {
    const ctx = $(`#today_chart_${this.states.guild?.id}`).get(0).getContext("2d");
    // 선 그래디언트 생성
    const gradientLine = ctx.createLinearGradient(0, 0, 0, 120);
    gradientLine.addColorStop(0, "#39af8a");
    gradientLine.addColorStop(1, "#39af8a40");

    // 채우기 그래디언트 생성
    const gradientFill = ctx.createLinearGradient(0, 0, 0, 120);
    gradientFill.addColorStop(0, "#39af8a80");
    gradientFill.addColorStop(1, "#39af8a00");

    const intervals = this.states.guild?.getIntervals();
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

  onClick(e) {
    this.states.onGuildIdSelect?.(this.states.guild?.id);
  }

  define() {
    return `
      <div class="today-item server-{$guild?.id} {$selected ? 'selected' : ''}" onclick={this.onClick}>
        <div class="header">
          <img src={$guild?.iconUrl} alt="Server Icon" class="icon" />
          <div class="name">{$guild?.name}</div>
        </div>
        <div class="content">
          <canvas id="today_chart_{$guild?.id}" class="today-chart"></canvas>
        </div>
      </div>
    `;
  }
}

export default TodayItem;
