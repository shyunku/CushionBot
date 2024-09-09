import CanvasEngine from "../../utils/canvas_engine/core/CanvasEngine.js";
import Layer from "../../utils/canvas_engine/core/Layer.js";
import Color from "../../utils/color.js";
import { durationStr, TimeUnit } from "../../utils/datetime.js";
import Guild from "../Guild.js";

const HighlightColor = "#44e7a3";
const EnterColor = "#44e7a3";
const LeaveColor = "#e74444";
const CurrentTimeColor = "#c04020";

class Sessions extends Layer {
  constructor(guild) {
    super();

    /** @type {Guild} */
    this.guild = guild;
    this.sessions = [];

    this.imageCaches = {};
  }

  getImage(url) {
    if (!this.imageCaches[url]) {
      const img = new Image();
      img.src = url;
      img.onload = () => {
        this.imageCaches[url] = img;
      };
    }
    return this.imageCaches[url];
  }

  preprocess(engine) {
    const { leftPad, rightPad, topPad } = engine.constants;
    const { intervalStart: intervalStartFlex, intervalEnd: intervalEndFlex } = engine.variables;
    const now = Date.now();

    const intervalStart = intervalStartFlex.get();
    const intervalEnd = intervalEndFlex.get();

    const zoomRate = engine.zoomRate;
    const filterStart = intervalStart - 12 * TimeUnit.HOUR * zoomRate;
    const filterEnd = intervalEnd + 12 * TimeUnit.HOUR * zoomRate;

    const flatSessions = Object.values(this.guild.users)
      .map((u) => u.sessions)
      .flat();
    const filtered = flatSessions.filter((s) => {
      if (s.online && filterStart <= now && now <= filterEnd) return true;
      const startIncluded = filterStart <= s.joinTime && s.joinTime <= filterEnd;
      const endIncluded = filterStart <= s.leaveTime && s.leaveTime <= filterEnd;
      const allIncluded = s.joinTime <= filterStart && filterEnd <= s.leaveTime;

      return startIncluded || endIncluded || allIncluded;
    });

    const sortedUsers = Object.values(this.guild.users).sort((a, b) => {
      const sessions1 = a.sessions;
      const sessions2 = b.sessions;
      if (sessions1.length === 0) return 1;
      if (sessions2.length === 0) return -1;

      const last1 = sessions1[sessions1.length - 1];
      const last2 = sessions2[sessions2.length - 1];

      if (last1.online !== last2.online) {
        return last2.online ? 1 : -1;
      }

      const leaveTime1 = last1.online ? now : last1.leaveTime;
      const leaveTime2 = last2.online ? now : last2.leaveTime;
      if (leaveTime1 !== leaveTime2) {
        return leaveTime2 - leaveTime1;
      }

      if (last1.channelName !== last2.channelName) {
        return last1.channelName.localeCompare(last2.channelName);
      }

      const lastDuration1 = now - last1.joinTime;
      const lastDuration2 = now - last2.joinTime;
      if (lastDuration1 !== lastDuration2) {
        return lastDuration2 - lastDuration1;
      }

      const totalDuration1 = sessions1.reduce((acc, s) => acc + (s.endTime - s.joinTime), 0);
      const totalDuration2 = sessions2.reduce((acc, s) => acc + (s.endTime - s.joinTime), 0);
      if (totalDuration1 !== totalDuration2) {
        return totalDuration2 - totalDuration1;
      }

      return a.effectiveName.localeCompare(b.effectiveName);
    });
    const userOrder = sortedUsers.reduce((acc, u, i) => {
      acc[u.id] = i;
      return acc;
    }, {});

    const filteredSessions = filtered.map((s) => {
      const startX = this.calculateX(s.joinTime);
      const endX = s.online ? this.calculateX(now) : this.calculateX(s.leaveTime);

      const color = Color.generateRandomColorWithSeed(s.channelName ?? "Unknown")
        .adjustSaturation(0.6)
        .adjustBrightness(0.35);

      return {
        start: s.joinTime,
        end: s.online ? now : s.leaveTime,
        online: s.online,
        startX,
        endX,
        width: endX - startX,
        userId: s.user.id,
        color: color.copy(),
        darkColor: color.adjustBrightness(0.2).copy(),
        brightColor: color.adjustBrightness(0.5).copy(),
        channelName: s.channelName,
      };
    });

    const sortedUserSessions = {};
    for (let session of filteredSessions) {
      const userId = session.userId;
      if (!sortedUserSessions[userId]) {
        sortedUserSessions[userId] = {
          order: userOrder[userId],
          user: this.guild.users[userId],
          sessions: [],
        };
      }
      sortedUserSessions[userId].sessions.push(session);
    }

    return Object.values(sortedUserSessions)
      .sort((a, b) => a.order - b.order)
      .map((u) => {
        u.sessions = u.sessions.sort((a, b) => a.start - b.start);
        u.sessions = u.sessions.map((s, i) => {
          s.next = u.sessions[i + 1] ?? null;
          return s;
        });
        return u;
      });
  }

