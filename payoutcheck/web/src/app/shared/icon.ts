import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Stroke icons (24px grid, 1.8px stroke), drawn inline so the CSP needs no external images. */
const PATHS: Record<string, string> = {
  dashboard: 'M4 13h6V4H4zM14 20h6v-9h-6zM14 4v4h6V4zM4 20h6v-4H4z',
  statements: 'M7 3h7l5 5v13H7zM14 3v5h5M10 13h6M10 17h6',
  findings: 'M12 3l9 16H3zM12 10v4M12 17.5v.5',
  orders: 'M4 6h16M4 12h16M4 18h10',
  upload: 'M12 16V4M7 9l5-5 5 5M5 20h14',
  outlet: 'M4 10l2-6h12l2 6M4 10h16v10H4zM9 20v-5h6v5',
  team: 'M9 11a3.5 3.5 0 100-7 3.5 3.5 0 000 7zM3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M16 4.5a3.5 3.5 0 010 6.5M18 14c2 .8 3 3 3 6',
  plan: 'M3 7h18v12H3zM3 11h18M7 15h4',
  audit: 'M12 8v4l3 2M12 21a9 9 0 110-18 9 9 0 010 18z',
  logout: 'M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  x: 'M6 6l12 12M18 6L6 18',
  download: 'M12 4v12M7 11l5 5 5-5M5 20h14',
  refresh: 'M20 11a8 8 0 10-2.3 5.7M20 5v6h-6',
  copy: 'M9 9h11v11H9zM5 15H4V4h11v1',
  mail: 'M3 6h18v12H3zM3 7l9 7 9-7',
  trash: 'M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13',
  menu: 'M4 7h16M4 12h16M4 17h16',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  info: 'M12 21a9 9 0 110-18 9 9 0 010 18zM12 11v6M12 7.5v.5',
  search: 'M11 18a7 7 0 110-14 7 7 0 010 14zM20 20l-4-4',
  file: 'M7 3h7l5 5v13H7zM14 3v5h5',
  shield: 'M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6z',
  lock: 'M6 11h12v9H6zM9 11V8a3 3 0 016 0v3',
};

@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="d()" /></svg>`,
  styles: `:host { display: inline-flex; flex: none; }`,
})
export class Icon {
  readonly name = input.required<string>();
  readonly size = input(18);
  protected readonly d = computed(() => PATHS[this.name()] ?? '');
}
