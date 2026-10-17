import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Faq } from '../core/content';
import { Icon } from './icon';

/** Accordion built on <details>, so answers are in the HTML and work without JavaScript. */
@Component({
  selector: 'app-faq',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="faq">
      @for (item of items(); track item.q; let first = $first) {
        <details [open]="first && openFirst()">
          <summary>
            <span>{{ item.q }}</span>
            <span class="toggle"><app-icon name="plus" [size]="18" /></span>
          </summary>
          <p>{{ item.a }}</p>
        </details>
      }
    </div>
  `,
  styles: `
    .faq { border-top: 1px solid var(--line); }
    details { border-bottom: 1px solid var(--line); }
    summary {
      display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 20px 2px;
      cursor: pointer; list-style: none; font-weight: 650; font-size: 1.06rem; color: var(--navy);
    }
    summary::-webkit-details-marker { display: none; }
    summary:hover { color: var(--signal); }
    .toggle {
      flex: none; width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center;
      background: var(--mist); color: var(--navy); transition: transform var(--dur) var(--ease), background-color var(--dur);
    }
    details[open] .toggle { transform: rotate(45deg); background: var(--signal-soft); color: var(--signal); }
    p { margin: -4px 48px 22px 2px; color: var(--slate); }
  `,
})
export class FaqList {
  readonly items = input.required<Faq[]>();
  readonly openFirst = input(true);
}
