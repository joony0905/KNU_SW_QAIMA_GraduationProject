import { useState } from "react";

type Row = {
  id: number;
  name: string;
};

type Props = {
  title?: string;
  initialRows?: Row[];
};

export default function CrudTable({ title = "목록", initialRows = [] }: Props) {
  const [rows, setRows] = useState<Row[]>(initialRows);
  const [name, setName] = useState("");

  const handleAdd = () => {
    if (!name.trim()) return;
    const newRow: Row = {
      id: Date.now(),
      name,
    };
    setRows((prev) => [...prev, newRow]);
    setName("");
  };

  const handleDelete = (id: number) => {
    setRows((prev) => prev.filter((r) => r.id !== id));
  };

  return (
    <div
      style={{
        background: "white",
        borderRadius: "8px",
        padding: "12px",
        boxShadow: "0 1px 3px rgba(0,0,0,0.08)",
      }}
    >
      <h2 style={{ fontWeight: 600, marginBottom: "12px" }}>{title}</h2>

      <div style={{ display: "flex", gap: "8px", marginBottom: "12px" }}>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="이름 입력"
          style={{ flex: 1, padding: "6px 8px" }}
        />
        <button
          onClick={handleAdd}
          style={{
            background: "#0f172a",
            color: "white",
            border: "none",
            padding: "6px 12px",
            borderRadius: "6px",
            cursor: "pointer",
          }}
        >
          추가
        </button>
      </div>

      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr style={{ textAlign: "left", borderBottom: "1px solid #e2e8f0" }}>
            <th style={{ padding: "6px" }}>ID</th>
            <th style={{ padding: "6px" }}>Name</th>
            <th style={{ padding: "6px" }}>Action</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} style={{ borderBottom: "1px solid #e2e8f0" }}>
              <td style={{ padding: "6px" }}>{r.id}</td>
              <td style={{ padding: "6px" }}>{r.name}</td>
              <td style={{ padding: "6px" }}>
                <button
                  onClick={() => handleDelete(r.id)}
                  style={{
                    background: "#ef4444",
                    color: "white",
                    border: "none",
                    padding: "4px 8px",
                    borderRadius: "4px",
                    cursor: "pointer",
                  }}
                >
                  삭제
                </button>
              </td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={3} style={{ padding: "6px", color: "#94a3b8" }}>
                데이터가 없습니다.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
