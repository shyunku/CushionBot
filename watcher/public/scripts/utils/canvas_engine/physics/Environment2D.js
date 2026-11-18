import Bounds from "./Bounds.js";
import Point2D from "./Point2D.js";
import RigidBody from "./RigidBody.js";
import RVector2D from "./RVector2D.js";
import Vector2D from "./Vector2D.js";

class Environment2D {
  constructor() {
    this.boundary = null;
    this.gravity = 0;
    this.resistence = 1;

    this.collidableLayerMap = {};
    this.enabledCollisions = {};
  }

  // Target Layer would affected by 'to' Layer as collision layer
  enableCollision = (targetLayerName, toLayerName, bidirectional) => {
    if (!this.enabledCollisions.hasOwnProperty(targetLayerName)) {
      this.enabledCollisions[targetLayerName] = {};
    }

    this.enabledCollisions[targetLayerName][toLayerName] = true;
    return this;
  };

  addRigidBodyToCollidableLayer = (layerName, body) => {
    if (!this.collidableLayerMap.hasOwnProperty(layerName)) {
      // console.log(`[CollidableLayer] new layer added: ${layerName}`);
      this.collidableLayerMap[layerName] = [];
    }

    this.collidableLayerMap[layerName].push(body);
  };

  setBounds(boundary) {
    if (boundary instanceof Bounds) {
      this.boundary = boundary;
    }

    return this;
  }

  setRawBounds(lux, luy, rdx, rdy) {
    this.boundary = new Bounds(lux, luy, rdx - lux, rdy - luy);

    return this;
  }

  setGravity(gravity) {
    this.gravity = gravity;
    return this;
  }

  setResistence(resistence) {
    this.resistence = resistence;
    return this;
  }

  getResistedVector = (vector, period, resistence) => {
    if (vector instanceof Vector2D) {
      vector = vector.mul(1 - this.resistence * resistence * period);
    } else if (vector instanceof RVector2D) {
      vector.mul(1 - this.resistence * resistence * period);
    } else {
      console.error(`Can't handle type of ${typeof vector}`);
    }

    return vector;
  };

  applyGravity = (rigidBody) => {
    rigidBody.velocity.addVector(new RVector2D().importAsVector(0, this.gravity));
  };

