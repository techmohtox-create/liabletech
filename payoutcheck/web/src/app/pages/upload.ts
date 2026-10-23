import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api, errorMessage } from '../core/api';
import { ImportBatch, ImportResult, Platform } from '../core/models';
import { Session } from '../core/session';
import { PlatformBadge } from '../shared/badges';
import { FORMAT_PIPES } from '../shared/format';
import { Icon } from '../shared/icon';

const FIELD_LABELS: Record<string, string> = {
  orderRef: 'Order ID', orderedAt: 'Order date', orderDate: 'Order date', status: 'Status', orderStatus: 'Status',
  platform: 'Platform', itemTotal: 'Item total', packaging: 'Packaging', restaurantDiscount: 'Restaurant discount',
  commission: 'Commission', pgFee: 'PG fee', otherFees: 'Other fees', gstOnFees: 'GST on fees', tcs: 'TCS', tds: 'TDS',
  customerRefund: 'Customer refund', cancellationDeduction: 'Cancellation', penalty: 'Penalty',
  otherAdjustment: 'Adjustment', netPayout: 'Net payout', description: 'Description',
};

@Component({
  selector: 'app-upload',
  imports: [FormsModule, RouterLink, PlatformBadge, Icon, ...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './upload.html',
  styles: `
    .drop {
      display: grid; place-items: center; gap: 6px; padding: 24px; border: 1.5px dashed var(--line-strong); border-radius: var(--radius);
      background: var(--surface-2); text-align: center; cursor: pointer; transition: border-color var(--dur) var(--ease);
    }
    .drop:hover, .drop.over { border-color: var(--signal); background: var(--signal-soft); }
    .drop input { position: absolute; width: 1px; height: 1px; opacity: 0; }
    .mapping { display: flex; flex-wrap: wrap; gap: 6px; }
    .mapping span { font-size: 12px; padding: 2px 8px; border-radius: 6px; background: var(--neutral-soft); }
    ol.steps { margin: 0; padding-left: 18px; display: grid; gap: 6px; color: var(--slate); }
  `,
})
export class UploadPage {
  private readonly api = inject(Api);
  protected readonly session = inject(Session);
  protected readonly welcome = inject(ActivatedRoute).snapshot.queryParamMap.has('welcome');

  protected ordersOutlet = this.session.defaultOutletId();
  protected ordersPlatform: Platform | '' = '';
  protected readonly ordersFile = signal<File | null>(null);

  protected statementOutlet = this.session.defaultOutletId();
  protected statementPlatform: Platform = 'SWIGGY';
  protected periodStart = '';
  protected periodEnd = '';
  protected payoutDate = '';
  protected utr = '';
  protected creditedAmount: number | null = null;
  protected readonly statementFile = signal<File | null>(null);

  protected readonly busy = signal<'orders' | 'statement' | null>(null);
  protected readonly result = signal<ImportResult | null>(null);
  protected readonly error = signal('');
  protected readonly history = signal<ImportBatch[]>([]);
  protected readonly over = signal<string | null>(null);
  protected readonly mapping = computed(() =>
    Object.entries(this.result()?.columns ?? {}).map(([field, header]) => ({ field: FIELD_LABELS[field] ?? field, header })),
  );

  constructor() {
    this.loadHistory();
  }

  protected pick(target: 'orders' | 'statement', files: FileList | null): void {
    const file = files?.[0] ?? null;
    (target === 'orders' ? this.ordersFile : this.statementFile).set(file);
    this.over.set(null);
  }

  protected drop(target: 'orders' | 'statement', event: DragEvent): void {
    event.preventDefault();
    this.pick(target, event.dataTransfer?.files ?? null);
  }

  protected async uploadOrders(): Promise<void> {
    const file = this.ordersFile();
    if (!file || !this.ordersOutlet) return;
    await this.run('orders', () => firstValueFrom(this.api.importOrders(Number(this.ordersOutlet), this.ordersPlatform, file)));
    if (!this.error()) this.ordersFile.set(null);
  }

  protected async uploadStatement(): Promise<void> {
    const file = this.statementFile();
    if (!file || !this.statementOutlet) return;
    await this.run('statement', () =>
      firstValueFrom(
        this.api.importStatement(
          Number(this.statementOutlet),
          {
            platform: this.statementPlatform,
            periodStart: this.periodStart,
            periodEnd: this.periodEnd,
            payoutDate: this.payoutDate,
            utr: this.utr,
            creditedAmount: this.creditedAmount,
          },
          file,
        ),
      ),
    );
    if (!this.error()) {
      this.statementFile.set(null);
      this.periodStart = this.periodEnd = this.payoutDate = this.utr = '';
      this.creditedAmount = null;
    }
  }

  protected outletName(id: number): string {
    return this.session.outletName(id);
  }

  private async run(kind: 'orders' | 'statement', call: () => Promise<ImportResult>): Promise<void> {
    this.busy.set(kind);
    this.error.set('');
    this.result.set(null);
    try {
      this.result.set(await call());
      this.loadHistory();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(null);
    }
  }

  private loadHistory(): void {
    this.api.imports().subscribe((h) => this.history.set(h));
  }
}
