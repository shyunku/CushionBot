import CanvasEngine from "../../utils/canvas_engine/core/CanvasEngine.js";
import Layer from "../../utils/canvas_engine/core/Layer.js";

class CanvasLog extends Layer {
  constructor() {
    super();
    this.logs = [];
    this.curFps = 0;
  }

  addLog(str) {
    this.logs.push({ isPair: false, head: "", body: str });
  }

  addLogPair(head, body) {
    this.logs.push({ isPair: true, head: head, body: body });
  }

  addBlank(num = 1) {
    for (let i = 0; i < num; i++) {
      this.addLog("");
    }
  }

  truncate() {
    this.logs = [];
  }

  /**
   * @param {CanvasEngine} engine
   */
  preprocess(engine) {
    this.truncate();

    engine.setFont(11);

    this.addBlank();

    this.addLogPair("Current Time", dayjs().format("YYYY-MM-DD HH:mm:ss.SSS"));
    this.addLogPair("Current Timestamp (ms)", Date.now());
    this.addBlank();

    this.addLogPair("Canvas ID", engine.id);
    this.addLogPair("Dest FPS", engine.destFps ?? "null");
    this.addLogPair("Real FPS", parseInt(engine.fps));
    this.addLogPair("RenderPeriod (ms)", engine.renderPeriod.toFixed(0));
    this.addLogPair("Zoom Level", "x" + engine.camera.getZoomLevel().get().toFixed(3));
    this.addLogPair("Pixel Rate", engine.pixelRate);
    let lookAt = engine.camera.getLookAt(true);
    let cameraRD = engine.getCameraRDCoordinate();
    this.addLogPair("Camera Look At", `(${lookAt.x}, ${lookAt.y})`);
    this.addLogPair("Camera Origin", `(${parseInt(engine.cameraTranslateX)}, ${parseInt(engine.cameraTranslateY)})`);
    this.addLogPair("Camera RD Vertex", `(${parseInt(cameraRD.x)}, ${parseInt(cameraRD.y)})`);
    this.addBlank();

    this.addLogPair("MousePos", `(${engine.mousePos.x}, ${engine.mousePos.y})`);
    this.addLogPair("CanvasMousePos", `(${parseInt(engine.canvasMousePos.x)}, ${parseInt(engine.canvasMousePos.y)})`);
    this.addLogPair("TestPos", `(${parseInt(engine.testPos.x)}, ${parseInt(engine.testPos.y)})`);
    this.addLogPair("MouseHovered", engine.mouseHovered);
    this.addLogPair("ZoomRate", engine.zoomRate ?? 1);
  }

  draw(c, ref) {
    if (!ref.enableLog) return;
    this.preprocess(ref);
    let offsetX = 10;

    for (let logOffset = 0; logOffset < this.logs.length; logOffset++) {
      let log = this.logs[logOffset];
      let offsetY = ref.height - this.logs.length * 15 * ref.pixelRate + ref.pixelRate * 15 * logOffset;

      c.fillStyle = "#888";

      if (typeof log.body === "object") {
        log.body = JSON.stringify(log.body);
      }

      if (log.isPair) {
        let headWidth = c.measureText(log.head).width;
        c.fillText(log.head, offsetX, offsetY);
        c.fillStyle = "#6AF";
        c.fillText(log.body, offsetX + headWidth + 10, offsetY);
      } else {
        c.fillText(log.body, offsetX, offsetY);
      }
    }
  }
}

export default CanvasLog;
