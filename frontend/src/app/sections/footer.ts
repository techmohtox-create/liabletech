import { Component, input } from '@angular/core';

@Component({
  selector: 'app-footer',
  template: `
    <footer>
      <div class="container inner">
        <div>
          <a class="brand" href="#top">liable<span>tech</span></a>
          <p>IT services you can hold us to.</p>
        </div>

        <nav aria-label="Footer">
          <a href="#services">Services</a>
          <a href="#approach">Approach</a>
          <a href="#process">Process</a>
          <a href="#contact">Contact</a>
          @if (email(); as address) {
            <a [href]="'mailto:' + address">{{ address }}</a>
          }
        </nav>
      </div>
      <div class="container legal">&copy; {{ year }} Liable Tech. All rights reserved.</div>
    </footer>
  `,
  styles: `
    footer {
      padding: 3rem 0 2rem;
      background: var(--ink);
      border-top: 1px solid var(--ink-line);
      color: var(--on-ink-muted);
      font-size: 0.93rem;
    }

    .inner {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 2rem;
    }

    .brand {
      color: var(--on-ink);
      font-family: var(--font-display);
      font-size: 1.25rem;
      font-weight: 700;
      letter-spacing: -0.03em;
      text-decoration: none;

      span {
        color: var(--mint);
      }
    }

    p {
      margin-top: 0.4rem;
    }

    nav {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem 1.75rem;

      a {
        text-decoration: none;

        &:hover {
          color: #fff;
        }
      }
    }

    .legal {
      margin-top: 2.5rem;
      padding-top: 1.5rem;
      border-top: 1px solid var(--ink-line);
      font-size: 0.85rem;
    }
  `
})
export class Footer {
  readonly email = input<string>();

  protected readonly year = new Date().getFullYear();
}
