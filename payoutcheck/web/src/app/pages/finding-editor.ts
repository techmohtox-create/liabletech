import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { Api, errorMessage } from '../core/api';
import { Finding, FindingStatus } from '../core/models';
import { Toasts } from '../core/toast';
import { FORMAT_PIPES, STATUS_LABEL } from '../shared/format';
import { Modal } from '../shared/modal';

/** Moves one finding through the dispute workflow. */
@Component({
  selector: 'app-finding-editor',
  imports: [FormsModule, Modal, ...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [heading]="typeLabel() || 'Update finding'" [open]="!!finding()" (closed)="cancelled.emit()">
      @if (finding(); as f) {
        <form class="stack" (ngSubmit)="save(f)">
          <p class="muted">{{ f.explanation }}</p>
          <div class="form-grid">
            <label class="field"><span>Status</span>
              <select name="status" [(ngModel)]="status">
                @for (s of statuses; track s) { <option [value]="s">{{ label[s] }}</option> }
              </select>
            </label>
            <label class="field"><span>Ticket reference</span>
              <input type="text" name="ticket" [(ngModel)]="ticketRef" maxlength="80" placeholder="Support ticket number" />
            </label>
            @if (status === 'RECOVERED') {
              <label class="field full"><span>Amount recovered</span>
                <input type="number" name="recovered" [(ngModel)]="recovered" min="0" step="0.01" />
                <small>At risk: {{ f.amountAtRisk | inr }}. Enter less for a partial recovery.</small>
              </label>
            }
            <label class="field full"><span>Notes</span>
              <textarea name="notes" rows="3" [(ngModel)]="notes" maxlength="1000" placeholder="What the platform said, evidence sent…"></textarea>
            </label>
          </div>
          @if (error()) { <div class="notice error">{{ error() }}</div> }
          <div class="actions" style="justify-content: flex-end">
            <button type="button" class="btn" (click)="cancelled.emit()">Cancel</button>
            <button type="submit" class="btn primary" [disabled]="busy()">Save</button>
          </div>
        </form>
      }
    </app-modal>
  `,
})
export class FindingEditor {
  private readonly api = inject(Api);
  private readonly toasts = inject(Toasts);
  readonly finding = input<Finding | null>(null);
  readonly typeLabel = input('');
  readonly saved = output<Finding>();
  readonly cancelled = output<void>();

  protected readonly statuses: FindingStatus[] = ['OPEN', 'DISPUTED', 'RECOVERED', 'REJECTED', 'IGNORED'];
  protected readonly label = STATUS_LABEL;
  protected status: FindingStatus = 'OPEN';
  protected ticketRef = '';
  protected recovered: number | null = null;
  protected notes = '';
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      const f = this.finding();
      if (f) {
        this.status = f.status === 'OPEN' ? 'DISPUTED' : f.status;
        this.ticketRef = f.ticketRef ?? '';
        this.recovered = f.recoveredAmount ?? f.amountAtRisk;
        this.notes = f.notes ?? '';
        this.error.set('');
      }
    });
  }

  protected async save(f: Finding): Promise<void> {
    this.busy.set(true);
    try {
      const updated = await firstValueFrom(
        this.api.updateFinding(f.id, {
          status: this.status,
          ticketRef: this.ticketRef || null,
          recoveredAmount: this.status === 'RECOVERED' ? this.recovered : null,
          notes: this.notes || null,
        }),
      );
      this.toasts.ok(`Marked ${STATUS_LABEL[updated.status].toLowerCase()}`);
      this.saved.emit(updated);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
