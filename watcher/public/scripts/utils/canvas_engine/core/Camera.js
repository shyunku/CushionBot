import FlexibleValue from "./FlexibleValue.js";

class Camera {
  // coordinate of camera indicates center of view, always.
  constructor(x, y, zoomLevel) {
    this.x = new FlexibleValue(x);
    this.y = new FlexibleValue(y);
    this.zoomLevel = new FlexibleValue(zoomLevel);

    this.minZoomLevel = 0.1;
    this.maxZoomLevel = 30;
  }

  setZoomLevelRange = (min, max) => {
    this.minZoomLevel = min;
    this.maxZoomLevel = max;
  };

  getLookAt(isInt = false) {
    return {
      x: this.x.get(isInt),
      y: this.y.get(isInt),
    };
  }

  getRealLookAt() {
    return {
      x: this.x.real(),
      y: this.y.real(),
    };
  }

  setLookAt(x, y) {
    this.x.set(x);
    this.y.set(y);
  }

  setRealLookAt(x, y) {
    this.x.setReal(x);
    this.y.setReal(y);
  }

  getZoomLevel() {
    return this.zoomLevel;
  }

  setZoomLevel(newZoomLevel) {
    if (newZoomLevel < this.minZoomLevel) {
      newZoomLevel = this.minZoomLevel;
    }

    if (newZoomLevel > this.maxZoomLevel) {
      newZoomLevel = this.maxZoomLevel;
    }

    this.zoomLevel.set(newZoomLevel);
  }

  setRealZoomLevel(newZoomLevel) {
    if (newZoomLevel < this.minZoomLevel) {
      newZoomLevel = this.minZoomLevel;
    }

    if (newZoomLevel > this.maxZoomLevel) {
      newZoomLevel = this.maxZoomLevel;
    }

    this.zoomLevel.setReal(newZoomLevel);
  }

  destroy() {
    this.x.destroy();
    this.y.destroy();
    this.zoomLevel.destroy();
  }
}

export default Camera;
