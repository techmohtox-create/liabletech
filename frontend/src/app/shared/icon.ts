import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Brand icons (24 px grid, 2 px stroke, one cyan "keystone" module per service icon) plus a few
 * interface glyphs. Inline SVG so they render in the pre-rendered HTML with no extra requests.
 */
export type IconName =
  | 'ai' | 'software' | 'cloud' | 'api' | 'sre' | 'support'
  | 'arrow' | 'check' | 'plus' | 'menu' | 'close' | 'chevron' | 'external' | 'mail' | 'cart' | 'pulse' | 'grid' | 'tender';

@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', class: 'icon' },
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      @switch (name()) {
        @case ('ai') {
          <rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>
          <path d="M10 6.5h4.5a2 2 0 0 1 2 2V14"/><rect x="14" y="3" width="7" height="7" rx="1.5" class="k"/>
        }
        @case ('api') {
          <rect x="3" y="4" width="6" height="6" rx="1.5"/><rect x="3" y="14" width="6" height="6" rx="1.5"/>
          <rect x="15" y="9" width="6" height="6" rx="1.5" class="k"/><path d="M9 7h3v10H9M12 12h3"/>
        }
        @case ('cloud') {
          <path d="M7 18h10a4 4 0 0 0 .6-7.95A6 6 0 0 0 6.2 9.1 4.5 4.5 0 0 0 7 18z"/>
          <rect x="10" y="12" width="4" height="4" rx="1" class="k"/>
        }
        @case ('software') {
          <rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M8 13l-2 2 2 2M12 17h4"/>
          <rect x="15" y="12" width="3" height="3" rx=".8" class="k"/>
        }
        @case ('support') {
          <path d="M4 14v-2a8 8 0 0 1 16 0v2"/><rect x="3" y="14" width="4" height="6" rx="1.5"/>
          <rect x="17" y="14" width="4" height="6" rx="1.5" class="k"/>
        }
        @case ('sre') {
          <path d="M3 13h4l2-5 4 10 2-5h6"/><rect x="17" y="4" width="4" height="4" rx="1" class="k"/>
        }
        @case ('cart') {
          <path d="M3 4h2l2.4 11h10.2L20 8H7"/><circle cx="10" cy="19" r="1.4"/><circle cx="17" cy="19" r="1.4"/>
          <rect x="11" y="9" width="4" height="3" rx=".8" class="k"/>
        }
        @case ('pulse') {
          <path d="M12 21s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 11c0 5.6-7 10-7 10z"/><path d="M8 12h2l1-2 2 4 1-2h2"/>
        }
        @case ('grid') {
          <rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/>
          <rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5" class="k"/>
        }
        @case ('tender') {
          <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M8.5 14l2 2 4-4"/>
          <rect x="8" y="6" width="3" height="3" rx=".8" class="k"/>
        }
        @case ('arrow') { <path d="M5 12h14M13 6l6 6-6 6"/> }
        @case ('check') { <path d="M5 12.5l4.5 4.5L19 7.5"/> }
        @case ('plus') { <path d="M12 5v14M5 12h14"/> }
        @case ('menu') { <path d="M4 7h16M4 12h16M4 17h16"/> }
        @case ('close') { <path d="M6 6l12 12M18 6L6 18"/> }
        @case ('chevron') { <path d="M6 9l6 6 6-6"/> }
        @case ('external') { <path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/> }
        @case ('mail') { <rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 7l9 6 9-6"/> }
      }
    </svg>
  `,
  styles: `
    :host { display: inline-flex; line-height: 0; flex: none; }
    .k { fill: var(--cyan, #16c7e3); stroke: none; }
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(24);
}
