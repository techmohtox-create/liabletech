import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { Api } from '../core/api';
import { AuditEvent, Page } from '../core/models';
import { FORMAT_PIPES } from '../shared/format';

@Component({
  selector: 'app-audit',
  imports: [...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-head">
      <div><h1>Audit log</h1><p>Every upload, check, status change and setting, with who made it.</p></div>
    </div>
    <section class="card">
      <div class="table-wrap">
        <table class="data">
          <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Detail</th></tr></thead>
          <tbody>
            @for (e of result()?.items ?? []; track e.id) {
              <tr>
                <td class="nowrap">{{ e.createdAt | stamp }}</td>
                <td>{{ e.actorEmail ?? 'system' }}</td>
                <td class="mono">{{ e.action }}</td>
                <td class="explain">{{ e.detail ?? '' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <div class="card-foot">
        <span class="muted">{{ result()?.total ?? 0 }} events</span>
        <div class="pager">
          <button class="btn small" type="button" [disabled]="page() === 0" (click)="page.set(page() - 1)">Newer</button>
          <span>Page {{ page() + 1 }} of {{ pages() }}</span>
          <button class="btn small" type="button" [disabled]="page() + 1 >= pages()" (click)="page.set(page() + 1)">Older</button>
        </div>
      </div>
    </section>
  `,
})
export class AuditPage {
  private readonly api = inject(Api);
  protected readonly page = signal(0);
  protected readonly result = signal<Page<AuditEvent> | null>(null);
  protected readonly pages = computed(() => Math.max(1, Math.ceil((this.result()?.total ?? 0) / 50)));

  constructor() {
    effect(() => this.api.audit(this.page()).subscribe((r) => this.result.set(r)));
  }
}
