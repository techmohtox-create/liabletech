import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api, errorMessage } from '../core/api';
import { DisputeLetter, Finding, FindingTypeInfo, StatementDetail } from '../core/models';
import { Session } from '../core/session';
import { Toasts } from '../core/toast';
import { PlatformBadge, SeverityBadge, StatusBadge } from '../shared/badges';
import { FORMAT_PIPES } from '../shared/format';
import { Icon } from '../shared/icon';
import { Modal } from '../shared/modal';
import { FindingEditor } from './finding-editor';

@Component({
  selector: 'app-statement-detail',
  imports: [RouterLink, PlatformBadge, SeverityBadge, StatusBadge, Icon, Modal, FindingEditor, ...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './statement-detail.html',
})
export class StatementDetailPage {
  private readonly api = inject(Api);
  private readonly toasts = inject(Toasts);
  private readonly router = inject(Router);
  protected readonly session = inject(Session);

  readonly id = input.required<string>();
  protected readonly detail = signal<StatementDetail | null>(null);
  protected readonly types = signal<FindingTypeInfo[]>([]);
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly tab = signal<'findings' | 'rows'>('findings');
  protected readonly onlyFlagged = signal(false);
  protected readonly editing = signal<Finding | null>(null);
  protected readonly letter = signal<DisputeLetter | null>(null);
  protected readonly confirmDelete = signal(false);

  protected readonly flaggedLines = computed(() => new Set((this.detail()?.findings ?? []).map((f) => f.lineId).filter((id) => id !== null)));
  protected readonly lines = computed(() => {
    const lines = this.detail()?.lines ?? [];
    return this.onlyFlagged() ? lines.filter((l) => this.flaggedLines().has(l.id)) : lines;
  });
  protected readonly open = computed(() =>
    (this.detail()?.findings ?? []).filter((f) => f.status === 'OPEN' || f.status === 'DISPUTED'),
  );
  protected readonly openAtRisk = computed(() => this.open().reduce((sum, f) => sum + f.amountAtRisk, 0));
  protected readonly recovered = computed(() => (this.detail()?.findings ?? []).reduce((sum, f) => sum + (f.recoveredAmount ?? 0), 0));

  constructor() {
    this.api.findingTypes().subscribe((t) => this.types.set(t));
    effect(() => this.load(Number(this.id())));
  }

  protected typeLabel(code: string): string {
    return this.types().find((t) => t.code === code)?.label ?? code;
  }

  protected load(id: number): void {
    this.api.statement(id).subscribe({
      next: (d) => this.detail.set(d),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected async recheck(): Promise<void> {
    this.busy.set(true);
    try {
      const summary = await firstValueFrom(this.api.recheck(Number(this.id())));
      this.toasts.ok(`Checked again: ${summary.findings} findings, ${summary.newFindings} new`);
      this.load(Number(this.id()));
    } catch (e) {
      this.toasts.error(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  protected async writeLetter(): Promise<void> {
    try {
      this.letter.set(await firstValueFrom(this.api.disputeLetter(Number(this.id()))));
    } catch (e) {
      this.toasts.error(errorMessage(e));
    }
  }

  protected async copyLetter(letter: DisputeLetter): Promise<void> {
    try {
      await navigator.clipboard.writeText(`Subject: ${letter.subject}\n\n${letter.body}`);
      this.toasts.ok('Copied. Paste it into a partner support ticket.');
    } catch {
      this.toasts.error('Could not copy. Select the text and copy it by hand.');
    }
  }

  protected async markLetterSent(): Promise<void> {
    const ids = this.open().filter((f) => f.status === 'OPEN' && f.amountAtRisk > 0).map((f) => f.id);
    if (ids.length) {
      await firstValueFrom(this.api.bulkUpdateFindings(ids, 'DISPUTED'));
      this.toasts.ok(`${ids.length} findings marked disputed`);
    }
    this.letter.set(null);
    this.load(Number(this.id()));
  }

  protected async remove(): Promise<void> {
    try {
      await firstValueFrom(this.api.deleteStatement(Number(this.id())));
      this.toasts.ok('Statement deleted');
      await this.router.navigateByUrl('/statements');
    } catch (e) {
      this.toasts.error(errorMessage(e));
    }
  }

  protected saved(): void {
    this.editing.set(null);
    this.load(Number(this.id()));
  }
}
