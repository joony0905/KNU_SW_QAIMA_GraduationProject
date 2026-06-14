import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardList,
  RefreshCw,
  ShieldCheck,
  UserRoundCheck,
} from "lucide-react";
import {
  analyzeCustomer,
  fetchCustomerAlerts,
  fetchHighRiskCustomers,
  fetchRiskSummary,
} from "./api/wm";
import type { AnalysisResponse, CustomerAlert, CustomerSummary, DashboardSummary, RiskGrade } from "./types/wm";

const gradeLabel: Record<RiskGrade, string> = {
  HIGH: "높음",
  MID: "주의",
  LOW: "안정",
};

function formatMoney(value: number) {
  if (Math.abs(value) >= 100000000) return `${(value / 100000000).toFixed(1)}억`;
  if (Math.abs(value) >= 10000) return `${Math.round(value / 10000).toLocaleString()}만`;
  return value.toLocaleString();
}

function gradeClass(grade: string) {
  return `grade grade-${grade.toLowerCase()}`;
}

export default function App() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [alerts, setAlerts] = useState<CustomerAlert[]>([]);
  const [selectedCustomerId, setSelectedCustomerId] = useState("C001");
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null);
  const [includeDebug, setIncludeDebug] = useState(true);
  const [loading, setLoading] = useState(true);
  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedCustomer = useMemo(
    () => customers.find((customer) => customer.customerId === selectedCustomerId) ?? customers[0],
    [customers, selectedCustomerId],
  );

  async function loadDashboard() {
    setLoading(true);
    setError(null);
    try {
      const [summaryData, highRiskData, alertData] = await Promise.all([
        fetchRiskSummary(),
        fetchHighRiskCustomers(),
        fetchCustomerAlerts(),
      ]);
      setSummary(summaryData);
      setCustomers(highRiskData.customers);
      setAlerts(alertData.alerts);
      const nextCustomerId = highRiskData.customers[0]?.customerId ?? alertData.alerts[0]?.customerId ?? "C001";
      setSelectedCustomerId(nextCustomerId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "대시보드 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function runAnalysis(customerId = selectedCustomerId) {
    if (!customerId) return;
    setAnalysisLoading(true);
    setError(null);
    try {
      const data = await analyzeCustomer(customerId, includeDebug);
      setAnalysis(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "분석을 실행하지 못했습니다.");
    } finally {
      setAnalysisLoading(false);
    }
  }

  useEffect(() => {
    loadDashboard();
  }, []);

  useEffect(() => {
    if (selectedCustomerId) {
      runAnalysis(selectedCustomerId);
    }
  }, [selectedCustomerId, includeDebug]);

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">SAFE-WM PB Dashboard</p>
          <h1>고령 고객 자산 안정성 점검</h1>
        </div>
        <button className="icon-button" type="button" onClick={loadDashboard} aria-label="대시보드 새로고침">
          <RefreshCw size={24} />
        </button>
      </header>

      {error && <div className="error-banner">{error}</div>}

      <section className="summary-grid" aria-label="위험 요약">
        <Metric icon={<UserRoundCheck />} label="관리 고객" value={summary?.totalCustomers ?? 0} />
        <Metric icon={<AlertTriangle />} label="고위험 고객" value={summary?.riskGradeCounts.HIGH ?? 0} tone="danger" />
        <Metric icon={<ClipboardList />} label="긴급 상담" value={summary?.priorityCounts.URGENT ?? 0} tone="warning" />
        <Metric icon={<ShieldCheck />} label="평균 안정성" value={summary?.averageAssetStabilityScore ?? 0} suffix="점" />
      </section>

      <div className="workspace">
        <section className="queue-panel" aria-label="고위험 고객 상담 큐">
          <div className="section-heading">
            <div>
              <h2>상담 우선 고객</h2>
              <p>위험 등급과 상담 우선순위가 높은 고객부터 표시합니다.</p>
            </div>
          </div>
          {loading ? (
            <div className="empty-state">고객 데이터를 불러오는 중입니다.</div>
          ) : (
              <div className="customer-list">
              {customers.map((customer) => (
                <button
                  key={customer.customerId}
                  className={`customer-row ${customer.customerId === selectedCustomerId ? "selected" : ""}`}
                  type="button"
                  onClick={() => setSelectedCustomerId(customer.customerId)}
                >
                  <span className={gradeClass(customer.lastAnalysis.riskGrade)}>
                    {gradeLabel[customer.lastAnalysis.riskGrade]}
                  </span>
                  <span className="customer-main">
                    <strong>{customer.name}</strong>
                    <span>
                      {customer.age}세 · {customer.region}
                    </span>
                    <span>총자산 {formatMoney(customer.summary.totalAssets)}원</span>
                  </span>
                  <span className="score">{customer.lastAnalysis.assetStabilityScore}점</span>
                </button>
              ))}
            </div>
          )}
        </section>

        <section className="detail-panel" aria-label="고객 분석 상세">
          <div className="detail-header">
            <div>
              <h2>{selectedCustomer?.name ?? "고객"} 상세 분석</h2>
              <p>
                {selectedCustomer?.age ?? "-"}세 · {selectedCustomer?.region ?? "-"} ·{" "}
                {selectedCustomer?.retirementStatus ?? "-"}
              </p>
            </div>
            <label className="toggle">
              <input
                type="checkbox"
                checked={includeDebug}
                onChange={(event) => setIncludeDebug(event.target.checked)}
              />
              Debug 표시
            </label>
          </div>

          {analysisLoading || !analysis ? (
            <div className="empty-state large">분석 결과를 준비하는 중입니다.</div>
          ) : (
            <>
              <div className="analysis-hero">
                <div>
                  <span className={gradeClass(analysis.riskGrade)}>{gradeLabel[analysis.riskGrade]}</span>
                  <h3>{analysis.assetStabilityScore}점</h3>
                  <p>{analysis.riskAlert.message}</p>
                </div>
                <button className="primary-button" type="button" onClick={() => runAnalysis()}>
                  <RefreshCw size={22} />
                  재분석
                </button>
              </div>

              <div className="two-column">
                <InfoSection title="고객 안내 문구" className="senior-copy">
                  <p>{analysis.customerExplanation.text}</p>
                </InfoSection>
                <InfoSection title="PB 상담 요약">
                  <p>{analysis.pbExplanation.text}</p>
                </InfoSection>
              </div>

              <InfoSection title="주요 위험 요인">
                <div className="risk-factor-list">
                  {analysis.riskFactors.map((factor) => (
                    <article key={factor.code} className="risk-factor">
                      <span className={gradeClass(factor.severity)}>{factor.severity}</span>
                      <div>
                        <h4>{factor.title}</h4>
                        <p>{factor.description}</p>
                      </div>
                    </article>
                  ))}
                </div>
              </InfoSection>

              <InfoSection title="상담 액션">
                <div className="action-list">
                  {analysis.pbActions.map((action) => (
                    <div key={`${action.title}-${action.description}`} className="action-row">
                      <CheckCircle2 size={22} />
                      <div>
                        <strong>{action.title}</strong>
                        <p>{action.description}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </InfoSection>

              <div className="two-column">
                <InfoSection title="Compliance">
                  <div className="status-line">
                    <span className="grade grade-low">{analysis.compliance.status}</span>
                    <span>{analysis.compliance.regenerated ? "금지 표현 재생성 완료" : "금지 표현 없음"}</span>
                  </div>
                </InfoSection>
                <InfoSection title="Agent Trace">
                  <ol className="trace-list">
                    {analysis.agentTrace.map((trace, index) => (
                      <li key={`${trace.agent}-${index}`}>
                        <strong>{trace.agent}</strong>
                        <span>{trace.status}</span>
                      </li>
                    ))}
                  </ol>
                </InfoSection>
              </div>

              {includeDebug && analysis.debug && (
                <InfoSection title="Debug Steps">
                  <pre className="debug-box">{JSON.stringify(analysis.debug.steps, null, 2)}</pre>
                </InfoSection>
              )}
            </>
          )}
        </section>
      </div>

      <section className="alerts-panel" aria-label="고객 알림">
        <div className="section-heading">
          <h2>고객 알림</h2>
        </div>
        <div className="alert-list">
          {alerts.map((alert) => (
            <button
              key={alert.customerId}
              className="alert-row"
              type="button"
              onClick={() => setSelectedCustomerId(alert.customerId)}
            >
              <span className={gradeClass(alert.riskGrade)}>{gradeLabel[alert.riskGrade]}</span>
              <strong>{alert.name}</strong>
              <span>{alert.message}</span>
            </button>
          ))}
        </div>
      </section>
    </main>
  );
}

function Metric({
  icon,
  label,
  value,
  suffix = "",
  tone = "neutral",
}: {
  icon: ReactNode;
  label: string;
  value: number;
  suffix?: string;
  tone?: "neutral" | "danger" | "warning";
}) {
  return (
    <article className={`metric metric-${tone}`}>
      <div className="metric-icon">{icon}</div>
      <div>
        <span>{label}</span>
        <strong>
          {value}
          {suffix}
        </strong>
      </div>
    </article>
  );
}

function InfoSection({
  title,
  children,
  className = "",
}: {
  title: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`info-section ${className}`}>
      <h3>{title}</h3>
      {children}
    </section>
  );
}
