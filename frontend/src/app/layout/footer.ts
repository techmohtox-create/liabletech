import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PRODUCTS, SERVICES } from '../core/content';
import { SITE } from '../core/site';

@Component({
  selector: 'app-footer',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <footer class="site-footer">
      <div class="container top">
        <div class="brand">
          <img src="/brand/liabletech-logo-primary-dark.svg" alt="LiableTech" width="150" height="29" loading="lazy" />
          <p class="tagline">{{ tagline }}</p>
          <p class="pos">{{ positioning }}</p>
          <a class="mail" [href]="'mailto:' + email">{{ email }}</a>
        </div>
        <nav aria-label="Services">
          <h2>Services</h2>
          @for (s of services; track s.slug) {
            <a [routerLink]="['/services', s.slug]">{{ s.name }}</a>
          }
        </nav>
        <nav aria-label="Products">
          <h2>Products</h2>
          @for (p of products; track p.slug) {
            <a [routerLink]="['/products', p.slug]">{{ p.name }}</a>
          }
          <a href="/ecommerce/">BazaarFlow live demo</a>
        </nav>
        <nav aria-label="Company">
          <h2>Company</h2>
          <a routerLink="/about">Our approach</a>
          <a routerLink="/contact">Contact</a>
          <a routerLink="/privacy">Privacy notice</a>
        </nav>
      </div>
      <div class="container bottom">
        <span>© {{ year }} LiableTech. All rights reserved.</span>
        <span class="bar" aria-hidden="true"><i></i><i></i></span>
      </div>
    </footer>
  `,
  styles: `
    .site-footer { background: var(--night); color: #a8b3c7; padding: 64px 0 28px; }
    .top { display: grid; grid-template-columns: 1.6fr 1.1fr 1fr 0.9fr; gap: 40px; }
    .brand img { height: 29px; width: auto; display: block; }
    .tagline { margin: 18px 0 6px; color: #fff; font-weight: 650; font-size: 1.05rem; }
    .pos { max-width: 340px; font-size: 0.95rem; line-height: 1.55; }
    .mail { color: var(--signal-light); font-weight: 600; }
    h2 { color: #fff; font-size: 0.8rem; letter-spacing: 0.1em; text-transform: uppercase; margin: 6px 0 14px; }
    nav { display: grid; align-content: start; gap: 10px; }
    nav a { color: #c3cddd; font-size: 0.95rem; }
    nav a:hover { color: #fff; }
    .bottom {
      display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-top: 48px; padding-top: 22px;
      border-top: 1px solid #1c2a44; font-size: 0.88rem;
    }
    .bar { display: inline-flex; gap: 6px; }
    .bar i { display: block; height: 6px; border-radius: 3px; }
    .bar i:first-child { width: 48px; background: var(--signal); }
    .bar i:last-child { width: 12px; background: var(--cyan); }
    @media (max-width: 900px) { .top { grid-template-columns: 1fr 1fr; } .brand { grid-column: 1 / -1; } }
    @media (max-width: 520px) { .top { grid-template-columns: 1fr; } }
  `,
})
export class Footer {
  protected readonly services = SERVICES;
  protected readonly products = PRODUCTS;
  protected readonly email = SITE.email;
  protected readonly tagline = SITE.tagline;
  protected readonly positioning = SITE.positioning;
  protected readonly year = new Date().getFullYear();
}
