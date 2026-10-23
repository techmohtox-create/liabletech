import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-auth-pitch',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="auth-pitch">
      <h1>Every rupee your delivery apps owe you, checked.</h1>
      <p>Weekly Swiggy and Zomato payouts arrive with commissions, refunds, penalties and adjustments few owners have time to check.
        PayoutCheck matches every order against the statement and lists the deductions worth disputing.</p>
      <ul>
        <li><app-icon name="check" />Orders that were delivered but never paid</li>
        <li><app-icon name="check" />Commission and fees above your contracted rate</li>
        <li><app-icon name="check" />Customer refunds, penalties and cancellation charges to challenge</li>
        <li><app-icon name="check" />A ready-to-send dispute message, and a record of what came back</li>
      </ul>
      <p class="muted">From ₹199 a month per outlet.</p>
    </section>
  `,
})
export class AuthPitch {}
