import Camera from "./Camera.js";
import Callable from "./Callable.js";
import Point2D from "../physics/Point2D.js";
import Bounds from "../physics/Bounds.js";
import { currentMicroProcessTime, fastInterval, getPixelRate } from "./Utils.js";

class CanvasEngine extends Callable {
  constructor(canvasId, fps) {
    super();

    this.id = canvasId;

    // Identifiers
    this.node = document.getElementById(canvasId);
    this.parentNode = this.node.parentNode;

    // Settings
    this.enableLog = true;

    // Positions
    this.canvasMousePos = { x: -9999, y: -9999 };
    this.mousePos = { x: -9999, y: -9999 };
    this.testPos = { x: -9999, y: -9999 };

    // Pixel Adjust (for resolution)
    this.pixelRate = getPixelRate();
    this.node.width = this.parentNode.clientWidth;
    this.node.height = this.parentNode.clientHeight;

    this.context = this.getHighDpiCanvasContext(this.node);

    this.width = this.node.width;
    this.height = this.node.height;

    // Camera
    this.camera = new Camera(this.width / 2, this.height / 2, 1);
    this.cameraTranslateX = 0;
    this.cameraTranslateY = 0;
    this.zoomable = false;
    this.zoomWithPos = false;

    this.destFps = fps ?? null;
    this.lastRenderTime = currentMicroProcessTime();
    this.renderPeriod = 1000;

    // Layers
    this.drawableLayers = [];
    this.staticDrawableLayers = [];

    // Controllers
    this.lookAtWhat = null;
    this.mouseHovered = false;
    this.dragging = false;

    this.context.fillStyle = "black";

    // Event Handlers
    this.mouseEnterHandlers = [];
    this.mouseLeaveHandlers = [];
    this.mouseDownHandlers = [];
    this.mouseUpHandlers = [];
    this.mouseMoveHandlers = [];
    this.mouseWheelHandlers = [];
    this.mouseDragHandlers = [];
    this.keyDownHandlers = [];
    this.resizeEventHandlers = [];
    this.lookAtChangeHandlers = [];

    const event = new MouseEvent("mousemove", {
      view: window,
      bubbles: true,
      cancelable: true,
      clientX: window.event ? window.event.clientX : 0,
      clientY: window.event ? window.event.clientY : 0,
    });
    this.node.dispatchEvent(event);

    window.addEventListener("resize", (e) => {
      this.resize();
      for (let handler of this.resizeEventHandlers) {
        handler();
      }
    });
    this.node.addEventListener("mouseenter", (e) => {
      this.mouseHovered = true;
      for (let handler of this.mouseEnterHandlers) {
        handler(e);
      }
    });
    this.node.addEventListener("mouseleave", (e) => {
      this.mouseHovered = false;
      this.dragging = false;
      for (let handler of this.mouseLeaveHandlers) {
        handler(e);
      }
    });
    this.node.addEventListener("mousedown", (e) => {
      this.dragging = true;
      for (let handler of this.mouseDownHandlers) {
        handler(e);
      }
    });
    this.node.addEventListener("mouseup", (e) => {
      this.dragging = false;
      for (let handler of this.mouseUpHandlers) {
        handler(e);
      }
    });
    this.node.addEventListener("mousemove", (e) => {
      this.updateMousePosition(e);
      for (let handler of this.mouseMoveHandlers) {
        handler(e);
      }

      if (this.dragging) {
        for (let handler of this.mouseDragHandlers) {
          handler(e);
        }
      }
    });
    this.node.addEventListener("mousewheel", (e) => {
      this.updateMousePosition(e);
      if (this.zoomable) {
        let realZoomLevel = this.camera.getZoomLevel().real();
        if (this.zoomWithPos) {
          this.zoom(this.canvasMousePos.x, this.canvasMousePos.y, realZoomLevel - e.deltaY / 2000);
        } else {
          this.justZoom(realZoomLevel - e.deltaY / 2000);
        }
      }

      for (let handler of this.mouseWheelHandlers) {
        handler(e);
      }
    });
    // Note :: you should set tabIndex to -1 of canvas element to use this handlers
    this.node.addEventListener("keydown", (e) => {
      for (let handler of this.keyDownHandlers) {
        handler(e);
      }
    });
    this.lookAtChangeListener = () => {
      for (let handler of this.lookAtChangeHandlers) {
        handler();
      }
    };

    fastInterval(() => {
      if (this.mouseHovered) {
        this.updateCanvasMousePosition();
      }
    });

    const c = this.context;

    this.tool = {
      drawText: function (text, x, y) {
        c.fillText(text, x, y);
      },
      drawText2: function (text, x, y, xo = 1, yo = 1, color) {
        if (color) c.fillStyle = color;

        let metrics = c.measureText(text);
        let textHeight = metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;

        // horizontal align (xo = -1, 0, 1)
        let _x = x + ((xo - 1) * metrics.width) / 2;

        // vertical align (yo = -1, 0, 1)
        let _y = y - ((yo - 1) * textHeight) / 2;

        c.fillText(text, _x, _y);
      },
      drawMidWidthText: function (text, x, y) {
        let metrics = c.measureText(text);
        c.fillText(text, x - metrics.width / 2, y);
      },
      drawMidHeightText: function (text, x, y) {
        let metrics = c.measureText(text);
        let textHeight = metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;
        c.fillText(text, x, y + textHeight / 2);
      },
      drawCenteredText: function (text, x, y, fillStyle) {
        if (fillStyle) c.fillStyle = fillStyle;
        let metrics = c.measureText(text);
        let textHeight = metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;
        c.fillText(text, x - metrics.width / 2, y + textHeight / 2);
      },
      drawRightAlignedMidHeightText: function (text, x, y) {
        let metrics = c.measureText(text);
        let textHeight = metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;
        c.fillText(text, x - metrics.width, y + textHeight / 2);
      },
      drawColorPaddedMidHeightText: function (text, x, y, padX, padY, fillColor, textColor) {
        let metrics = c.measureText(text);
        let textPadWidth = metrics.width + padX * 2;
        let textHeight = metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;
        let textPadHeight = textHeight + padY * 2;

        c.fillStyle = fillColor;
        c.fillRect(x, y - textHeight / 2 - padY, textPadWidth, textPadHeight);
        c.fillStyle = textColor;
        c.fillText(text, x + padX, y + textHeight / 2);
      },
      drawColorPaddedCenteredText: function (text, x, y, padX, padY, fillColor, textColor) {
        let metrics = c.measureText(text);
        let textPadWidth = metrics.width + padX * 2;
        let textHeight = metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;
        let textPadHeight = textHeight + padY * 2;

        c.fillStyle = fillColor;
        c.fillRect(x - metrics.width / 2 - padX, y - textHeight / 2 - padY, textPadWidth, textPadHeight);
        c.fillStyle = textColor;
        c.fillText(text, x - metrics.width / 2, y + textHeight / 2);
      },
      drawLine: function (x1, y1, x2, y2, color, thickness) {
        if (color) c.strokeStyle = color;
        if (thickness) c.lineWidth = thickness;
        c.beginPath();
        c.moveTo(parseInt(x1) + 0.5, parseInt(y1));
        c.lineTo(parseInt(x2) + 0.5, parseInt(y2));
        c.stroke();
      },
      drawDotLine: function (x1, y1, x2, y2, dash = [5, 5]) {
        c.setLineDash(dash);
        c.beginPath();
        c.moveTo(parseInt(x1), parseInt(y1));
        c.lineTo(parseInt(x2), parseInt(y2));
        c.stroke();
      },
      drawCircle: function (x, y, r, strokeColor, strokeWidth) {
        if (strokeColor) c.strokeStyle = strokeColor;
        if (strokeWidth) c.lineWidth = strokeWidth;

        c.beginPath();
        c.arc(x, y, r, 0, 2 * Math.PI);
        c.stroke();
      },
      drawRect: function (x, y, w, h, strokeColor, strokeWidth) {
        if (strokeColor) c.strokeStyle = strokeColor;
        if (strokeWidth) c.lineWidth = strokeWidth;
        c.strokeRect(x, y, w, h);
      },
      strokeColor: function (color) {
        c.strokeStyle = color;
      },
      strokeWidth: function (thickness) {
        c.lineWidth = thickness;
      },
      fillRect: function (x, y, w, h, fillStyle) {
        if (fillStyle) c.fillStyle = fillStyle;
        c.fillRect(x, y, w, h);
      },
      fillColor: function (color) {
        c.fillStyle = color;
      },
      fillCircle: function (x, y, r, fillStyle) {
        if (fillStyle) c.fillStyle = fillStyle;
        c.beginPath();
        c.arc(x, y, r, 0, 2 * Math.PI);
        c.fill();
      },
      drawAndFillCircle: function (x, y, r, fillColor, strokeColor, strokeWidth) {
        if (fillColor) c.fillStyle = fillColor;
        if (strokeColor) c.strokeStyle = strokeColor;
        if (strokeWidth) c.lineWidth = strokeWidth;

        c.beginPath();
        c.arc(x, y, r, 0, 2 * Math.PI);
        c.fill();
        c.beginPath();
        c.arc(x, y, r, 0, 2 * Math.PI);
        c.stroke();
      },
      drawAndFillCircleWithBlur: function (x, y, r, fillColor, strokeColor, strokeWidth, blur, color) {
        if (fillColor) c.fillStyle = fillColor;
        if (strokeColor) c.strokeStyle = strokeColor;
        if (strokeWidth) c.lineWidth = strokeWidth;

        if (blur !== undefined) {
          if (color) c.shadowColor = color;
          c.shadowBlur = blur;
          c.shadowOffsetX = 0;
          c.shadowOffsetY = 0;
        }

        c.beginPath();
        c.arc(x, y, r, 0, 2 * Math.PI);
        c.stroke();

        c.shadowBlur = 0;

        c.beginPath();
        c.arc(x, y, r, 0, 2 * Math.PI);
        c.fill();
      },
      drawAndFillRect: function (x, y, w, h, fillColor, strokeColor, strokeWidth) {
        if (fillColor) c.fillStyle = fillColor;
        if (strokeColor) c.strokeStyle = strokeColor;
        if (strokeWidth) c.lineWidth = strokeWidth;

        c.fillRect(x, y, w, h);
        c.strokeRect(x, y, w, h);
      },
      drawAndFillCenteredRect: function (x, y, w, h, fillColor, strokeColor, strokeWidth) {
        if (fillColor) c.fillStyle = fillColor;
        if (strokeColor) c.strokeStyle = strokeColor;
        if (strokeWidth) c.lineWidth = strokeWidth;

        c.fillRect(x - w / 2, y, w, h);
        c.strokeRect(x - w / 2, y, w, h);
      },
      drawAndFillCenteredRectWithPadding: function (x, y, w, h, padding, fillColor, strokeColor, strokeWidth) {
        if (fillColor) c.fillStyle = fillColor;
        if (strokeColor) c.strokeStyle = strokeColor;
        if (strokeWidth) c.lineWidth = strokeWidth;

        c.fillRect(x - w / 2 - padding, y - padding, w + padding * 2, h + padding * 2);
        c.strokeRect(x - w / 2 - padding, y - padding, w + padding * 2, h + padding * 2);
      },
      drawImage(image, x, y, w, h) {
        c.drawImage(image, x, y, w, h);
      },
      drawVetricalCenterImage: function (image, x, y, w, h) {
        c.drawImage(image, x, y - h / 2, w, h);
      },
      drawCenteredImage: function (image, x, y, w, h) {
        c.drawImage(image, x - w / 2, y - h / 2, w, h);
      },
      drawCenteredRoundImage: function (image, x, y, r) {
        c.save();
        c.beginPath();
        c.arc(x, y, r, 0, Math.PI * 2, true);
        c.closePath();
        c.clip();

        c.drawImage(image, x - r, y - r, r * 2, r * 2);

        c.beginPath();
        c.arc(x, y, r, 0, Math.PI * 2, true);
        c.clip();
        c.closePath();
        c.restore();
      },
      drawClipedContext: (cliper, func) => {
        c.save();
        cliper(c);
        c.clip();
        func(c);
        c.restore();
      },
      getTextWidth: (text) => {
        let metrics = c.measureText(text);
        return metrics.width;
      },
      getTextHeight: function (text) {
        let metrics = c.measureText(text);
        return metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;
      },
      setOpacity: function (opacity) {
        c.globalAlpha = opacity;
      },
    };
  }

