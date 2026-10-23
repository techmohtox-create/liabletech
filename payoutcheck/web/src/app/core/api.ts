import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuditEvent, CheckSummary, Dashboard, DisputeLetter, Finding, FindingStatus, FindingTypeInfo, ImportBatch,
  ImportResult, Me, OrderRecord, OrganizationView, Outlet, Page, PlanCode, PlanView, Platform, PlatformTerms, Role,
  StatementDetail, StatementSummary, UserView,
} from './models';

type Params = Record<string, string | number | boolean | null | undefined>;

function params(values: Params): HttpParams {
  let result = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== null && value !== undefined && value !== '') {
      result = result.set(key, String(value));
    }
  }
  return result;
}

/** Typed access to the PayoutCheck API. Every call is same-origin, so the session cookie and XSRF token travel with it. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  csrf(): Observable<void> {
    return this.http.get<void>('/api/auth/csrf');
  }
  me(): Observable<Me> {
    return this.http.get<Me>('/api/auth/me');
  }
  login(email: string, password: string): Observable<Me> {
    return this.http.post<Me>('/api/auth/login', { email, password });
  }
  signup(body: { businessName: string; outletName: string; fullName: string; email: string; password: string }): Observable<Me> {
    return this.http.post<Me>('/api/auth/signup', body);
  }
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {});
  }

  plans(): Observable<PlanView[]> {
    return this.http.get<PlanView[]>('/api/plans');
  }
  organization(): Observable<OrganizationView> {
    return this.http.get<OrganizationView>('/api/organization');
  }
  renameOrganization(name: string): Observable<OrganizationView> {
    return this.http.put<OrganizationView>('/api/organization', { name });
  }
  changePlan(plan: PlanCode): Observable<OrganizationView> {
    return this.http.put<OrganizationView>('/api/organization/plan', { plan });
  }
  users(): Observable<UserView[]> {
    return this.http.get<UserView[]>('/api/users');
  }
  addUser(body: { email: string; fullName: string; password: string; role: Role }): Observable<UserView> {
    return this.http.post<UserView>('/api/users', body);
  }
  updateUser(id: number, role: Role, active: boolean): Observable<UserView> {
    return this.http.put<UserView>(`/api/users/${id}`, { role, active });
  }
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/users/me/password', { currentPassword, newPassword });
  }

  outlets(): Observable<Outlet[]> {
    return this.http.get<Outlet[]>('/api/outlets');
  }
  createOutlet(body: Partial<Outlet>): Observable<Outlet> {
    return this.http.post<Outlet>('/api/outlets', body);
  }
  updateOutlet(id: number, body: Partial<Outlet>): Observable<Outlet> {
    return this.http.put<Outlet>(`/api/outlets/${id}`, body);
  }
  terms(outletId: number): Observable<PlatformTerms[]> {
    return this.http.get<PlatformTerms[]>(`/api/outlets/${outletId}/terms`);
  }
  saveTerms(outletId: number, platform: Platform, body: Omit<PlatformTerms, 'platform' | 'updatedAt'>): Observable<PlatformTerms> {
    return this.http.put<PlatformTerms>(`/api/outlets/${outletId}/terms/${platform}`, body);
  }

  dashboard(outletId?: number | null): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/dashboard', { params: params({ outletId }) });
  }

  importOrders(outletId: number, platform: Platform | '', file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>('/api/imports/orders', form, { params: params({ outletId, platform }) });
  }
  importStatement(outletId: number, details: Params, file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>('/api/imports/statements', form, { params: params({ outletId, ...details }) });
  }
  imports(): Observable<ImportBatch[]> {
    return this.http.get<ImportBatch[]>('/api/imports');
  }

  orders(filter: Params): Observable<Page<OrderRecord>> {
    return this.http.get<Page<OrderRecord>>('/api/orders', { params: params(filter) });
  }

  statements(filter: Params = {}): Observable<StatementSummary[]> {
    return this.http.get<StatementSummary[]>('/api/statements', { params: params(filter) });
  }
  statement(id: number): Observable<StatementDetail> {
    return this.http.get<StatementDetail>(`/api/statements/${id}`);
  }
  recheck(id: number): Observable<CheckSummary> {
    return this.http.post<CheckSummary>(`/api/statements/${id}/check`, {});
  }
  deleteStatement(id: number): Observable<void> {
    return this.http.delete<void>(`/api/statements/${id}`);
  }

  findings(filter: Params): Observable<Page<Finding>> {
    return this.http.get<Page<Finding>>('/api/findings', { params: params(filter) });
  }
  findingTypes(): Observable<FindingTypeInfo[]> {
    return this.http.get<FindingTypeInfo[]>('/api/findings/types');
  }
  updateFinding(id: number, body: { status: FindingStatus; ticketRef?: string | null; recoveredAmount?: number | null; notes?: string | null }): Observable<Finding> {
    return this.http.put<Finding>(`/api/findings/${id}`, body);
  }
  bulkUpdateFindings(ids: number[], status: FindingStatus, ticketRef?: string): Observable<{ updated: number }> {
    return this.http.post<{ updated: number }>('/api/findings/bulk', { ids, status, ticketRef: ticketRef || null });
  }
  disputeLetter(statementId: number, findingIds?: number[]): Observable<DisputeLetter> {
    return this.http.post<DisputeLetter>(`/api/findings/letter/${statementId}`, { findingIds: findingIds ?? [] });
  }
  findingsExportUrl(filter: Params): string {
    const query = params(filter).toString();
    return '/api/findings/export' + (query ? '?' + query : '');
  }

  audit(page: number): Observable<Page<AuditEvent>> {
    return this.http.get<Page<AuditEvent>>('/api/audit', { params: params({ page, size: 50 }) });
  }
}

/** The server's ProblemDetail message, or a plain fallback. */
export function errorMessage(error: unknown): string {
  const e = error as { status?: number; error?: { detail?: string; errors?: Record<string, string> } };
  if (e?.error?.errors) {
    const [field, message] = Object.entries(e.error.errors)[0];
    return `${field}: ${message}`;
  }
  if (e?.error?.detail) {
    return e.error.detail;
  }
  if (e?.status === 0) {
    return 'Cannot reach the server. Is it running?';
  }
  return 'Something went wrong. Please try again.';
}
