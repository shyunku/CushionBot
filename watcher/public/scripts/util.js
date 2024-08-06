const TimeUnit = {
  SECOND: 1000,
  MINUTE: 60 * 1000,
  HOUR: 60 * 60 * 1000,
  DAY: 24 * 60 * 60 * 1000,
};

class Intervals {
  constructor() {
    this.root = null;
  }

  add(start, end) {
    if (this.root == null) {
      this.root = new Interval(start, end);
      return;
    } else {
      this.root.add(start, end);
    }
  }

  toArray() {
    const result = [];
    let current = this.root;
    while (current != null) {
      result.push(current);
      current = current.next;
    }
    return result;
  }

  toSlice() {
    const set = new Set();
    let current = this.root;
    while (current != null) {
      // set.add(current.start + (set.has(current.start) ? 1 : 0));
      set.add(current.start);
      set.add(current.end);
      current = current.next;
    }
    const arr = Array.from(set);
    arr.sort((a, b) => a - b);
    return arr;
  }

  getCount(time) {
    let current = this.root;
    while (current != null) {
      if (current.start <= time && time <= current.end) {
        return current.count;
      }
      current = current.next;
    }
    return 0;
  }
}

class Interval {
  constructor(start, end, count = 1) {
    this.id = hashStrAsStr(`${start}-${end}-${Date.now()}`);

    this.start = start;
    this.end = end;
    this.count = count;

    this.prev = null;
    this.next = null;
  }

  addPrev(start, end) {
    if (start >= end) return;
    if (this.prev == null) {
      this.prev = new Interval(start, end);
      this.prev.next = this;
    } else {
      this.prev.add(start, end);
    }
  }

  addNext(start, end) {
    if (start >= end) return;
    if (this.next == null) {
      this.next = new Interval(start, end);
      this.next.prev = this;
    } else {
      this.next.add(start, end);
    }
  }

  add(start, end) {
    if (start >= end) return;

    if (end < this.start) {
      this.addPrev(start, end);
    } else if (start > this.end) {
      this.addNext(start, end);
    } else {
      if (start < this.start && end > this.end) {
        this.addPrev(start, this.start);
        this.addNext(this.end, end);
        this.count++;
      } else if (start < this.start) {
        this.addPrev(start, this.start);
        this.add(this.start, end);
      } else if (end > this.end) {
        this.addNext(this.end, end);
        this.add(start, this.end);
      } else {
        if (start === this.start && end === this.end) {
          this.count++;
        } else if (start === this.start) {
          // stick to the start
          const newInterval = new Interval(end, this.end, this.count);
          newInterval.next = this.next;
          if (!!this.next) this.next.prev = newInterval;
          this.next = newInterval;
          newInterval.prev = this;

          this.end = end;
          this.count++;
        } else if (end === this.end) {
          // stick to the end
          const newInterval = new Interval(this.start, start, this.count);
          newInterval.prev = this.prev;
          if (!!this.prev) this.prev.next = newInterval;
          this.prev = newInterval;
          newInterval.next = this;

          this.start = start;
          this.count++;
        } else {
          // right split
          const rightInterval = new Interval(end, this.end, this.count);
          rightInterval.next = this.next;
          if (!!this.next) this.next.prev = rightInterval;
          this.next = rightInterval;
          rightInterval.prev = this;

          this.end = end;
          this.count++;

          // left split
          const leftInterval = new Interval(this.start, start, this.count);
          leftInterval.prev = this.prev;
          if (!!this.prev) this.prev.next = leftInterval;
          this.prev = leftInterval;
          leftInterval.next = this;

          this.start = start;
          this.count++;
        }
      }
    }
  }
}

class Color {
  constructor(r, g, b) {
    this.r = r;
    this.g = g;
    this.b = b;
  }

