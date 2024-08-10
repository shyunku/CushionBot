import Vector2D from "./Vector2D.js";

class RVector2D {
  constructor(direction = 0, size = 0) {
    this.direction = direction;
    this.size = size;
  }

  importAsVector(x, y) {
    this.direction = Math.atan2(y, x);
    this.size = new Vector2D(x, y).normalize();

    return this;
  }

  add(value, factor = 1) {
    this.size += value * factor;
  }

  mul(value) {
    this.size *= value;
  }

  addVector(rvector) {
    let v1 = this.toVector2D();
    let v2 = rvector.toVector2D();

    let v3 = v1.add(v2);
    this.fromVector2D(v3);
  }

  setDirectionByVector(vector) {
    this.direction = Math.atan2(vector.y, vector.x);
  }

  setDirectionByAngle(angle) {
    this.direction = angle;
  }

  flipXtoNegative() {
    let x = -Math.abs(Math.cos(this.direction));
    let y = Math.sin(this.direction);
    this.direction = Math.atan2(y, x);
  }

  flipYtoNegative() {
    let x = Math.cos(this.direction);
    let y = -Math.abs(Math.sin(this.direction));
    this.direction = Math.atan2(y, x);
  }

  flipXtoPositive() {
    let x = Math.abs(Math.cos(this.direction));
    let y = Math.sin(this.direction);
    this.direction = Math.atan2(y, x);
  }

  flipYtoPositive() {
    let x = Math.cos(this.direction);
    let y = Math.abs(Math.sin(this.direction));
    this.direction = Math.atan2(y, x);
  }

  getAngle() {
    return this.direction;
  }

  fromVector2D(vector2D) {
    return this.importAsVector(vector2D.x, vector2D.y);
  }

  toVector2D() {
    return new Vector2D(this.size * Math.cos(this.direction), this.size * Math.sin(this.direction));
  }
}

export default RVector2D;
