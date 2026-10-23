import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Api, errorMessage } from '../core/api';
import { StatementSummary } from '../core/models';
import { Session } from '../core/session';
import { PlatformBadge } from '../shared/badges';
import { FORMAT_PIPES } from '../shared/format';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-statements',
  imports: [RouterLink, PlatformBadge, Icon, ...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-head">
      <div>
        <h1>Payout statements</h1>
        <p>Each weekly settlement you have uploaded, and what the checks found in it.</p>
      </div>
      <a class="btn primary" routerLink="/upload"><app-icon name="upload" />Upload a statement</a>
    </div>
    @if (error()) { <div class="notice error">{{ error() }}</div> }
    <section class="card">
      <div class="table-wrap">
        <table class="data">
          <thead>
            <tr>
              <th>Period</th><th>Platform</th>@if (!session.outletId()) { <th>Outlet</th> }
              <th class="num">Sales</th><th class="num">Deducted</th><th class="num">Net payout</th>
              <th class="num">Findings</th><th class="num">Open at risk</th><th class="num">Recovered</th>
            </tr>
          </thead>
          <tbody>
            @for (s of items(); track s.statement.id) {
              <tr class="clickable" (click)="open(s.statement.id)">
                <td class="nowrap"><a [routerLink]="['/statements', s.statement.id]">{{ s.statement.periodStart | period: s.statement.periodEnd }}</a>
                  <div class="muted small">{{ s.statement.lineCount }} rows · {{ s.statement.fileName }}</div></td>
                <td><app-platform [value]="s.statement.platform" /></td>
                @if (!session.outletId()) { <td>{{ session.outletName(s.statement.outletId) }}</td> }
                <td class="num">{{ s.statement.grossSales | inr: true }}</td>
                <td class="num">{{ s.statement.totalDeductions | inr: true }}</td>
                <td class="num">{{ s.statement.netPayout | inr: true }}</td>
                <td class="num">{{ s.findings }}</td>
                <td class="num" [class.bad]="s.openAtRisk > 0">{{ s.openAtRisk | inr: true }}</td>
                <td class="num" [class.good]="s.recovered > 0">{{ s.recovered | inr: true }}</td>
              </tr>
            } @empty {
              <tr><td colspan="9"><div class="empty"><app-icon name="statements" [size]="28" /><h3>No statements yet</h3>
                <p>Download the weekly payout annexure from the Swiggy or Zomato partner app and upload it.</p></div></td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
})
export class StatementsPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly session = inject(Session);
  protected readonly items = signal<StatementSummary[]>([]);
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      this.api.statements({ outletId: this.session.outletId() }).subscribe({
        next: (s) => this.items.set(s),
        error: (e) => this.error.set(errorMessage(e)),
      });
    });
  }

  protected open(id: number): void {
    this.router.navigate(['/statements', id]);
  }
}
