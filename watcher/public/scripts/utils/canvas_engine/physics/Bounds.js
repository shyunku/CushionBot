import Point2D from "./Point2D.js";

class Bounds {
  constructor(x, y, w, h) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
    this.cx = x + w / 2;
    this.cy = y + h / 2;
  }

  importAsCenterPoint = (cx, cy, w, h) => {
    this.x = cx - w / 2;
    this.y = cy - h / 2;
    this.w = w;
    this.h = h;
    this.cx = cx;
    this.cy = cy;

    return this;
  };

  rx() {
    return this.x + this.w;
  }

  dy() {
    return this.y + this.h;
  }

  randomInnerPoint() {
    return new Point2D(Math.random() * this.w + this.x, Math.random() * this.h + this.y);
  }
}

export default Bounds;