  /**
   *
   * @param {any} c
   * @param {CanvasEngine} engine
   */
  draw(c, engine) {
    if (!engine.variables) return;
    const { leftPad, rightPad, topPad, sessionBoxTopPad, sessionBoxHeight, sessionBoxMargin } = engine.constants;
    const { intervalStart, intervalEnd } = engine.variables;

    engine.setFont(12);
    const sortedUserSessions = this.preprocess(engine);

    const totalSessionCount = sortedUserSessions.reduce((acc, u) => acc + u.sessions.length, 0);
    engine.tool.drawText2(
      `총 ${totalSessionCount}개의 세션, fps: ${parseInt(engine.fps)}`,
      5,
      topPad + 5,
      1,
      0,
      "#aaa"
    );

    // clip
    let hoveredSession = null;
    engine.tool.drawClipedContext(
      (c) => {
        c.beginPath();
        c.rect(0, topPad, engine.width, engine.height - topPad);
        c.closePath();
      },
      (c) => {
        for (let userSessions of sortedUserSessions) {
          const { order, sessions, user } = userSessions;
          const y = sessionBoxTopPad + topPad + order * (sessionBoxHeight + sessionBoxMargin) - engine.yOffset;

          let image = null;
          if (user.avatarUrl) {
            image = this.getImage(user.avatarUrl);
          }

          for (let session of sessions) {
            const { startX, endX, width, online, color, darkColor, brightColor, next } = session;
            const hovered =
              engine.mousePos.x >= startX &&
              engine.mousePos.x <= endX &&
              engine.mousePos.y >= y &&
              engine.mousePos.y <= y + sessionBoxHeight;
            const showUser = next == null || next.startX - endX > 40 + engine.tool.getTextWidth(user.effectiveName);

            engine.tool.setOpacity(online ? 1 : hovered ? 0.7 : 0.6);

            // draw session box
            if (online) {
              c.shadowBlur = 10;
              c.shadowColor = HighlightColor;
            }
            c.fillStyle = hovered ? brightColor : color;
            c.fillRect(startX, y, width, sessionBoxHeight);
            c.shadowBlur = 0;
            if (online) engine.tool.drawRect(startX, y, width, sessionBoxHeight, HighlightColor, 1);

            // draw session image & text
            if (showUser) {
              engine.tool.drawText2(
                user.effectiveName,
                endX + 35,
                y + sessionBoxHeight / 2,
                1,
                0,
                online ? HighlightColor : `rgba(200, 200, 200)`
              );
              if (image) engine.tool.drawCenteredRoundImage(image, endX + 20, y + sessionBoxHeight / 2, 10);
            }

            engine.tool.setOpacity(1);

            // draw tooltip
            if (hovered) {
              hoveredSession = { session, user };
            }
          }
        }
      }
    );

    // draw current time
    const now = Date.now();
    const nowX = this.calculateX(now, intervalStart, intervalEnd, leftPad, rightPad);
    engine.tool.drawLine(nowX, topPad - 5, nowX, engine.height, CurrentTimeColor);
    engine.tool.drawColorPaddedCenteredText(
      dayjs(now).format("hh:mm:ss"),
      nowX,
      topPad / 2 + 1,
      5,
      5,
      CurrentTimeColor,
      "white"
    );

    // draw tooltip
    if (hoveredSession) {
      const { session, user } = hoveredSession;

      const { x, y } = engine.mousePos;
      const xFlip = x > engine.width / 2;
      const yFlip = y > engine.height / 2;

      const pad = 10;
      const imageRadius = 12;
      const lineHeight = 24;
      const width = 240;

      const segmentCount = 4;
      const height = pad * 3 + imageRadius + lineHeight * (session.online ? segmentCount : segmentCount + 1);

      engine.tool.fillRect(x - (xFlip ? width : 0), y - (yFlip ? height : 0), width, height, "rgb(0, 0, 0, 0.85)");
      const userImage = this.getImage(user.avatarUrl);
      if (userImage) {
        engine.setFont(13);
        engine.tool.drawCenteredRoundImage(
          userImage,
          x + pad + imageRadius - (xFlip ? width : 0),
          y + pad + imageRadius - (yFlip ? height : 0),
          imageRadius
        );
        engine.tool.drawText2(
          user.effectiveName,
          x + pad + imageRadius * 2 + 8 - (xFlip ? width : 0),
          y + pad + imageRadius - (yFlip ? height : 0),
          1,
          0,
          "white"
        );
        engine.setFont(12);
        let index = 1;
        engine.tool.drawText2(
          `"${session.channelName}" 채널`,
          x + pad - (xFlip ? width : 0),
          y + pad + imageRadius + lineHeight * index++ - (yFlip ? height : 0),
          1,
          0,
          session.brightColor
        );
        engine.tool.drawText2(
          `${dayjs(session.start).format("YYYY년 MM월 DD일 H시 mm분 ss초 입장")}`,
          x + pad - (xFlip ? width : 0),
          y + pad + imageRadius + lineHeight * index++ - (yFlip ? height : 0),
          1,
          0,
          EnterColor
        );
        if (!session.online) {
          engine.tool.drawText2(
            `${dayjs(session.end).format("YYYY년 MM월 DD일 H시 mm분 ss초 퇴장")}`,
            x + pad - (xFlip ? width : 0),
            y + pad + imageRadius + lineHeight * index++ - (yFlip ? height : 0),
            1,
            0,
            LeaveColor
          );
        }
        engine.tool.drawText2(
          `${durationStr(session.end - session.start)} 동안 연결`,
          x + pad - (xFlip ? width : 0),
          y + pad + imageRadius + lineHeight * index++ - (yFlip ? height : 0),
          1,
          0,
          "white"
        );
        engine.tool.drawText2(
          `총 연결: ${durationStr(user.totalDuration)}`,
          x + pad - (xFlip ? width : 0),
          y + pad + imageRadius + lineHeight * index++ - (yFlip ? height : 0),
          1,
          0,
          "#777"
        );
      }
    }
  }

  calculateX(time) {
    const engine = this.ref;
    const { leftPad, rightPad } = engine.constants;
    const { intervalStart: startFlex, intervalEnd: endFlex } = engine.variables;
    const intervalStart = startFlex.get();
    const intervalEnd = endFlex.get();

    const width = engine.width - leftPad - rightPad;
    const intervalDuration = intervalEnd - intervalStart;
    const factor = (time - intervalStart) / intervalDuration;
    return leftPad + width * factor;
  }
}

export default Sessions;
