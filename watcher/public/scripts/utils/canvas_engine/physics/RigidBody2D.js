import Bounds from "./Bounds.js";
import Point2D from "./Point2D.js";
import RVector2D from "./RVector2D.js";

export const RigidBodyShape = {
  CIRCLE: "circle",
  SQUARE: "square",
};

class RigidBody {
  constructor(env, x, y, size, name = "unknown", shape = RigidBodyShapeConstants.CIRCLE) {
    this.env = env;
    this.pos = new Point2D(x, y);
    this.cpos = new Point2D(x, y);
    this.velocity = new RVector2D();
    this.accel = 0;
    this.size = size;
    this.width = size;
    this.height = size;
    this.collidable = false;
    this.shape = shape;
    this.name = name;
    this.static = false;
    this.resistence = 0;
    this.ignoreBoundary = false;
    this.text = null;
    this.maxVelocity = null;
  }

  addAsCollidableLayer() {
    this.env.addRigidBodyToCollidableLayer(this.name, this);
    return this;
  }

  getSquareBounds() {
    return new Bounds().importAsCenterPoint(this.cpos.x, this.cpos.y, this.width, this.height);
  }

  setCenterBounds(cx, cy, width, height) {
    this.width = width;
    this.height = height;
    this.cpos = new Point2D(cx, cy);
  }

  setStatic() {
    this.static = true;
    return this;
  }

  setResistence(resistence) {
    this.resistence = resistence;
    return this;
  }

  setInitialVelocity(x, y) {
    this.velocity = new RVector2D().importAsVector(x, y);
    return this;
  }

  setMaxVelocity(maxVelocity) {
    this.maxVelocity = maxVelocity;
    return this;
  }

  enableCollision() {
    this.collidable = true;
    return this;
  }

  setTextContent(text) {
    this.text = text;
    return this;
  }

  setIgnoreBoundary(ignoreBoundary) {
    this.ignoreBoundary = ignoreBoundary;
    return this;
  }

  distance(point) {
    return this.pos.distance(point);
  }

  update(period) {
    if (this.static) return;

    this.velocity.add(this.accel, period);

    if (this.maxVelocity && this.velocity.size > this.maxVelocity) {
      this.velocity.size = this.maxVelocity;
    }

    if (this.resistence > 0) {
      this.velocity = this.env.getResistedVector(this.velocity, period, this.resistence);
    }

    let velocityAsVector2D = this.velocity.toVector2D();

    this.pos.add(velocityAsVector2D, period);

    if (this.collidable) {
      this.env.applyCollisions(this);
    }

    this.env.applyGravity(this);

    if (!this.ignoreBoundary) {
      this.env.applyBoundaryResist(this.pos, this.size, this.velocity, period);
    }
  }
}

export default RigidBody;
