import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  kind: 'ok' | 'error';
  text: string;
}

@Injectable({ providedIn: 'root' })
export class Toasts {
  readonly items = signal<Toast[]>([]);
  private next = 1;

  ok(text: string): void {
    this.push('ok', text);
  }

  error(text: string): void {
    this.push('error', text);
  }

  dismiss(id: number): void {
    this.items.update((items) => items.filter((t) => t.id !== id));
  }

  private push(kind: Toast['kind'], text: string): void {
    const id = this.next++;
    this.items.update((items) => [...items, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), kind === 'error' ? 8000 : 4000);
  }
}