  get fps() {
    return 1000 / this.renderPeriod;
  }

  addMouseDragEventHandler(handler) {
    this.mouseDragHandlers.push(handler);
  }

  addMouseDownEventHandler(handler) {
    this.mouseDownHandlers.push(handler);
  }

  addMouseUpEventHandler(handler) {
    this.mouseUpHandlers.push(handler);
  }

  addMouseMoveEventHandler(handler) {
    this.mouseMoveHandlers.push(handler);
  }

  addMouseWheelEventHandler(handler) {
    this.mouseWheelHandlers.push(handler);
  }

  addResizeEventHandler(handler) {
    this.resizeEventHandlers.push(handler);
  }

  addLookAtChangeEventHandler(handler) {
    this.lookAtChangeHandlers.push(handler);
  }

  addKeyDownHandler(handler) {
    this.keyDownHandlers.push(handler);
  }

  getHighDpiCanvasContext = (canvasObject) => {
    let w = canvasObject.width;
    let h = canvasObject.height;

    canvasObject.width = w * this.pixelRate;
    canvasObject.height = h * this.pixelRate;
    canvasObject.style.width = w + "px";
    canvasObject.style.height = h + "px";

    let context = canvasObject.getContext("2d");
    if (this.camera) {
      let zoomLevel = this.camera.getZoomLevel().get();
      context.scale(zoomLevel, zoomLevel);
    }
    context.setTransform(1, 0, 0, 1, 0.5, 0.5);
    context.imageSmoothingEnabled = true;

    return context;
  };

