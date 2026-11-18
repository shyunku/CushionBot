import { useEffect, useState } from "react";

/** Phone layout breakpoint; keep in sync with the media query in styles.css. */
export const MOBILE_QUERY = "(max-width: 700px)";

export function useMobile() {
  const [mobile, setMobile] = useState(() => window.matchMedia(MOBILE_QUERY).matches);
  useEffect(() => {
    const mq = window.matchMedia(MOBILE_QUERY);
    const update = () => setMobile(mq.matches);
    mq.addEventListener("change", update);
    update();
    return () => mq.removeEventListener("change", update);
  }, []);
  return mobile;
}
