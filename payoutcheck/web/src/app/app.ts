import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Toasts } from './core/toast';
import { Icon } from './shared/icon';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
    <div class="toasts" aria-live="polite">
      @for (toast of toasts.items(); track toast.id) {
        <div class="toast" [class.toast-error]="toast.kind === 'error'" role="status">
          <app-icon [name]="toast.kind === 'error' ? 'info' : 'check'" />
          <span>{{ toast.text }}</span>
          <button type="button" class="icon-btn" (click)="toasts.dismiss(toast.id)" aria-label="Dismiss"><app-icon name="x" [size]="16" /></button>
        </div>
      }
    </div>
  `,
})
export class App {
  protected readonly toasts = inject(Toasts);
}
