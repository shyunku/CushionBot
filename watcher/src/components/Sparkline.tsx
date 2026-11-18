export default function Sparkline({ values, width, height }: { values: number[]; width: number; height: number }) {
  if (values.length < 2) return <svg width={width} height={height} />;
  const max = Math.max(1, ...values);
  const d = values
    .map((v, i) => `${i === 0 ? "M" : "L"}${((i / (values.length - 1)) * width).toFixed(1)},${(height - (v / max) * (height - 4) - 2).toFixed(1)}`)
    .join(" ");
  return (
    <svg width={width} height={height}>
      <path d={`${d} L${width},${height} L0,${height} Z`} fill="var(--accent)" opacity={0.25} />
      <path d={d} fill="none" stroke="var(--accent)" strokeWidth={1.6} />
    </svg>
  );
}