  static generateRandomColorWithSeed(seed) {
    seed += "$$seed";
    const rHash = Math.floor(hashStr(seed + "r") * 255);
    const gHash = Math.floor(hashStr(seed + "g") * 255);
    const bHash = Math.floor(hashStr(seed + "b") * 255);
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
}

async function httpGet(url) {
  return new Promise((resolve, reject) => {
    try {
      if (!url.startsWith("/")) {
        url = `/${url}`;
      }
      url = `http://${botHost}:${botPort}${url}`;

      $.ajax({
        url,
        type: "GET",
        success: (data) => {
          resolve(data);
        },
        error: (err) => {
          reject(err);
        },
      });
    } catch (err) {
      reject(err);
    }
  });
}

function cyrb128(str) {
  let h1 = 1779033703,
    h2 = 3144134277,
    h3 = 1013904242,
    h4 = 2773480762;
  for (let i = 0, k; i < str.length; i++) {
    k = str.charCodeAt(i);
    h1 = h2 ^ Math.imul(h1 ^ k, 597399067);
    h2 = h3 ^ Math.imul(h2 ^ k, 2869860233);
    h3 = h4 ^ Math.imul(h3 ^ k, 951274213);
    h4 = h1 ^ Math.imul(h4 ^ k, 2716044179);
  }
  h1 = Math.imul(h3 ^ (h1 >>> 18), 597399067);
  h2 = Math.imul(h4 ^ (h2 >>> 22), 2869860233);
  h3 = Math.imul(h1 ^ (h3 >>> 17), 951274213);
  h4 = Math.imul(h2 ^ (h4 >>> 19), 2716044179);
  (h1 ^= h2 ^ h3 ^ h4), (h2 ^= h1), (h3 ^= h1), (h4 ^= h1);
  return [h1 >>> 0, h2 >>> 0, h3 >>> 0, h4 >>> 0];
}

function sfc32(a, b, c, d) {
  return function () {
    a |= 0;
    b |= 0;
    c |= 0;
    d |= 0;
    let t = (((a + b) | 0) + d) | 0;
    d = (d + 1) | 0;
    a = b ^ (b >>> 9);
    b = (c + (c << 3)) | 0;
    c = (c << 21) | (c >>> 11);
    c = (c + t) | 0;
    return (t >>> 0) / 4294967296;
  };
}

function fnv1a(str) {
  let hash = 2166136261;
  for (let i = 0; i < str.length; i++) {
    hash ^= str.charCodeAt(i);
    hash += (hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24);
  }
  return hash >>> 0;
}

function hashStr(str) {
  const seeds = cyrb128(str + "$seed");
  return sfc32(seeds[0], seeds[1], seeds[2], seeds[3])();
}

function hashStrAsStr(str) {
  const seeds = cyrb128(str + "$seed");
  const seed = sfc32(seeds[0], seeds[1], seeds[2], seeds[3])();
  return Math.floor(seed * 10000000000).toString(16);
}

function fastInterval(fn, interval) {
  fn();
  return setInterval(fn, interval);
}

function dateStr(date = Date.now()) {
  const d = new Date(date);
  const year = d.getFullYear() % 100;
  const month = d.getMonth() + 1;
  const day = d.getDate();
  const md = `${month}`.padStart(2, "0");
  const dd = `${day}`.padStart(2, "0");
  return `${year}.${md}.${dd}`;
}

function startOfDay(time = Date.now()) {
  const date = new Date(time);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0, 0, 0);
}

function endOfDay(time = Date.now()) {
  const date = new Date(time);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59, 999);
}

function durationStr(duration) {
  if (duration === 0) return "0초";

  const days = Math.floor(duration / (24 * 60 * 60 * 1000));
  const hours = Math.floor((duration % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000));
  const minutes = Math.floor((duration % (60 * 60 * 1000)) / (60 * 1000));
  const seconds = Math.floor((duration % (60 * 1000)) / 1000);

  const parts = [];
  if (days > 0) {
    parts.push(`${days}일`);
  }
  if (hours > 0) {
    parts.push(`${hours}시간`);
  }
  if (minutes > 0) {
    parts.push(`${minutes}분`);
  }
  if (seconds > 0) {
    parts.push(`${seconds}초`);
  }
  return parts.join(" ");
}
