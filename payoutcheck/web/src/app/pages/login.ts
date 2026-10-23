import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api, errorMessage } from '../core/api';
import { Session } from '../core/session';
import { AuthPitch } from './auth-pitch';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink, AuthPitch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth">
      <app-auth-pitch />
      <div class="auth-form">
        <form (ngSubmit)="submit()" #f="ngForm">
          <div class="brand"><img src="favicon.svg" alt="" width="28" height="28" /> PayoutCheck</div>
          <div>
            <h1>Sign in</h1>
            <p class="muted">Check this week's Swiggy and Zomato payouts.</p>
          </div>
          @if (expired) { <div class="notice">Your session ended. Sign in again to continue.</div> }
          @if (error()) { <div class="notice error" role="alert">{{ error() }}</div> }
          <label class="field"><span>Email</span>
            <input type="email" name="email" [(ngModel)]="email" required autocomplete="username" autofocus />
          </label>
          <label class="field"><span>Password</span>
            <input type="password" name="password" [(ngModel)]="password" required autocomplete="current-password" />
          </label>
          <button class="btn primary" type="submit" [disabled]="busy() || f.invalid">{{ busy() ? 'Signing in…' : 'Sign in' }}</button>
          <div class="notice">
            <span>Demo account: <strong class="mono">demo&#64;payoutcheck.local</strong> / <strong class="mono">demo1234</strong>
              <button type="button" class="btn small ghost" (click)="fillDemo()">Use it</button></span>
          </div>
          <p class="muted">New here? <a routerLink="/signup">Create an account</a></p>
        </form>
      </div>
    </div>
  `,
})
export class LoginPage {
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  private readonly router = inject(Router);
  protected readonly expired = inject(ActivatedRoute).snapshot.queryParamMap.has('expired');
  protected email = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected fillDemo(): void {
    this.email = 'demo@payoutcheck.local';
    this.password = 'demo1234';
  }

  protected async submit(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const me = await firstValueFrom(this.api.login(this.email, this.password));
      await this.session.signedIn(me);
      await this.router.navigateByUrl('/');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
