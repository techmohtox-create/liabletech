import { ChangeDetectionStrategy, Component, ElementRef, effect, input, output, viewChild } from '@angular/core';
import { Icon } from './icon';

/** A native dialog: focus trapping, Escape and the backdrop come from the browser. */
@Component({
  selector: 'app-modal',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog (close)="closed.emit()" (click)="backdrop($event)" [class.wide]="wide()">
      <header>
        <h2>{{ heading() }}</h2>
        <button type="button" class="icon-btn" (click)="dialog.close()" aria-label="Close"><app-icon name="x" /></button>
      </header>
      <div class="body"><ng-content /></div>
    </dialog>
  `,
  styles: `
    dialog {
      width: min(560px, calc(100vw - 32px)); max-height: calc(100vh - 48px); padding: 0; border: 1px solid var(--line);
      border-radius: var(--radius); background: var(--surface); color: var(--ink); box-shadow: var(--shadow-lg);
    }
    dialog.wide { width: min(820px, calc(100vw - 32px)); }
    dialog::backdrop { background: rgba(7, 15, 31, 0.5); }
    header { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border-bottom: 1px solid var(--line); }
    h2 { margin: 0; font-size: 17px; }
    .body { padding: 20px; overflow: auto; }
  `,
})
export class Modal {
  readonly heading = input.required<string>();
  readonly open = input(false);
  readonly wide = input(false);
  readonly closed = output<void>();
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const dialog = this.dialog().nativeElement;
      if (this.open() && !dialog.open) dialog.showModal();
      if (!this.open() && dialog.open) dialog.close();
    });
  }

  protected backdrop(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) this.dialog().nativeElement.close();
  }
}
