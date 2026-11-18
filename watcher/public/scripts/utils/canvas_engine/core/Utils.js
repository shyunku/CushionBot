export function getPixelRate() {
  var ctx = document.createElement("canvas").getContext("2d"),
    dpr = window.devicePixelRatio || 1,
    bsr =
      ctx.webkitBackingStorePixelRatio ||
      ctx.mozBackingStorePixelRatio ||
      ctx.msBackingStorePixelRatio ||
      ctx.oBackingStorePixelRatio ||
      ctx.backingStorePixelRatio ||
      1;
  return dpr / bsr;
}

export async function getFrameRate() {
  new Promise((resolve) => requestAnimationFrame((t1) => requestAnimationFrame((t2) => resolve(1000 / (t2 - t1)))));
}

export function fastInterval(func, interval, ...args) {
  const newFunc = () => func(...args);
  newFunc();
  setInterval(newFunc, interval);
}

export function currentMicroProcessTime() {
  return performance.now();
}

export function getEaseOutFactor(ASR, renderPeriod) {
  let retVal = 1 - Math.pow(1 - ASR, renderPeriod / 1000);
  return retVal > 1 ? 1 : retVal;
}
