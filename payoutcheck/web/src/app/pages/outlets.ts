import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { Api, errorMessage } from '../core/api';
import { Outlet, Platform, PlatformTerms } from '../core/models';
import { Session } from '../core/session';
import { Toasts } from '../core/toast';
import { FORMAT_PIPES, PLATFORM_LABEL } from '../shared/format';
import { Icon } from '../shared/icon';
import { Modal } from '../shared/modal';

@Component({
  selector: 'app-outlets',
  imports: [FormsModule, Icon, Modal, ...FORMAT_PIPES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './outlets.html',
})
export class OutletsPage {
  private readonly api = inject(Api);
  private readonly toasts = inject(Toasts);
  protected readonly session = inject(Session);
  protected readonly platformLabel = PLATFORM_LABEL;

  protected readonly selectedId = signal<number | null>(null);
  protected readonly terms = signal<PlatformTerms[]>([]);
  protected readonly editingOutlet = signal<Partial<Outlet> | null>(null);
  protected readonly saving = signal(false);

  constructor() {
    effect(() => {
      const outlets = this.session.outlets();
      if (this.selectedId() === null && outlets.length) {
        this.selectedId.set(this.session.outletId() ?? outlets[0].id);
      }
    });
    effect(() => {
      const id = this.selectedId();
      if (id !== null) this.api.terms(id).subscribe((t) => this.terms.set(t.map((x) => ({ ...x }))));
    });
  }

  protected selected(): Outlet | undefined {
    return this.session.outlets().find((o) => o.id === this.selectedId());
  }

  protected async saveTerms(t: PlatformTerms): Promise<void> {
    const id = this.selectedId();
    if (id === null) return;
    this.saving.set(true);
    try {
      const { platform, updatedAt, ...body } = t;
      void updatedAt;
      const saved = await firstValueFrom(this.api.saveTerms(id, platform as Platform, body));
      this.terms.update((all) => all.map((x) => (x.platform === saved.platform ? { ...saved } : x)));
      this.toasts.ok(`${PLATFORM_LABEL[platform]} terms saved. Use "Check again" on a statement to apply them to it.`);
    } catch (e) {
      this.toasts.error(errorMessage(e));
    } finally {
      this.saving.set(false);
    }
  }

  protected newOutlet(): void {
    this.editingOutlet.set({ name: '', city: '', swiggyId: '', zomatoId: '', active: true });
  }

  protected edit(outlet: Outlet): void {
    this.editingOutlet.set({ ...outlet });
  }

  protected async saveOutlet(): Promise<void> {
    const outlet = this.editingOutlet();
    if (!outlet) return;
    try {
      const saved = outlet.id
        ? await firstValueFrom(this.api.updateOutlet(outlet.id, outlet))
        : await firstValueFrom(this.api.createOutlet(outlet));
      await this.session.refreshOutlets();
      this.selectedId.set(saved.id);
      this.editingOutlet.set(null);
      this.toasts.ok('Outlet saved');
    } catch (e) {
      this.toasts.error(errorMessage(e));
    }
  }
}
