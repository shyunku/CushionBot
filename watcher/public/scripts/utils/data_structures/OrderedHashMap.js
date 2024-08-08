export default class OrderedHashMap {
  constructor() {
    this.map = new Map();
    this.keys = [];
  }

  add(key, value) {
    if (this.map.has(key)) {
      console.log(this.map.get(key));
      throw new Error(`Key ${key} already exists`);
    }
    this.map.set(key, value);
    this.keys.push(key);
  }

  get(key) {
    return this.map.get(key);
  }

  remove(key) {
    this.map.delete(key);
    this.keys = this.keys.filter((k) => k !== key);
  }

  updateKey(oldKey, newKey) {
    console.log("updateKey", oldKey, newKey);
    if (this.map.has(newKey)) return;
    const index = this.keys.indexOf(oldKey);
    if (index === -1) return;
    this.keys[index] = newKey;
    this.map.set(newKey, this.map.get(oldKey));
    this.map.delete(oldKey);
  }

  clear() {
    this.map.clear();
    this.keys = [];
  }

  toArray() {
    return this.keys.map((key) => this.map.get(key));
  }
}
