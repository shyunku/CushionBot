import Crypto from "../../utils/crypto.js";

export class Intervals {
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

export class Interval {
  constructor(start, end, count = 1) {
    this.id = Crypto.hash(`${start}-${end}-${Crypto.uuid()}`);

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