  getContext() {
    return this.context;
  }

  resize = () => {
    let tempCanvas = document.createElement("canvas");
    tempCanvas.width = this.width;
    tempCanvas.height = this.height;

    let tempContext = this.getHighDpiCanvasContext(tempCanvas);

    if (this.context.canvas.width === 0 || this.context.canvas.height === 0) {
      this.sizeSync();
      return;
    }

    tempContext.drawImage(this.context.canvas, 0, 0);
    this.sizeSync();

    if (tempContext.canvas.width === 0 || tempContext.canvas.height === 0) return;
    this.context.drawImage(tempContext.canvas, 0, 0);

    if (this.lookAtWhat && this.lookAtWhat.x && this.lookAtWhat.y) {
      this.lookAt(this.lookAtWhat.x, this.lookAtWhat.y);
    } else {
      this.lookAt(this.node.width / 2, this.node.height / 2);
    }
  };

  sizeSync = () => {
    this.node.width = this.parentNode.clientWidth;
    this.node.height = this.parentNode.clientHeight;

    this.context = this.getHighDpiCanvasContext(this.node);

    this.width = this.node.width;
    this.height = this.node.height;
  };

  init() {
    let zoomLevel = this.camera.getZoomLevel().get();
    let lookAt = this.camera.getLookAt();

    this.cameraTranslateX = lookAt.x - this.width / 2 / zoomLevel;
    this.cameraTranslateY = lookAt.y - this.height / 2 / zoomLevel;

    this.context.clearRect(0, -1, this.width, this.height + 1);
    this.context.scale(zoomLevel, zoomLevel);
    // this.context.setTransform(1, 0, 0, 1, 0.5, 0.5);
    this.context.translate(-this.cameraTranslateX, -this.cameraTranslateY);
  }

