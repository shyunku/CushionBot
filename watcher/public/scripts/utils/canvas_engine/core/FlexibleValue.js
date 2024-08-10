import { getEaseOutFactor } from "./Utils.js";

class FlexibleValue {
  // ASR: Approace Speed Rate
  constructor(value, opts = { factor: 0.99, useInt: false }) {
    this.value = this.destValue = value;
    this.factor = opts.factor;

    this.useInt = opts.useInt;
    this.lastCalculateTime = Date.now();
    this.calculatePeriod = 0;
  }

  set(value, withReal = false) {
    const minDiff = this.useInt ? 1 : 0.01;

    if (withReal) {
      this.setReal(value);
      return;
    }
    if (this.destValue === value) return;
    if (Math.abs(value - this.destValue) < minDiff) {
      this.value = this.destValue = value;
    }

    this.destValue = value;

    clearInterval(this.flexThread);

    this.lastCalculateTime = Date.now();
    this.flexThread = setInterval(() => {
      this.calculatePeriod = Date.now() - this.lastCalculateTime;

      const oldValue = this.value;
      this.value += (this.destValue - this.value) * getEaseOutFactor(this.factor, this.calculatePeriod);

      // console.log("flexible value set", this.value, this.destValue, oldValue);
      if (Math.abs(this.value - this.destValue) < minDiff) {
        // console.log("done");
        this.value = this.destValue;
        clearInterval(this.flexThread);
      }
      this.lastCalculateTime = Date.now();
    });
  }

  setReal(value) {
    clearInterval(this.flexThread);
    this.value = this.destValue = value;
  }

  add(value) {
    this.set(this.destValue + value);
  }

  real() {
    return this.destValue;
  }

  get(isInt = false) {
    return isInt ? parseInt(this.value) : this.value;
  }

  destroy() {
    clearInterval(this.flexThread);
  }
}

export default FlexibleValue;
