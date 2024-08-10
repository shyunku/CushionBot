import Vector2D from "./Vector2D.js";

class Point2D {
  constructor(x, y) {
    if (!y) {
      this.x = x.x;
      this.y = x.y;
    } else {
      this.x = x;
      this.y = y;
    }
  }

  import(obj) {
    this.x = obj.x;
    this.y = obj.y;
  }

  add(vector, factor = 1) {
    if (vector instanceof Vector2D) {
      this.x += vector.x * factor;
      this.y += vector.y * factor;
    } else {
      this.x += vector * factor;
      this.y += vector * factor;
    }

    return this;
  }

  subtract(x, y) {
    return new Vector2D(this.x - x, this.y - y);
  }

  subtractByPoint(point) {
    return new Vector2D(this.x - point.x, this.y - point.y);
  }

  distance(point) {
    return Math.sqrt((this.x - point.x) ** 2 + (this.y - point.y) ** 2);
  }
}

export default Point2D;
