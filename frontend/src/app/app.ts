import { afterNextRender, ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs/operators';

import { rememberCampaign } from './core/lead.service';
import { Footer } from './layout/footer';
import { Header } from './layout/header';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, Header, Footer],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip-link" href="#main">Skip to content</a>
    <app-header />
    <main id="main" tabindex="-1">
      <router-outlet />
    </main>
    <app-footer />
    @if (showMobileCta()) {
      <div class="mobile-cta">
        <a class="btn btn-ghost btn-sm" href="/ecommerce/">Live demo</a>
        <a class="btn btn-primary btn-sm" routerLink="/contact">Discuss your project</a>
      </div>
    }
  `,
  styles: `
    main { display: block; outline: none; }
    .mobile-cta { display: none; }
    @media (max-width: 720px) {
      .mobile-cta {
        position: fixed; left: 0; right: 0; bottom: 0; z-index: 40; display: grid; grid-template-columns: 1fr 1.6fr; gap: 8px;
        padding: 10px 12px calc(10px + env(safe-area-inset-bottom)); background: rgba(255, 255, 255, .96);
        border-top: 1px solid var(--line); box-shadow: 0 -8px 24px -16px rgba(11, 27, 51, .35); backdrop-filter: blur(10px);
      }
      :host { display: block; padding-bottom: 68px; }
    }
  `,
})
export class App {
  protected readonly showMobileCta = signal(true);

  constructor() {
    inject(Router)
      .events.pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => this.showMobileCta.set(!event.urlAfterRedirects.startsWith('/contact')));

    afterNextRender(() => {
      rememberCampaign();
      (window as unknown as { ltReady?: boolean }).ltReady = true;
    });
  }
}
