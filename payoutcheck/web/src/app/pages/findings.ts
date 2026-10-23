import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api, errorMessage } from '../core/api';
import { Finding, FindingStatus, FindingTypeInfo, Page } from '../core/models';
import { Session } from '../core/session';
import { Toasts } from '../core/toast';
import { PlatformBadge, SeverityBadge, StatusBadge } from '../shared/badges';
import { FORMAT_PIPES, STATUS_LABEL, inr } from '../shared/format';
import { Icon } from '../shared/icon';
import { FindingEditor } from './finding-editor';

@Component({
  selector: 'app-findings',
  imports: [FormsModule, RouterLink, SeverityBadge, StatusBadge, PlatformBadge, Icon, FindingEditor, ...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './findings.html',
})
export class FindingsPage {
  private readonly api = inject(Api);
  private readonly toasts = inject(Toasts);
  protected readonly session = inject(Session);

  /** From the query string, e.g. a dashboard link. */
  readonly type = input<string>();

  protected readonly statuses: (FindingStatus | '')[] = ['OPEN', 'DISPUTED', 'RECOVERED', 'REJECTED', 'IGNORED', ''];
  protected readonly statusLabel = { ...STATUS_LABEL, '': 'All' } as Record<string, string>;
  protected readonly status = signal<FindingStatus | ''>('OPEN');
  protected readonly typeFilter = signal('');
  protected readonly platform = signal('');
  protected readonly severity = signal('');
  protected readonly query = signal('');
  protected readonly page = signal(0);
  protected readonly size = 50;

  protected readonly types = signal<FindingTypeInfo[]>([]);
  protected readonly result = signal<Page<Finding> | null>(null);
  protected readonly loading = signal(false);
  protected readonly selected = signal<Set<number>>(new Set());
  protected readonly editing = signal<Finding | null>(null);
  protected readonly bulkTicket = signal('');

  protected readonly filter = computed(() => ({
    outletId: this.session.outletId(),
    status: this.status(),
    type: this.typeFilter(),
    platform: this.platform(),
    severity: this.severity(),
    q: this.query(),
  }));
  protected readonly exportUrl = computed(() => this.api.findingsExportUrl(this.filter()));
  protected readonly pageTotal = computed(() => (this.result()?.items ?? []).reduce((sum, f) => sum + f.amountAtRisk, 0));
  protected readonly allSelected = computed(() => {
    const items = this.result()?.items ?? [];
    return items.length > 0 && items.every((f) => this.selected().has(f.id));
  });
  protected readonly pages = computed(() => Math.max(1, Math.ceil((this.result()?.total ?? 0) / this.size)));

  constructor() {
    this.api.findingTypes().subscribe((t) => this.types.set(t));
    effect(() => {
      const fromLink = this.type();
      if (fromLink) {
        this.typeFilter.set(fromLink);
        this.status.set('');
      }
    });
    effect(() => {
      const filter = this.filter();
      const page = this.page();
      this.load(filter, page);
    });
  }

  protected setFilter(apply: () => void): void {
    apply();
    this.page.set(0);
  }

  protected typeLabel(code: string): string {
    return this.types().find((t) => t.code === code)?.label ?? code;
  }

  protected toggle(id: number, on: boolean): void {
    this.selected.update((s) => {
      const next = new Set(s);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  protected toggleAll(on: boolean): void {
    this.selected.set(on ? new Set((this.result()?.items ?? []).map((f) => f.id)) : new Set());
  }

  protected async bulk(status: FindingStatus): Promise<void> {
    const ids = [...this.selected()];
    try {
      const { updated } = await firstValueFrom(this.api.bulkUpdateFindings(ids, status, this.bulkTicket()));
      this.toasts.ok(`${updated} finding${updated === 1 ? '' : 's'} marked ${STATUS_LABEL[status].toLowerCase()}`);
      this.selected.set(new Set());
      this.bulkTicket.set('');
      this.reload();
    } catch (e) {
      this.toasts.error(errorMessage(e));
    }
  }

  protected saved(): void {
    this.editing.set(null);
    this.reload();
  }

  protected reload(): void {
    this.load(this.filter(), this.page());
  }

  protected money(value: number): string {
    return inr(value);
  }

  private load(filter: Record<string, unknown>, page: number): void {
    this.loading.set(true);
    this.api.findings({ ...(filter as Record<string, string>), page, size: this.size }).subscribe({
      next: (r) => {
        this.result.set(r);
        this.loading.set(false);
      },
      error: (e) => {
        this.toasts.error(errorMessage(e));
        this.loading.set(false);
      },
    });
  }
}
