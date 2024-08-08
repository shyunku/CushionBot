const TimeUnit = {
  SECOND: 1000,
  MINUTE: 60 * 1000,
  HOUR: 60 * 60 * 1000,
  DAY: 24 * 60 * 60 * 1000,
};

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
