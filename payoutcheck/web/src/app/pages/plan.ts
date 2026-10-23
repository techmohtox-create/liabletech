import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { Api, errorMessage } from '../core/api';
import { OrganizationView, PlanCode, PlanView } from '../core/models';
import { Session } from '../core/session';
import { Toasts } from '../core/toast';
import { FORMAT_PIPES } from '../shared/format';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-plan',
  imports: [FormsModule, Icon, ...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .plans { display: grid; gap: 16px; grid-template-columns: repeat(3, minmax(0, 1fr)); }
    @media (max-width: 900px) { .plans { grid-template-columns: minmax(0, 1fr); } }
    .plan { padding: 22px; display: grid; gap: 14px; align-content: start; }
    .plan.current { border-color: var(--signal); box-shadow: 0 0 0 3px var(--signal-soft); }
    .price { font-size: 30px; font-weight: 700; letter-spacing: -0.02em; }
    .price span { font-size: 14px; font-weight: 500; color: var(--muted); }
    .plan ul { margin: 0; padding: 0; list-style: none; display: grid; gap: 8px; }
    .plan li { display: flex; gap: 8px; align-items: flex-start; }
    .plan li app-icon { color: var(--good); margin-top: 1px; }
  `,
  template: `
    <div class="page-head">
      <div>
        <h1>Plan</h1>
        <p>One recovered deduction a month usually covers the plan. Billing is handled outside the app; the owner records the plan here.</p>
      </div>
    </div>
    @if (org(); as o) {
      <div class="plans">
        @for (p of plans(); track p.code) {
          <section class="card plan" [class.current]="p.code === o.plan.code">
            <div><h2>{{ p.label }}</h2></div>
            <div class="price">₹{{ p.monthlyPriceInr }}<span> / month + GST</span></div>
            <ul>
              <li><app-icon name="check" />{{ p.maxOutlets === 1 ? '1 outlet' : 'Up to ' + p.maxOutlets + ' outlets' }}, Swiggy and Zomato</li>
              <li><app-icon name="check" />{{ p.maxUsers === 1 ? '1 user' : 'Up to ' + p.maxUsers + ' users' }}</li>
              <li><app-icon name="check" />Every check on every statement</li>
              <li><app-icon name="check" />Dispute tracking and CSV export</li>
              @if (p.disputeLetters) { <li><app-icon name="check" />Ready-to-send dispute letters</li> }
            </ul>
            @if (p.code === o.plan.code) {
              <span class="badge st-open">Current plan</span>
            } @else if (session.isOwner()) {
              <button class="btn" [class.primary]="p.monthlyPriceInr > o.plan.monthlyPriceInr" type="button" (click)="choose(p.code)">
                {{ p.monthlyPriceInr > o.plan.monthlyPriceInr ? 'Upgrade' : 'Switch' }} to {{ p.label }}
              </button>
            }
          </section>
        }
      </div>

      <section class="card" style="margin-top: 20px; max-width: 640px">
        <div class="card-head"><h2>Business</h2><span class="muted">{{ o.activeOutlets }}/{{ o.plan.maxOutlets }} outlets · {{ o.activeUsers }}/{{ o.plan.maxUsers }} users</span></div>
        <form class="card-body stack" (ngSubmit)="rename()">
          <label class="field"><span>Business name</span><input type="text" name="name" [(ngModel)]="name" required maxlength="160" [disabled]="!session.isOwner()" /></label>
          <p class="muted small">Customer since {{ o.createdAt | longDate }}</p>
          @if (session.isOwner()) { <div class="actions" style="justify-content: flex-end"><button class="btn" type="submit">Save</button></div> }
        </form>
      </section>
    }
  `,
})
export class PlanPage {
  private readonly api = inject(Api);
  private readonly toasts = inject(Toasts);
  protected readonly session = inject(Session);
  protected readonly plans = signal<PlanView[]>([]);
  protected readonly org = signal<OrganizationView | null>(null);
  protected name = '';

  constructor() {
    this.api.plans().subscribe((p) => this.plans.set(p));
    this.api.organization().subscribe((o) => this.setOrg(o));
  }

  protected async choose(plan: PlanCode): Promise<void> {
    try {
      this.setOrg(await firstValueFrom(this.api.changePlan(plan)));
      this.toasts.ok('Plan changed');
    } catch (e) {
      this.toasts.error(errorMessage(e));
    }
  }

  protected async rename(): Promise<void> {
    try {
      this.setOrg(await firstValueFrom(this.api.renameOrganization(this.name)));
      this.toasts.ok('Saved');
    } catch (e) {
      this.toasts.error(errorMessage(e));
    }
  }

  private setOrg(o: OrganizationView): void {
    this.org.set(o);
    this.name = o.name;
    const me = this.session.me();
    if (me) this.session.me.set({ ...me, orgName: o.name, plan: o.plan.code });
  }
}
