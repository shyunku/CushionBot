import CanvasLog from "../../objects/layers/CanvasLog.js";
import Sessions from "../../objects/layers/Sessions.js";
import TimeLines, { IntervalUnits } from "../../objects/layers/TimeLines.js";
import CanvasEngine from "../../utils/canvas_engine/core/CanvasEngine.js";
import FlexibleValue from "../../utils/canvas_engine/core/FlexibleValue.js";
import { endOfDay, startOfDay, TimeUnit } from "../../utils/datetime.js";
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
        intervalStart: new FlexibleValue(Date.now() - 18 * TimeUnit.HOUR, { factor: 0.999, useInt: true }),
        intervalEnd: new FlexibleValue(Date.now() + 6 * TimeUnit.HOUR - 1, { factor: 0.999, useInt: true }),
        guild: null,
      },
    });

    this.engine = null;
    this.timelineLayer = null;
    this.sessionsLayer = null;
  }

  afterRender() {
    const guild = this.states.guild;
    if (guild == null) return;
    const initialized = this.engine != null;
    const userCount = Object.keys(guild.users).length;

    /** @type {CanvasEngine} */
    let engine = this.engine;
    if (!initialized) {
      engine = new CanvasEngine("main_chart");
      engine.yOffset = 0;
      engine.zoomRate = 1;
      engine.constants = {
        leftPad: 30,
        rightPad: 30,
        topPad: 30,
        sessionBoxTopPad: 15,
        sessionBoxHeight: 28,
        sessionBoxMargin: 14,
      };

      const boxHeightFactor = engine.constants.sessionBoxHeight + engine.constants.sessionBoxMargin;
      const maxYOffset = Math.max(
        engine.constants.sessionBoxTopPad + boxHeightFactor * (userCount + 1) - engine.parentNode.clientHeight,
        0
      );

      engine.addMouseDownEventHandler((e) => {
        engine.mouseAnchorX = engine.mousePos.x;
        engine.mouseAnchorY = engine.mousePos.y;
      });
      engine.addMouseMoveEventHandler((e) => {
        if (engine.mouseAnchorX == null || engine.mouseAnchorY == null) return;
        const { intervalStart: intervalStartFlex, intervalEnd: intervalEndFlex } = engine.variables;
        const realStart = intervalStartFlex.real();
        const realEnd = intervalEndFlex.real();
        const realDiff = realEnd - realStart;

        const diff = engine.mousePos.x - engine.mouseAnchorX;
        const diffTime = (diff / engine.width) * realDiff;
        intervalStartFlex.set(realStart - diffTime, true);
        intervalEndFlex.set(realEnd - diffTime, true);

        engine.yOffset -= engine.mousePos.y - engine.mouseAnchorY;
        if (engine.yOffset > maxYOffset) engine.yOffset = maxYOffset;
        if (engine.yOffset < 0) engine.yOffset = 0;

        engine.mouseAnchorX = engine.mousePos.x;
        engine.mouseAnchorY = engine.mousePos.y;
      });
      engine.addMouseUpEventHandler((e) => {
        const { intervalStart: intervalStartFlex, intervalEnd: intervalEndFlex } = engine.variables;
        this.setState("intervalStart", intervalStartFlex);
        this.setState("intervalEnd", intervalEndFlex);
        engine.mouseAnchorX = null;
        engine.mouseAnchorY = null;
      });
      engine.addMouseWheelEventHandler((e) => {
        const { intervalStart: intervalStartFlex, intervalEnd: intervalEndFlex } = engine.variables;
        const { leftPad, rightPad } = engine.constants;
        const realStart = intervalStartFlex.real();
        const realEnd = intervalEndFlex.real();
        const realDiff = realEnd - realStart;

        const isZoom = e.deltaY < 0;
        const zoomFactor = 0.2;
        const zoomRate = isZoom ? 1 - zoomFactor : 1 + zoomFactor;
        engine.zoomRate *= zoomRate;

        const width = engine.width - leftPad - rightPad;
        const mouseOffset = engine.mousePos.x - leftPad;
        const factor = mouseOffset / width;

        const mouseTime = realStart + factor * realDiff;
        const newIntervalStart = mouseTime - (mouseTime - realStart) * zoomRate;
        const newIntervalEnd = mouseTime + (realEnd - mouseTime) * zoomRate;

        let newDiff = newIntervalEnd - newIntervalStart;
        if (newDiff > 5 * 365 * TimeUnit.DAY) {
          return;
        } else if (newDiff < 10 * TimeUnit.SECOND) {
          return;
        }

        intervalStartFlex.set(newIntervalStart);
        intervalEndFlex.set(newIntervalEnd);

        this.setState("intervalStart", intervalStartFlex);
        this.setState("intervalEnd", intervalEndFlex);
      });

      this.timelineLayer = new TimeLines(guild);
      this.sessionsLayer = new Sessions(guild);

      console.log(this.states.guild, engine);
      // engine.registerStaticLayer(new CanvasLog());
      engine.registerLayer(this.timelineLayer);
      engine.registerLayer(this.sessionsLayer);
      engine.render();

      this.engine = engine;
    }

    engine.variables = {
      intervalStart: this.states.intervalStart,
      intervalEnd: this.states.intervalEnd,
    };

    this.timelineLayer.guild = guild;
    this.sessionsLayer.guild = guild;
  }

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

  moveToCurrent() {
    const { intervalStart, intervalEnd } = this.states;
    const newStart = Date.now() - 18 * TimeUnit.HOUR;
    const newEnd = Date.now() + 6 * TimeUnit.HOUR - 1;
    intervalStart.set(newStart);
    intervalEnd.set(newEnd);
    this.setState("intervalStart", intervalStart);
    this.setState("intervalEnd", intervalEnd);
  }

  moveToCurrentDay() {
    const { intervalStart, intervalEnd } = this.states;
    const newStart = startOfDay().getTime();
    const newEnd = endOfDay().getTime();
    intervalStart.set(newStart);
    intervalEnd.set(newEnd);
    this.setState("intervalStart", intervalStart);
    this.setState("intervalEnd", intervalEnd);
  }

  define() {
    const { intervalStart: flexStart, intervalEnd: flexEnd } = this.states;
    const intervalStart = flexStart.real();
    const intervalEnd = flexEnd.real();
    const timeUnit = this.calculateIntervalUnit(intervalStart, intervalEnd);
    const units = ["MM.DD HH:mm:ss", "MM.DD HH:mm:ss", "MM.DD HH:mm", "MM.DD HH시", "YY.MM.DD", "YY.MM"];
    const intervalText = `${dayjs(intervalStart).format(units[timeUnit])} ~ ${dayjs(intervalEnd).format(
      units[timeUnit]
    )}`;

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
            <button class="btn" onclick={this.moveToCurrentDay}>오늘</button>
            <button class="btn" onclick={this.moveToCurrent}>현재</button>
          </div>
        </div>
        <div id="main_area">
          <canvas id="main_chart" class="main-chart"></canvas>
        </div>
      </div>
    `;
  }

  calculateIntervalUnit(intervalStart, intervalEnd) {
    const start = dayjs(intervalStart);
    const end = dayjs(intervalEnd);

    if (start.year() !== end.year()) return IntervalUnits.YEAR;
    if (start.month() !== end.month()) return IntervalUnits.MONTH;
    if (start.date() !== end.date()) return IntervalUnits.DAY;
    if (start.hour() !== end.hour()) return IntervalUnits.HOUR;
    if (start.minute() !== end.minute()) return IntervalUnits.MINUTE;
    return IntervalUnits.SECOND;
  }
}

export default MainContent;
