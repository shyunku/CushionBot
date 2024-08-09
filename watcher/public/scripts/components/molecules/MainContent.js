import { endOfDay, startOfDay } from "../../utils/datetime.js";
import UI from "../../utils/ui.js";

export const Modes = {
  INTERVAL: "일반",
  DAY: "일간",
  WEEK: "주간",
};

class MainContent extends UI {
  constructor(props) {
    super({
      ...props,
      initialState: {
        mode: Modes.INTERVAL,
        intervalStart: startOfDay().getTime(),
        intervalEnd: endOfDay().getTime(),
      },
    });
  }

  afterRender() {}

  onTitleClick() {
    const route = "/channel/" + this.states.guild?.id;

    // copy to clipboard
    if (!!navigator.clipboard) {
      navigator.clipboard.writeText(window.location.origin + route);
    } else {
      const el = document.createElement("textarea");
      el.value = window.location.origin + route;
      document.body.appendChild(el);
      el.select();
      document.execCommand("copy");
      document.body.removeChild(el);
    }

    alert("복사되었습니다!");
  }

  onModeSelect(mode) {
    this.setState("mode", mode);
  }

  define() {
    const { intervalStart, intervalEnd } = this.states;
    let intervalText = `${dayjs(intervalStart).format("YY.MM.DD")} ~ ${dayjs(intervalEnd).format("YY.MM.DD")}`;
    if (dayjs(intervalStart).format("YY.MM.DD") === dayjs(intervalEnd).format("YY.MM.DD")) {
      intervalText = `${dayjs(intervalStart).format("YY.MM.DD")}`;
    }

    return `
      <div id="main_content">
        <div id="channel_title" class="title" onclick={this.onTitleClick}>{$guild?.name ?? "Unknown"} (${intervalText})</div>
        <div class="modes">
          ${UI.$$(
            Object.values(Modes),
            (mode) =>
              `<button class="btn {$mode === '${mode}' ? 'selected' : ''}" id="mode_interval" onclick={e => this.onModeSelect("${mode}")}>${mode}</button>`
          )}
        </div>
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
      </div>
    `;
  }
}

export default MainContent;
