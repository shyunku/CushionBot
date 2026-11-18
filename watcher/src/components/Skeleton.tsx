export default function Skeleton() {
  return (
    <>
      <div className="head">
        <div className="sk" style={{ width: 220, height: 30 }} />
        <div className="sk" style={{ width: 160, height: 16 }} />
      </div>
      <div className="summary">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="sk" style={{ height: 62 }} />
        ))}
      </div>
      <div className="ctrl">
        <div className="sk" style={{ width: 300, height: 30 }} />
      </div>
      <div className="tl" style={{ padding: "44px 16px" }}>
        {Array.from({ length: 10 }, (_, i) => (
          <div key={i} style={{ display: "flex", gap: 16, marginBottom: 16 }}>
            <div className="sk" style={{ width: 150, height: 22 }} />
            <div className="sk" style={{ marginLeft: (i * 97) % 400, width: 120 + ((i * 53) % 260), height: 22 }} />
          </div>
        ))}
      </div>
    </>
  );
}
