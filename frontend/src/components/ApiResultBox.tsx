import { useTranslation } from "react-i18next";

type Props = {
  loading: boolean;
  error: string | null | undefined;
  data: any;
};

export default function ApiResultBox({ loading, error, data }: Props) {
  const { i18n } = useTranslation();
  const english = i18n.language.toLowerCase().startsWith("en");

  if (loading) return <div>{english ? "Loading..." : "불러오는 중..."}</div>;
  if (error) return <div style={{ color: "red" }}>{english ? "Error" : "에러"}: {error}</div>;
  if (!data) return <div>{english ? "No response yet." : "아직 응답이 없습니다."}</div>;

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
