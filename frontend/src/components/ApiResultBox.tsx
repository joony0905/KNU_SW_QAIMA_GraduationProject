type Props = {
  loading: boolean;
  error: string | null | undefined;
  data: any;
};

export default function ApiResultBox({ loading, error, data }: Props) {
  if (loading) return <div>불러오는 중...</div>;
  if (error) return <div style={{ color: "red" }}>에러: {error}</div>;
  if (!data) return <div>아직 응답이 없습니다.</div>;

  return (
    <pre
      style={{
        background: "#111827",
        color: "#e5e7eb",
        padding: "12px",
        borderRadius: "8px",
        fontSize: "0.8rem",
        overflowX: "auto",
      }}
    >
      {JSON.stringify(data, null, 2)}
    </pre>
  );
}
