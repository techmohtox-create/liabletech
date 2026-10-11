import { Component, signal } from '@angular/core';

@Component({
  selector: 'app-header',
  template: `
    <header class="bar">
      <div class="container inner">
        <a class="brand" href="#top" aria-label="Liable Tech home">liable<span>tech</span></a>

        <button
          class="toggle"
          type="button"
          aria-controls="site-nav"
          [attr.aria-expanded]="menuOpen()"
          (click)="menuOpen.set(!menuOpen())"
        >
          <span class="sr-only">Menu</span>
          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
            <path [attr.d]="menuOpen() ? 'M6 6l12 12M18 6L6 18' : 'M4 7h16M4 12h16M4 17h16'" />
          </svg>
        </button>

        <nav id="site-nav" [class.open]="menuOpen()" (click)="menuOpen.set(false)">
          @for (link of links; track link.target) {
            <a [href]="link.target">{{ link.label }}</a>
          }
          <a class="btn btn-primary btn-sm" href="#contact">Talk to us</a>
        </nav>
      </div>
    </header>
  `,
  styles: `
    .bar {
      position: fixed;
      inset: 0 0 auto;
      z-index: 20;
      background: rgba(10, 16, 32, 0.86);
      border-bottom: 1px solid var(--ink-line);
      backdrop-filter: blur(12px);
      color: var(--on-ink);
    }

    .inner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      height: var(--header-h);
    }

    .brand {
      font-family: var(--font-display);
      font-size: 1.35rem;
      font-weight: 700;
      letter-spacing: -0.03em;
      text-decoration: none;

      span {
        color: var(--mint);
      }
    }

    nav {
      display: flex;
      align-items: center;
      gap: 1.75rem;

      a:not(.btn) {
        color: var(--on-ink-muted);
        font-size: 0.95rem;
        font-weight: 500;
        text-decoration: none;
        transition: color 0.15s;

        &:hover {
          color: #fff;
        }
      }
    }

    .toggle {
      display: none;
      padding: 0.4rem;
      border: 0;
      background: none;
      color: inherit;
      cursor: pointer;

      path {
        fill: none;
        stroke: currentColor;
        stroke-width: 2;
        stroke-linecap: round;
      }
    }

    @media (max-width: 820px) {
      .toggle {
        display: block;
      }

      nav {
        display: none;
        position: absolute;
        inset: var(--header-h) 0 auto;
        flex-direction: column;
        align-items: stretch;
        gap: 0;
        padding: 0.5rem 1.25rem 1.25rem;
        background: var(--ink);
        border-bottom: 1px solid var(--ink-line);

        &.open {
          display: flex;
        }

        a:not(.btn) {
          padding: 0.85rem 0;
          border-bottom: 1px solid var(--ink-line);
          font-size: 1rem;
        }

        .btn {
          margin-top: 1rem;
        }
      }
    }
  `
})
export class Header {
  protected readonly menuOpen = signal(false);

  protected readonly links = [
    { label: 'Services', target: '#services' },
    { label: 'Approach', target: '#approach' },
    { label: 'Process', target: '#process' }
  ];
}
