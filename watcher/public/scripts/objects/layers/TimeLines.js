import CanvasEngine from "../../utils/canvas_engine/core/CanvasEngine.js";
import Layer from "../../utils/canvas_engine/core/Layer.js";
import { getFactors } from "../../utils/common.js";
import { TimeUnit } from "../../utils/datetime.js";
import Guild from "../Guild.js";

const DisplayTimeUnits = [
  { unit: TimeUnit.SECOND, units: [1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30] },
  { unit: TimeUnit.MINUTE, units: [1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30] },
  { unit: TimeUnit.HOUR, units: [1, 2, 3, 4, 6, 12] },
  { unit: TimeUnit.DAY, units: [1] },
]
  .map((u) => {
    const { unit, units } = u;
    return units.map((f) => f * unit);
  })
  .flat();

export const IntervalUnits = {
  SECOND: 0,
  MINUTE: 1,
  HOUR: 2,
  DAY: 3,
  MONTH: 4,
  YEAR: 5,
};

const DisplayFormats = ["s초", "m분", "H시", "D일", "M월", "YY년"];

class TimeLines extends Layer {
  constructor(guild) {
    super();

    /** @type {Guild} */
    this.guild = guild;
  }

  /**
   *
   * @param {CanvasEngine} engine
   */
  preprocess(engine) {
    const timelines = [];

    const { leftPad, rightPad, topPad } = engine.constants;
    const { intervalStart: intervalStartFlex, intervalEnd: intervalEndFlex } = engine.variables;

    const intervalStart = intervalStartFlex.get();
    const intervalEnd = intervalEndFlex.get();
    // console.log(intervalStart, intervalEnd, intervalEndFlex.real());
    const intervalUnit = this.calculateIntervalUnit(intervalStart, intervalEnd + 1);
    const maxUnitFormat = DisplayFormats[intervalUnit];
    const testText = dayjs().format(maxUnitFormat);
    const maxWidth = engine.tool.getTextWidth(testText) + 10;

    const boxLeft = leftPad;
    const boxWidth = engine.width - leftPad - rightPad;

    const totalTime = intervalEnd - intervalStart;
    const simulateSegmentCounts = (l, r, w) => {
      return Math.floor(r / w) - Math.floor((l - 1) / w);
    };

    let unitTime = null;
    for (let i = 0; i < DisplayTimeUnits.length; i++) {
      const unit = DisplayTimeUnits[i];
      const segmentCount = simulateSegmentCounts(intervalStart, intervalEnd, unit);

      const segmentWidth = boxWidth / segmentCount;
      if (segmentWidth >= maxWidth) {
        unitTime = unit;
        break;
      }
    }

    const startTime = intervalStart;
    const endTime = intervalEnd;
    const timezoneDiff = 9 * TimeUnit.HOUR;

    const times = [];
    if (unitTime != null) {
      const timelineStart = Math.floor((startTime + timezoneDiff) / unitTime) * unitTime - timezoneDiff;
      const timelineEnd = Math.floor((endTime + timezoneDiff + 1) / unitTime) * unitTime - timezoneDiff;

      for (let time = timelineStart; time <= timelineEnd; time += unitTime) {
        times.push(time);
      }
    } else {
      unitTime = 1 * TimeUnit.DAY;
      const timelineStart = Math.floor((startTime + timezoneDiff) / unitTime) * unitTime - timezoneDiff;
      const timelineEnd = Math.floor((endTime + timezoneDiff + 1) / unitTime) * unitTime - timezoneDiff;

      const weekCount = this.getWeekCount(timelineStart, timelineEnd);
      const weekWidth = boxWidth / weekCount;
      if (weekWidth >= maxWidth) {
        for (let time = timelineStart; time <= timelineEnd; time += TimeUnit.DAY) {
          if (dayjs(time).date() % 7 === 1) times.push(time);
        }
      } else {
        const monthCount = this.getMonthCount(timelineStart, timelineEnd);
        const monthWidth = boxWidth / monthCount;
        if (monthWidth >= maxWidth) {
          for (let time = timelineStart; time <= timelineEnd; time += TimeUnit.DAY) {
            if (dayjs(time).date() === 1) times.push(time);
          }
        } else {
          for (let time = timelineStart; time <= timelineEnd; time += TimeUnit.DAY) {
            if (dayjs(time).month() === 0 && dayjs(time).date() === 1) times.push(time);
          }
        }
      }
    }

    for (let time of times) {
      const r = (time - startTime) / totalTime;
      const x = boxLeft + r * boxWidth;

      const timeUnit = this.calculateTimeUnit(time);
      const isMajor = timeUnit >= intervalUnit;

      const isDay = time % (24 * 60 * 60 * 1000) === 15 * 60 * 60 * 1000;
      const isMonth = dayjs(time).date() === 1 && dayjs(time).hour() === 0;

      const color = isMajor ? "rgba(81, 226, 180, 1)" : "rgba(255, 255, 255, 0.3)";
      const timeFormat = DisplayFormats[timeUnit];

      let format = "H시";
      if (isDay) format = "M/DD H시";
      else if (isMonth) format = "YY/MM/DD";

      timelines.push({
        x,
        time,
        format: timeFormat,
        color,
      });
    }

    return timelines;
  }

  /**
   *
   * @param {any} c
   * @param {CanvasEngine} engine
   */
  draw(c, engine) {
    if (!engine.variables) return;
    const { leftPad, rightPad, topPad } = engine.constants;
    const { intervalStart, intervalEnd } = engine.variables;

    engine.setFont(12);
    const timelines = this.preprocess(engine);

    for (let i = 0; i < timelines.length; i++) {
      const timeline = timelines[i];
      const { x, time, format, color } = timeline;

      let text = dayjs(time).format(format);

      c.strokeStyle = "rgba(255, 255, 255, 0.1)";
      engine.tool.drawLine(x, topPad, x, engine.height);
      engine.tool.drawText2(text, x, topPad / 2, 0, 0, color);
    }
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

  calculateTimeUnit(time) {
    const date = dayjs(time);
    if (date.second() !== 0) return IntervalUnits.SECOND;
    if (date.minute() !== 0) return IntervalUnits.MINUTE;
    if (date.hour() !== 0) return IntervalUnits.HOUR;
    if (date.date() !== 1) return IntervalUnits.DAY;
    if (date.month() !== 0) return IntervalUnits.MONTH;
    return IntervalUnits.YEAR;
  }

  getWeekCount(start, end) {
    let count = 0;
    for (let time = start; time <= end; time += TimeUnit.DAY) {
      if (dayjs(time).day() === 0) count++;
    }
    return count;
  }

  getMonthCount(start, end) {
    return Math.floor((end - start) / (30 * TimeUnit.DAY));
  }

  getYearCount(start, end) {
    return dayjs(end).year() - dayjs(start).year();
  }
}

export default TimeLines;
