import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, errorMessage } from '../core/api';
import { Dashboard } from '../core/models';
import { Session } from '../core/session';
import { PlatformBadge, SeverityBadge } from '../shared/badges';
import { BarChart } from '../shared/bar-chart';
import { FORMAT_PIPES, day } from '../shared/format';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, BarChart, SeverityBadge, PlatformBadge, Icon, ...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html',
  styles: `
    .type-bars { list-style: none; margin: 0; padding: 0; display: grid; gap: 12px; }
    .type-bars a { display: grid; grid-template-columns: 1fr auto; gap: 4px 12px; color: inherit; text-decoration: none; }
    .type-bars a:hover .type-label { color: var(--signal); }
    .type-label { font-size: 13.5px; font-weight: 500; }
    .track { grid-column: 1 / -1; height: 8px; border-radius: 4px; background: var(--neutral-soft); overflow: hidden; }
    .fill { display: block; height: 100%; border-radius: 0 4px 4px 0; background: var(--chart-1); }
  `,
})
export class DashboardPage {
  private readonly api = inject(Api);
  protected readonly session = inject(Session);
  protected readonly data = signal<Dashboard | null>(null);
  protected readonly error = signal('');

  protected readonly weekBars = computed(() =>
    (this.data()?.weeks ?? []).map((w) => ({
      label: day(w.periodEnd),
      value: w.flagged,
      detail: `Week ending ${day(w.periodEnd)} · ${Math.round(w.grossSales).toLocaleString('en-IN')} sales`,
    })),
  );

  protected readonly maxType = computed(() => Math.max(1, ...(this.data()?.byType ?? []).map((t) => t.amount)));

  constructor() {
    effect(() => {
      const outletId = this.session.outletId();
      this.api.dashboard(outletId).subscribe({
        next: (d) => this.data.set(d),
        error: (e) => this.error.set(errorMessage(e)),
      });
    });
  }
}