  initOptions() {
    this.setFont(this.context, 20, false);
    this.context.textAlign = "left";
    this.context.lineWidth = 1;
    this.context.setLineDash([]);
  }

  // set font size (depend on pixel rate, bigger if pixel rate is bigger)
  setFont(size, bold = false) {
    const fontSize = size * this.pixelRate;
    this.context.font = `${bold ? "bold" : ""} ${fontSize}px Noto Sans`;
    return fontSize;
  }

  // set font size (same size on every resolution)
  setStaticFont(size, bold = false) {
    Util.canvas.setFont(this.context, size, bold);

    return size;
  }

  /* ---------------------------------- Factory functions ---------------------------------- */
  setZoomable(zoomable) {
    this.zoomable = zoomable;

    return this;
  }

  setZoomRange(min, max) {
    this.camera.setZoomLevelRange(min, max);

    return this;
  }

  setZoomRate(rate) {
    this.camera.setZoomLevel(rate);

    return this;
  }

  finalize() {
    this.renderPeriod = currentMicroProcessTime() - this.lastRenderTime;
    this.lastRenderTime = currentMicroProcessTime();
  }

  look(x, y, zoom = 1) {
    this.camera.setLookAt(x, y);
    this.camera.setZoomLevel(zoom);
    this.lookAtChangeListener();
  }

