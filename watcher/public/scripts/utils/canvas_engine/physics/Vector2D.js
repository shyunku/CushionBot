class Vector2D {
  constructor(x = 0, y = 0) {
    this.x = x;
    this.y = y;
  }

  initialize() {
    this.x = 0;
    this.y = 0;
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

  addX(xOffset, factor = 1) {
    this.x += xOffset * factor;
    return this;
  }

  addY(yOffset, factor = 1) {
    this.y += yOffset * factor;
    return this;
  }

  flipXtoNegative() {
    this.x = -Math.abs(this.x);
  }

  flipYtoNegative() {
    this.y = -Math.abs(this.y);
  }

  flipXtoPositive() {
    this.x = Math.abs(this.x);
  }

  flipYtoPositive() {
    this.y = Math.abs(this.y);
  }

  div(factor) {
    this.x /= factor;
    this.y /= factor;

    return this;
  }

  mul(factor) {
    this.x *= factor;
    this.y *= factor;

    return this;
  }

  normalize() {
    return Math.sqrt(this.x ** 2 + this.y ** 2);
  }

  reverseNormalize(normalized) {
    let factor = normalized / this.normalize();
    this.mul(factor);
    return this;
  }

  redirection(x, y) {
    let originalNormalized = this.normalize();
    this.x = x;
    this.y = y;
    return this.reverseNormalize(originalNormalized);
  }

  redirectionByVector(vector) {
    let originalNormalized = this.normalize();
    if (originalNormalized === 0) return this;

    this.x = vector.x;
    this.y = vector.y;
    return this.reverseNormalize(originalNormalized);
  }
}

export default Vector2D;
