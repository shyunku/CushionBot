function makeRandomColor() {
  const r = Math.floor(Math.random() * 255);
  const g = Math.floor(Math.random() * 255);
  const b = Math.floor(Math.random() * 255);
  return `rgb(${r},${g},${b})`;
}

function getColorByStringWithBrightness(str, lb = 0, rb = 1) {
  const rhash = hashStr("$r" + str.repeat(3));
  const ghash = hashStr("$g" + str.repeat(3));
  const bhash = hashStr("$b" + str.repeat(3));
  let r = Math.floor(rhash * 255);
  let g = Math.floor(ghash * 255);
  let b = Math.floor(bhash * 255);
  const brightness = (r * 299 + g * 587 + b * 114) / (1000 * 255);
  const ob = lb + (rb - lb) * brightness;
  const f = ob / brightness;

  r = Math.min(255, Math.floor(r * f));
  g = Math.min(255, Math.floor(g * f));
  b = Math.min(255, Math.floor(b * f));

  return `rgb(${r},${g},${b})`;
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
  const seed = fnv1a(str);
  return Math.abs(Math.sin(seed));
}

function hashStrAsStr(str) {
  const seed = fnv1a(str);
  return Math.floor(Math.abs(Math.sin(seed) * 1000000)).toString(16);
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

function timeStr(time = Date.now()) {
  const date = new Date(time);
  const hours = date.getHours();
  const minutes = date.getMinutes();
  const seconds = date.getSeconds();

  const hd = `${hours}`;
  const md = `${minutes}`.padStart(2, "0");
  const sd = `${seconds}`.padStart(2, "0");
  return `${hd}:${md}:${sd}`;
}

function startOfDay(time = Date.now()) {
  const date = new Date(time);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0, 0, 0);
}

function endOfDay(time = Date.now()) {
  const date = new Date(time);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59, 999);
}

function datetimeStr(time = Date.now()) {
  return `${dateStr(time)} ${timeStr(time)}`;
}

function durationStr(duration) {
  const hours = Math.floor(duration / (60 * 60 * 1000));
  const minutes = Math.floor((duration % (60 * 60 * 1000)) / (60 * 1000));
  const seconds = Math.floor((duration % (60 * 1000)) / 1000);

  const hd = `${hours}`;
  const md = `${minutes}`.padStart(2, "0");
  const sd = `${seconds}`.padStart(2, "0");
  return `${hd}:${md}:${sd}`;
}
