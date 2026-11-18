import Crypto from "./crypto.js";

export default class Color {
  constructor(r, g, b) {
    this.r = r;
    this.g = g;
    this.b = b;
  }

  static generateRandomColorWithSeed(seed) {
    seed += "$$seed";
    const rHash = Math.floor(Crypto.seedRand(seed + "r") * 255);
    const gHash = Math.floor(Crypto.seedRand(seed + "g") * 255);
    const bHash = Math.floor(Crypto.seedRand(seed + "b") * 255);
    return new Color(rHash, gHash, bHash);
  }

  calculateBrightness() {
    let r = this.r / 255;
    let g = this.g / 255;
    let b = this.b / 255;
    return Math.sqrt(0.299 * r * r + 0.587 * g * g + 0.114 * b * b);
  }

  calculateSaturation() {
    const max = Math.max(this.r, this.g, this.b);
    const min = Math.min(this.r, this.g, this.b);
    const delta = max - min;

    if (max === 0) {
      return 0;
    } else {
      return delta / max;
    }
  }

  adjustBrightness(brightness) {
    const ob = brightness;
    const f = ob / this.calculateBrightness();

    this.r = Math.min(255, Math.floor(this.r * f));
    this.g = Math.min(255, Math.floor(this.g * f));
    this.b = Math.min(255, Math.floor(this.b * f));

    return this;
  }

  adjustSaturation(saturation) {
    const max = Math.max(this.r, this.g, this.b);
    const min = Math.min(this.r, this.g, this.b);
    const delta = max - min;

    if (delta === 0) {
      return this;
    }

    const os = this.calculateSaturation();
    const f = saturation / os;

    this.r = Math.min(255, Math.floor((this.r - min) * f + min));
    this.g = Math.min(255, Math.floor((this.g - min) * f + min));
    this.b = Math.min(255, Math.floor((this.b - min) * f + min));

    return this;
  }

  adjustBrightnessAndSaturation(brightness, saturation) {
    // Adjust Brightness
    const currentBrightness = this.calculateBrightness();
    const brightnessFactor = brightness / currentBrightness;

    let newR = Math.min(255, Math.floor(this.r * brightnessFactor));
    let newG = Math.min(255, Math.floor(this.g * brightnessFactor));
    let newB = Math.min(255, Math.floor(this.b * brightnessFactor));

    // Adjust Saturation
    const max = Math.max(newR, newG, newB);
    const min = Math.min(newR, newG, newB);
    const delta = max - min;

    if (delta !== 0) {
      const currentSaturation = delta / max;
      const saturationFactor = saturation / currentSaturation;

      newR = Math.min(255, Math.floor((newR - min) * saturationFactor + min));
      newG = Math.min(255, Math.floor((newG - min) * saturationFactor + min));
      newB = Math.min(255, Math.floor((newB - min) * saturationFactor + min));
    }

    this.r = newR;
    this.g = newG;
    this.b = newB;

    return this;
  }

  toString() {
    return `rgb(${this.r},${this.g},${this.b})`;
  }

  copy() {
    return new Color(this.r, this.g, this.b);
  }
}
