import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { Api, errorMessage } from '../core/api';
import { Role, UserView } from '../core/models';
import { Session } from '../core/session';
import { Toasts } from '../core/toast';
import { FORMAT_PIPES } from '../shared/format';
import { Modal } from '../shared/modal';

@Component({
  selector: 'app-team',
  imports: [FormsModule, Modal, ...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-head">
      <div>
        <h1>Team</h1>
        <p>Owners manage the plan and team. Managers upload files, set terms and work disputes. Viewers, such as your accountant, can read and export.</p>
      </div>
      @if (session.isOwner()) { <button class="btn primary" type="button" (click)="adding.set(true)">Add person</button> }
    </div>
    <section class="card">
      <div class="table-wrap">
        <table class="data">
          <thead><tr><th>Name</th><th>Role</th><th>Last sign-in</th><th></th></tr></thead>
          <tbody>
            @for (u of users(); track u.id) {
              <tr>
                <td><strong>{{ u.fullName }}</strong>@if (u.id === session.me()?.id) { <span class="muted"> (you)</span> }
                  <div class="muted small">{{ u.email }}</div></td>
                <td>
                  @if (session.isOwner() && u.id !== session.me()?.id) {
                    <select [ngModel]="u.role" (ngModelChange)="update(u, $event, u.active)" [attr.aria-label]="'Role for ' + u.fullName" style="width: auto">
                      <option value="OWNER">Owner</option><option value="MANAGER">Manager</option><option value="VIEWER">Viewer</option>
                    </select>
                  } @else { {{ u.role.toLowerCase() }} }
                  @if (!u.active) { <span class="badge st-ignored">Deactivated</span> }
                </td>
                <td>{{ u.lastLoginAt | stamp }}</td>
                <td class="num">
                  @if (session.isOwner() && u.id !== session.me()?.id) {
                    <button type="button" class="btn small" (click)="update(u, u.role, !u.active)">{{ u.active ? 'Deactivate' : 'Reactivate' }}</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>

    <section class="card" style="margin-top: 20px; max-width: 560px">
      <div class="card-head"><h2>Change your password</h2></div>
      <form class="card-body stack" (ngSubmit)="changePassword()" #pw="ngForm">
        <label class="field"><span>Current password</span><input type="password" name="current" [(ngModel)]="current" required autocomplete="current-password" /></label>
        <label class="field"><span>New password</span><input type="password" name="next" [(ngModel)]="next" required minlength="8" autocomplete="new-password" /></label>
        <div class="actions" style="justify-content: flex-end"><button class="btn" type="submit" [disabled]="pw.invalid">Change password</button></div>
      </form>
    </section>

    <app-modal heading="Add a person" [open]="adding()" (closed)="adding.set(false)">
      <form class="stack" (ngSubmit)="add()" #f="ngForm">
        <div class="form-grid">
          <label class="field"><span>Name</span><input type="text" name="fullName" [(ngModel)]="model.fullName" required maxlength="120" /></label>
          <label class="field"><span>Email</span><input type="email" name="email" [(ngModel)]="model.email" required email /></label>
          <label class="field"><span>Temporary password</span><input type="text" name="password" [(ngModel)]="model.password" required minlength="8" />
            <small>Share it with them; they can change it after signing in.</small></label>
          <label class="field"><span>Role</span>
            <select name="role" [(ngModel)]="model.role"><option value="MANAGER">Manager</option><option value="VIEWER">Viewer</option><option value="OWNER">Owner</option></select></label>
        </div>
        <div class="actions" style="justify-content: flex-end">
          <button type="button" class="btn" (click)="adding.set(false)">Cancel</button>
          <button type="submit" class="btn primary" [disabled]="f.invalid">Add</button>
        </div>
      </form>
    </app-modal>
  `,
})
export class TeamPage {
  private readonly api = inject(Api);
  private readonly toasts = inject(Toasts);
  protected readonly session = inject(Session);
  protected readonly users = signal<UserView[]>([]);
  protected readonly adding = signal(false);
  protected model = { fullName: '', email: '', password: '', role: 'MANAGER' as Role };
  protected current = '';
  protected next = '';

  constructor() {
    this.load();
  }

  protected async add(): Promise<void> {
    try {
      await firstValueFrom(this.api.addUser(this.model));
      this.toasts.ok(`${this.model.fullName} added`);
      this.model = { fullName: '', email: '', password: '', role: 'MANAGER' };
      this.adding.set(false);
      this.load();
    } catch (e) {
      this.toasts.error(errorMessage(e));
    }
  }

  protected async update(user: UserView, role: Role, active: boolean): Promise<void> {
    try {
      await firstValueFrom(this.api.updateUser(user.id, role, active));
      this.toasts.ok('Saved');
    } catch (e) {
      this.toasts.error(errorMessage(e));
    }
    this.load();
  }

  protected async changePassword(): Promise<void> {
    try {
      await firstValueFrom(this.api.changePassword(this.current, this.next));
      this.current = this.next = '';
      this.toasts.ok('Password changed');
    } catch (e) {
      this.toasts.error(errorMessage(e));
    }
  }

  private load(): void {
    this.api.users().subscribe((u) => this.users.set(u));
  }
}
