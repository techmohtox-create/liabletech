import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api, errorMessage } from '../core/api';
import { OrderRecord, Page } from '../core/models';
import { Session } from '../core/session';
import { PlatformBadge } from '../shared/badges';
import { FORMAT_PIPES } from '../shared/format';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-orders',
  imports: [FormsModule, RouterLink, PlatformBadge, Icon, ...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-head">
      <div>
        <h1>Orders</h1>
        <p>Your own record of orders, from your POS or the platforms' order history. Statements are checked against these.</p>
      </div>
      <a class="btn primary" routerLink="/upload"><app-icon name="upload" />Upload orders</a>
    </div>
    @if (error()) { <div class="notice error">{{ error() }}</div> }
    <section class="card">
      <div class="filters">
        <input type="search" placeholder="Order ID" [ngModel]="q()" (ngModelChange)="set(q, $event)" aria-label="Search orders" />
        <select [ngModel]="platform()" (ngModelChange)="set(platform, $event)" aria-label="Platform">
          <option value="">Both platforms</option><option value="SWIGGY">Swiggy</option><option value="ZOMATO">Zomato</option>
        </select>
        <select [ngModel]="status()" (ngModelChange)="set(status, $event)" aria-label="Status">
          <option value="">Any status</option><option value="DELIVERED">Delivered</option><option value="CANCELLED">Cancelled</option>
          <option value="REJECTED">Rejected</option><option value="OTHER">Other</option>
        </select>
        <label class="check">From <input type="date" [ngModel]="from()" (ngModelChange)="set(from, $event)" /></label>
        <label class="check">To <input type="date" [ngModel]="to()" (ngModelChange)="set(to, $event)" /></label>
      </div>
      <div class="table-wrap">
        <table class="data">
          <thead><tr><th>Order</th><th>Placed</th><th>Platform</th><th>Status</th><th class="num">Items</th><th class="num">Packaging</th>
            <th class="num">Restaurant discount</th></tr></thead>
          <tbody>
            @for (o of result()?.items ?? []; track o.id) {
              <tr>
                <td class="mono">{{ o.orderRef }}</td>
                <td class="nowrap">{{ o.orderedAt | stamp }}</td>
                <td><app-platform [value]="o.platform" /></td>
                <td>{{ o.status.toLowerCase() }}</td>
                <td class="num">{{ o.itemTotal | inr }}</td>
                <td class="num">{{ o.packaging | inr }}</td>
                <td class="num">{{ o.restaurantDiscount | inr }}</td>
              </tr>
            } @empty {
              <tr><td colspan="7"><div class="empty"><h3>No orders found</h3><p>Upload an order export to start matching.</p></div></td></tr>
            }
          </tbody>
        </table>
      </div>
      <div class="card-foot">
        <span class="muted">{{ result()?.total ?? 0 }} orders</span>
        <div class="pager">
          <button class="btn small" type="button" [disabled]="page() === 0" (click)="page.set(page() - 1)">Previous</button>
          <span>Page {{ page() + 1 }} of {{ pages() }}</span>
          <button class="btn small" type="button" [disabled]="page() + 1 >= pages()" (click)="page.set(page() + 1)">Next</button>
        </div>
      </div>
    </section>
  `,
})
export class OrdersPage {
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  protected readonly q = signal('');
  protected readonly platform = signal('');
  protected readonly status = signal('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly page = signal(0);
  protected readonly result = signal<Page<OrderRecord> | null>(null);
  protected readonly error = signal('');
  protected readonly pages = computed(() => Math.max(1, Math.ceil((this.result()?.total ?? 0) / 50)));

  constructor() {
    effect(() => {
      const filter = {
        outletId: this.session.outletId(),
        q: this.q(),
        platform: this.platform(),
        status: this.status(),
        from: this.from(),
        to: this.to(),
        page: this.page(),
        size: 50,
      };
      this.api.orders(filter).subscribe({ next: (r) => this.result.set(r), error: (e) => this.error.set(errorMessage(e)) });
    });
  }

  protected set(target: ReturnType<typeof signal<string>>, value: string): void {
    target.set(value ?? '');
    this.page.set(0);
  }
}