  lookAt(object, y) {
    this.lookAtWhat = y ? new Point2D(object, y) : object;
    this.camera.setLookAt(this.lookAtWhat.x, this.lookAtWhat.y);
    this.lookAtChangeListener();
  }

  lookImmediate(x, y, fixed = false) {
    if (fixed) {
      this.lookAtWhat = new Point2D(x, y);
    }
    this.camera.setRealLookAt(x, y);
    this.lookAtChangeListener();
  }

  zoom(x, y, zoomLevel) {
    this.camera.setLookAt(x, y);
    this.camera.setZoomLevel(zoomLevel);
  }

  justZoom(zoomLevel) {
    this.camera.setZoomLevel(zoomLevel);
  }

  zoomImmediate(zoomLevel) {
    this.camera.setRealZoomLevel(zoomLevel);
  }

  render = () => {
    let a = Math.floor(this.renderPeriod);

    this.init();

    for (let layer of this.drawableLayers) {
      if (layer.shouldRerender) layer.draw(this.context, this, this.tool, this.width, this.height);
      layer.renderCompleted();
      this.initOptions();
    }

    // Zoom Initialize
    this.context.setTransform(1, 0, 0, 1, 0.5, 0.5);

    for (let layer of this.staticDrawableLayers) {
      if (layer.shouldRerender) layer.draw(this.context, this, this.tool, this.width, this.height);
      layer.renderCompleted();
      this.initOptions();
    }

    this.finalize();

    // setTimeout(this.render);
    this.animationFrameRequester = requestAnimationFrame(this.render);
  };

  registerLayer(layer) {
    if (layer) {
      this.drawableLayers.push(layer);

      if (layer.registerRef && typeof layer.registerRef === "function") {
        layer.registerRef(this);
      } else {
        layer.ref = this;
      }
    } else {
      console.error(`Can't register layer as null!`);
    }
  }

  registerStaticLayer(layer) {
    if (layer) {
      this.staticDrawableLayers.push(layer);
      layer.ref = this;
    }
  }

  updateMousePosition = (e) => {
    let rect = this.node.getBoundingClientRect();

    this.mousePos = {
      x: this.pixelRate * (e.clientX - rect.left),
      y: this.pixelRate * (e.clientY - rect.top),
    };
  };

  updateCanvasMousePosition = () => {
    let lookAt = this.camera.getLookAt();
    let zoomLevel = this.camera.getZoomLevel().get();

    this.canvasMousePos = {
      x: lookAt.x + (this.mousePos.x - this.width / 2) / zoomLevel,
      y: lookAt.y + (this.mousePos.y - this.height / 2) / zoomLevel,
    };
  };

  getCameraRDCoordinate() {
    let lookAt = this.camera.getLookAt(true);

    return {
      x: 2 * lookAt.x - this.cameraTranslateX,
      y: 2 * lookAt.y - this.cameraTranslateY,
    };
  }

  getCameraLUCoordinate() {
    return {
      x: this.cameraTranslateX,
      y: this.cameraTranslateY,
    };
  }

  getCameraRealBounds() {
    let realZoomLevel = this.camera.getZoomLevel().real();
    let realLookAt = this.camera.getRealLookAt();
    let lux = realLookAt.x - this.width / 2 / realZoomLevel;
    let luy = realLookAt.y - this.height / 2 / realZoomLevel;
    let rdx = 2 * realLookAt.x - lux;
    let rdy = 2 * realLookAt.y - luy;
    return new Bounds(lux, luy, rdx - lux, rdy - luy);
  }

  willDestroy() {
    this.init();
    window.removeEventListener("resize", this.resize);

    cancelAnimationFrame(this.animationFrameRequester);
  }

  destroy() {
    this.willDestroy();
  }
}

export default CanvasEngine;
