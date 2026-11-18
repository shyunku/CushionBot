export default function GuildIcon({ name, url, size, round }: { name: string; url: string | null; size: number; round?: boolean }) {
  const style = { width: size, height: size, fontSize: size * 0.42 };
  if (url) return <img className={`gicon ${round ? "round" : ""}`} src={url} alt="" style={style} />;
  return (
    <span className={`gicon fallback ${round ? "round" : ""}`} style={style}>
      {name.slice(0, 1)}
    </span>
  );
}
