import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from './api';
import { Me, Outlet } from './models';

const OUTLET_KEY = 'payoutcheck.outlet';

/** Who is signed in, their outlets, and the outlet the screens are filtered to. */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  readonly me = signal<Me | null>(null);
  readonly outlets = signal<Outlet[]>([]);
  /** null means all outlets. */
  readonly outletId = signal<number | null>(readOutlet());
  readonly loaded = signal(false);

  readonly canEdit = computed(() => this.me()?.role === 'OWNER' || this.me()?.role === 'MANAGER');
  readonly isOwner = computed(() => this.me()?.role === 'OWNER');
  readonly activeOutlets = computed(() => this.outlets().filter((o) => o.active));

  async restore(): Promise<void> {
    try {
      await firstValueFrom(this.api.csrf());
      this.me.set(await firstValueFrom(this.api.me()));
      await this.refreshOutlets();
    } catch {
      this.me.set(null);
    } finally {
      this.loaded.set(true);
    }
  }

  async signedIn(me: Me): Promise<void> {
    this.me.set(me);
    await this.refreshOutlets();
  }

  async refreshOutlets(): Promise<void> {
    const outlets = await firstValueFrom(this.api.outlets());
    this.outlets.set(outlets);
    const chosen = this.outletId();
    if (chosen !== null && !outlets.some((o) => o.id === chosen)) {
      this.selectOutlet(null);
    }
  }

  selectOutlet(id: number | null): void {
    this.outletId.set(id);
    try {
      if (id === null) localStorage.removeItem(OUTLET_KEY);
      else localStorage.setItem(OUTLET_KEY, String(id));
    } catch {
      // Storage can be unavailable (private windows); the choice just won't persist.
    }
  }

  /** The outlet to import into: the selected one, else the only one. */
  defaultOutletId(): number | null {
    const active = this.activeOutlets();
    return this.outletId() ?? (active.length ? active[0].id : null);
  }

  outletName(id: number): string {
    return this.outlets().find((o) => o.id === id)?.name ?? `Outlet ${id}`;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.logout());
    } catch {
      // Signing out locally is enough if the server is gone.
    }
    this.me.set(null);
    this.outlets.set([]);
    await firstValueFrom(this.api.csrf()).catch(() => undefined);
    await this.router.navigateByUrl('/login');
  }

  /** Called when any request comes back 401. */
  expired(): void {
    if (this.me()) {
      this.me.set(null);
      this.router.navigateByUrl('/login?expired=1');
    }
  }
}

function readOutlet(): number | null {
  try {
    const value = localStorage.getItem(OUTLET_KEY);
    return value ? Number(value) : null;
  } catch {
    return null;
  }
}
