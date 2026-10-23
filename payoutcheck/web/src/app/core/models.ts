export type Platform = 'SWIGGY' | 'ZOMATO';
export type Role = 'OWNER' | 'MANAGER' | 'VIEWER';
export type PlanCode = 'STARTER' | 'GROWTH' | 'PRO';
export type FindingStatus = 'OPEN' | 'DISPUTED' | 'RECOVERED' | 'REJECTED' | 'IGNORED';
export type Severity = 'HIGH' | 'MEDIUM' | 'LOW';

export interface Me {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  orgId: number;
  orgName: string;
  plan: PlanCode;
}

export interface PlanView {
  code: PlanCode;
  label: string;
  monthlyPriceInr: number;
  maxOutlets: number;
  maxUsers: number;
  disputeLetters: boolean;
}

export interface OrganizationView {
  id: number;
  name: string;
  plan: PlanView;
  activeOutlets: number;
  activeUsers: number;
  createdAt: string;
}

export interface UserView {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface Outlet {
  id: number;
  name: string;
  city: string | null;
  swiggyId: string | null;
  zomatoId: string | null;
  active: boolean;
}

export interface PlatformTerms {
  platform: Platform;
  commissionPct: number;
  pgFeePct: number;
  gstOnFeesPct: number;
  tcsPct: number;
  tdsPct: number;
  commissionOnPackaging: boolean;
  tolerance: number;
  updatedAt: string | null;
}

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface OrderRecord {
  id: number;
  outletId: number;
  platform: Platform;
  orderRef: string;
  orderedAt: string;
  status: string;
  itemTotal: number;
  packaging: number;
  restaurantDiscount: number;
}

export interface PayoutStatement {
  id: number;
  outletId: number;
  platform: Platform;
  periodStart: string;
  periodEnd: string;
  payoutDate: string | null;
  utr: string | null;
  creditedAmount: number | null;
  fileName: string;
  lineCount: number;
  grossSales: number;
  totalDeductions: number;
  netPayout: number;
  checkedAt: string | null;
  createdAt: string;
}

export interface StatementSummary {
  statement: PayoutStatement;
  findings: number;
  openAtRisk: number;
  recovered: number;
}

export interface StatementLine {
  id: number;
  lineNo: number;
  orderRef: string | null;
  orderDate: string | null;
  orderStatus: string | null;
  itemTotal: number;
  packaging: number;
  restaurantDiscount: number;
  commission: number;
  pgFee: number;
  otherFees: number;
  gstOnFees: number;
  tcs: number;
  tds: number;
  customerRefund: number;
  cancellationDeduction: number;
  penalty: number;
  otherAdjustment: number;
  netPayout: number;
  description: string | null;
}

export interface Finding {
  id: number;
  outletId: number;
  statementId: number;
  lineId: number | null;
  platform: Platform;
  orderRef: string | null;
  type: string;
  severity: Severity;
  expectedAmount: number | null;
  actualAmount: number | null;
  amountAtRisk: number;
  explanation: string;
  status: FindingStatus;
  ticketRef: string | null;
  recoveredAmount: number | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export interface StatementDetail {
  statement: PayoutStatement;
  lines: StatementLine[];
  findings: Finding[];
}

export interface FindingTypeInfo {
  code: string;
  label: string;
  description: string;
}

export interface CheckSummary {
  statementId: number;
  findings: number;
  newFindings: number;
  matchedLines: number;
  unmatchedLines: number;
  ordersInPeriod: number;
}

export interface ImportResult {
  importId: number;
  kind: 'ORDERS' | 'STATEMENT';
  fileName: string;
  rowsRead: number;
  imported: number;
  newRecords: number;
  skipped: number;
  rejected: number;
  errors: { row: number; message: string }[];
  columns: Record<string, string>;
  statementId: number | null;
  check: CheckSummary | null;
  recheckedStatements: number;
}

export interface ImportBatch {
  id: number;
  outletId: number;
  kind: string;
  platform: Platform;
  fileName: string;
  rowsTotal: number;
  rowsImported: number;
  rowsRejected: number;
  errors: string | null;
  createdAt: string;
}

export interface Dashboard {
  totals: {
    grossSales: number;
    totalDeductions: number;
    netPayout: number;
    openAtRisk: number;
    disputedAtRisk: number;
    recovered: number;
    rejected: number;
    statements: number;
    openFindings: number;
    flaggedShareOfSales: number;
  };
  weeks: { periodEnd: string; grossSales: number; deductions: number; flagged: number; recovered: number }[];
  byType: { type: string; label: string; count: number; amount: number }[];
  byPlatform: { platform: Platform; grossSales: number; deductions: number; openAtRisk: number; deductionRate: number }[];
  topOpen: Finding[];
}

export interface DisputeLetter {
  subject: string;
  body: string;
  totalClaimed: number;
  findings: number;
}

export interface AuditEvent {
  id: number;
  actorEmail: string | null;
  action: string;
  entity: string;
  entityId: number | null;
  detail: string | null;
  createdAt: string;
}