  applyCollisions = (rigidBody) => {
    const { pos, size, shape, name } = rigidBody;
    let collisionLayersAffectedBy = this.enabledCollisions[name] || {};

    // console.log(rigidBody);

    for (let collidableLayerName in collisionLayersAffectedBy) {
      let collidableObjects = this.collidableLayerMap[collidableLayerName] || [];
      for (let opp of collidableObjects) {
        if (!(opp instanceof RigidBody)) continue;

        let oppObjectShape = opp.shape;

        switch (shape) {
          case RigidBodyShapeConstants.CIRCLE:
            switch (oppObjectShape) {
              case RigidBodyShapeConstants.CIRCLE:
                let intersectRange = opp.size + size;
                let intersected = opp.pos.distance(pos) < intersectRange;
                if (intersected) {
                  let resistDirection = Math.atan2(pos.y - opp.pos.y, pos.x - opp.pos.x);

                  rigidBody.pos.x = opp.pos.x + Math.cos(resistDirection) * intersectRange;
                  rigidBody.pos.y = opp.pos.y + Math.sin(resistDirection) * intersectRange;
                  rigidBody.velocity.addVector(opp.velocity);
                  rigidBody.velocity.setDirectionByAngle(resistDirection);
                }
                break;
              case RigidBodyShapeConstants.SQUARE:
                let oppBounds = opp.getSquareBounds();

                let honestlyIntersected =
                  (pos.x >= oppBounds.cx - oppBounds.w / 2 &&
                    pos.x <= oppBounds.cx + oppBounds.w / 2 &&
                    pos.y <= oppBounds.cy + oppBounds.h / 2 + size &&
                    pos.y >= oppBounds.cy - oppBounds.h / 2 - size) ||
                  (pos.y <= oppBounds.cy + oppBounds.h / 2 &&
                    pos.y >= oppBounds.cy - oppBounds.h / 2 &&
                    pos.x >= oppBounds.cx - oppBounds.w / 2 - size &&
                    pos.x <= oppBounds.cx + oppBounds.w / 2 + size);
                let vertexIntersected = Array(4)
                  .fill(null)
                  .map((e, i) => (Math.PI * (1 + i * 2)) / 4)
                  .reduce((acc, angle) => {
                    let vertexPos = new Point2D(
                      oppBounds.cx + (Math.cos(angle) * Math.sqrt(2) * oppBounds.w) / 2,
                      oppBounds.cy + (Math.sin(angle) * Math.sqrt(2) * oppBounds.h) / 2
                    );
                    return acc || vertexPos.distance(pos) < size;
                  }, false);

                if (honestlyIntersected || vertexIntersected) {
                  let collideAngle = Util.refineAngle(Math.atan2(-pos.y + oppBounds.cy, pos.x - oppBounds.cx));

                  if (!honestlyIntersected && vertexIntersected) {
                    // vertex collision
                    let vertexPos, resistDirection;

                    if (collideAngle < Math.PI / 2) {
                      // Right-Up-side vertex resist
                      // console.log(`[Collision-Vertex] Right-Up`);
                      vertexPos = new Point2D(oppBounds.cx + oppBounds.w / 2, oppBounds.cy - oppBounds.h / 2);
                    } else if (collideAngle < Math.PI) {
                      // Left-Up-side vertex resist
                      // console.log(`[Collision-Vertex] Left-Up`);
                      vertexPos = new Point2D(oppBounds.cx - oppBounds.w / 2, oppBounds.cy - oppBounds.h / 2);
                    } else if (collideAngle < (Math.PI * 3) / 2) {
                      // Left-Down-side vertex resist
                      // console.log(`[Collision-Vertex] Left-Down`);
                      vertexPos = new Point2D(oppBounds.cx - oppBounds.w / 2, oppBounds.cy + oppBounds.h / 2);
                    } else {
                      // Right-Down-side vertex resist
                      // console.log(`[Collision-Vertex] Right-Down`);
                      vertexPos = new Point2D(oppBounds.cx + oppBounds.w / 2, oppBounds.cy + oppBounds.h / 2);
                    }

                    resistDirection = Math.atan2(pos.y - vertexPos.y, pos.x - vertexPos.x);
                    rigidBody.pos.x = vertexPos.x + Math.cos(resistDirection) * (size + 1);
                    rigidBody.pos.y = vertexPos.y + Math.sin(resistDirection) * (size + 1);
                    rigidBody.velocity.setDirectionByAngle(resistDirection);
                  } else {
                    // linear collision
                    if (collideAngle > Math.PI / 4 && collideAngle <= (Math.PI * 3) / 4) {
                      // console.log(`[Collision-Linear] Up`);
                      // Up-side resist of square
                      rigidBody.pos.y = oppBounds.cy - oppBounds.h / 2 - size;
                      rigidBody.velocity.flipYtoNegative();
                    } else if (collideAngle > (Math.PI * 3) / 4 && collideAngle <= (Math.PI * 5) / 4) {
                      // console.log(`[Collision-Linear] Left`);
                      // Left-side resist of square
                      rigidBody.pos.x = oppBounds.cx - oppBounds.w / 2 - size;
                      rigidBody.velocity.flipXtoNegative();
                    } else if (collideAngle > (Math.PI * 5) / 4 && collideAngle <= (Math.PI * 7) / 4) {
                      // console.log(`[Collision-Linear] Down`);
                      // Down-side resist of squre
                      rigidBody.pos.y = oppBounds.cy + oppBounds.h / 2 + size;
                      rigidBody.velocity.flipYtoPositive();
                    } else {
                      // console.log(`[Collision-Linear] Right`);
                      // Right-side resist of squre
                      rigidBody.pos.x = oppBounds.cx + oppBounds.w / 2 + size;
                      rigidBody.velocity.flipXtoPositive();
                    }
                  }
                }
                break;
            }
            break;
          case RigidBodyShapeConstants.SQUARE:
            switch (oppObjectShape) {
              case RigidBodyShapeConstants.CIRCLE:
                console.warn(`Not supported shape collistion: ${shape} vs ${oppObjectShape}`);
                // TODO :: implement
                break;
              case RigidBodyShapeConstants.SQUARE:
                console.warn(`Not supported shape collistion: ${shape} vs ${oppObjectShape}`);
                // TODO :: implement
                break;
            }
            break;
        }
      }
    }
  };

  applyBoundaryResist = (pos, size, vel, period) => {
    if (this.boundary) {
      // Left-side resist
      if (pos.x - size < this.boundary.x) {
        pos.x = this.boundary.x + size;
        vel.flipXtoPositive();
      }

      // Right-side resist
      if (pos.x + size > this.boundary.rx()) {
        pos.x = this.boundary.rx() - size;
        vel.flipXtoNegative();
      }

      // Up-side resist
      if (pos.y - size < this.boundary.y) {
        pos.y = this.boundary.y + size;
        vel.flipYtoPositive();
      }

      // Down-side resist
      if (pos.y + size > this.boundary.dy()) {
        pos.y = this.boundary.dy() - size;
        vel.flipYtoNegative();
      }
    }
  };
}

export default Environment2D;
