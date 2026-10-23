import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api, errorMessage } from '../core/api';
import { Session } from '../core/session';
import { AuthPitch } from './auth-pitch';

@Component({
  selector: 'app-signup',
  imports: [FormsModule, RouterLink, AuthPitch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth">
      <app-auth-pitch />
      <div class="auth-form">
        <form (ngSubmit)="submit()" #f="ngForm">
          <div class="brand"><img src="favicon.svg" alt="" width="28" height="28" /> PayoutCheck</div>
          <div>
            <h1>Create your account</h1>
            <p class="muted">Starter plan, one outlet. You can upgrade any time.</p>
          </div>
          @if (error()) { <div class="notice error" role="alert">{{ error() }}</div> }
          <label class="field"><span>Business name</span>
            <input type="text" name="businessName" [(ngModel)]="model.businessName" required maxlength="160" placeholder="e.g. Sharma's Kitchen" />
          </label>
          <label class="field"><span>First outlet</span>
            <input type="text" name="outletName" [(ngModel)]="model.outletName" maxlength="120" placeholder="e.g. Koramangala (optional)" />
          </label>
          <label class="field"><span>Your name</span>
            <input type="text" name="fullName" [(ngModel)]="model.fullName" required maxlength="120" autocomplete="name" />
          </label>
          <label class="field"><span>Email</span>
            <input type="email" name="email" [(ngModel)]="model.email" required email autocomplete="email" />
          </label>
          <label class="field"><span>Password</span>
            <input type="password" name="password" [(ngModel)]="model.password" required minlength="8" autocomplete="new-password" />
            <small>At least 8 characters.</small>
          </label>
          <button class="btn primary" type="submit" [disabled]="busy() || f.invalid">{{ busy() ? 'Creating…' : 'Create account' }}</button>
          <p class="muted">Already have an account? <a routerLink="/login">Sign in</a></p>
        </form>
      </div>
    </div>
  `,
})
export class SignupPage {
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  protected readonly model = { businessName: '', outletName: '', fullName: '', email: '', password: '' };
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected async submit(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const me = await firstValueFrom(this.api.signup(this.model));
      await this.session.signedIn(me);
      await this.router.navigateByUrl('/upload?welcome=1');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
