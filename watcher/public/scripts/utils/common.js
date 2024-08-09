export function fastInterval(fn, interval) {
  fn();
  return setInterval(fn, interval);
}

/**
 * compare two objects deeply and return true if they are equal
 * @param {*} a
 * @param {*} b
 * @returns {boolean}
 */
export function deepCompare(a, b) {
  if (a === b) return true;
  if (a instanceof Date && b instanceof Date) return a.getTime() === b.getTime();
  if (a === null || b === null || typeof a !== "object" || typeof b !== "object") return a === b;
  if (a.prototype !== b.prototype) return false;
  const keys = Object.keys(a);
  if (keys.length !== Object.keys(b).length) return false;
  return keys.every((k) => deepCompare(a[k], b[k]));
}

export function trimAndClearString(str) {
  if (str == null) return null;
  const result = str.replace(/\s*(<[^>]+>)\s*/g, "$1");
  if (result.length === 0) return str;
  return result;
}

export function isClass(v) {
  return typeof v === "function" && /^\s*class\s+/.test(v.toString());
}
