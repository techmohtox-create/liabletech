import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PRODUCTS } from '../core/content';
import { Seo } from '../core/seo.service';
import { SITE } from '../core/site';
import { Icon } from '../shared/icon';
import { Reveal } from '../shared/reveal';
import { WaitlistForm } from '../shared/waitlist-form';

/** Landing page for a product that is not released yet: planned scope plus a waitlist. */
@Component({
  selector: 'app-coming-soon-page',
  imports: [RouterLink, Icon, WaitlistForm, Reveal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (product(); as p) {
      <section class="page-hero">
        <div class="container hero">
          <div>
            <ol class="crumbs">
              <li><a routerLink="/">Home</a></li><li><a routerLink="/products">Products</a></li><li>{{ p.name }}</li>
            </ol>
            <span class="badge badge-soon">Coming soon</span>
            <h1>{{ p.name }}: {{ p.tagline }}</h1>
            <p class="lead">{{ p.summary }}</p>
            <p class="aud"><strong>Built for:</strong> {{ p.audience }}</p>
            @if (p.price) {
              <p class="price"><strong>Planned price:</strong> {{ p.price }}</p>
            }
          </div>
          <div class="join" data-reveal>
            <span class="product-icon"><app-icon [name]="p.icon" [size]="30" /><i></i></span>
            <h2>Get early access</h2>
            <p class="muted">Join the waitlist to see it first, shape the roadmap and get early-adopter terms.</p>
            <app-waitlist-form [product]="p.slug" [productName]="p.name" />
          </div>
        </div>
      </section>

      <section class="section">
        <div class="container scope">
          <div data-reveal>
            <span class="eyebrow">Planned scope</span>
            <h2>What {{ p.name }} will cover</h2>
            <p class="muted">The first release focuses on the essentials below. Waitlist members help decide what comes next.</p>
          </div>
          <ul class="scope-list" data-reveal>
            @for (item of p.scope; track item; let i = $index) {
              <li><span class="n">0{{ i + 1 }}</span>{{ item }}</li>
            }
          </ul>
        </div>
      </section>

      <section class="section-tight section-mist">
        <div class="container now">
          <div>
            <h2>Need something like this now?</h2>
            <p class="muted">We can build a tailored version for your organisation today, using the same foundations.</p>
          </div>
          <div class="btn-row">
            <a class="btn btn-primary btn-lg" routerLink="/contact" [queryParams]="{ interest: p.slug }">Talk to us</a>
            <a class="btn btn-ghost btn-lg" routerLink="/products/bazaarflow">See a live product</a>
          </div>
        </div>
      </section>
    }
  `,
  styles: `
    .hero { display: grid; grid-template-columns: 1.2fr 1fr; gap: clamp(28px, 5vw, 64px); align-items: center; }
    h1 { margin-top: 14px; }
    .aud, .price { color: var(--slate); }
    .join { padding: clamp(22px, 3vw, 32px); border-radius: var(--radius-lg); background: #fff; border: 1px solid var(--line); box-shadow: var(--shadow); }
    .join h2 { font-size: 1.5rem; margin: 16px 0 6px; }
    .product-icon { position: relative; width: 60px; height: 60px; border-radius: 16px; background: var(--navy); color: #fff; display: grid; place-items: center; }
    .product-icon i { position: absolute; right: 7px; bottom: 7px; width: 12px; height: 12px; border-radius: 3px; background: var(--cyan); }
    .scope { display: grid; grid-template-columns: 1fr 1.2fr; gap: clamp(28px, 5vw, 64px); align-items: start; }
    .scope-list { list-style: none; padding: 0; margin: 0; display: grid; gap: 10px; }
    .scope-list li { display: flex; gap: 16px; align-items: center; padding: 16px 18px; border: 1px solid var(--line); border-radius: 14px; font-weight: 600; color: var(--navy); }
    .n { font-size: .85rem; color: var(--signal); font-weight: 700; letter-spacing: .08em; }
    .now { display: flex; justify-content: space-between; align-items: center; gap: 24px; flex-wrap: wrap; }
    .now h2 { font-size: 1.6rem; margin-bottom: 6px; }
    .now p { margin: 0; }
    @media (max-width: 900px) { .hero, .scope { grid-template-columns: 1fr; } }
  `,
})
export class ComingSoonPage {
  readonly slug = input.required<string>();
  protected readonly product = computed(() => PRODUCTS.find((p) => p.slug === this.slug() && p.status === 'soon'));

  constructor() {
    const seo = inject(Seo);
    effect(() => {
      const p = this.product();
      if (!p) {
        return;
      }
      const path = `/products/${p.slug}`;
      seo.set({
        title: p.seoTitle,
        description: p.seoDescription,
        path,
        crumbs: [
          { name: 'Products', path: '/products' },
          { name: p.name, path },
        ],
        schema: [
          {
            '@type': 'SoftwareApplication',
            name: p.name,
            applicationCategory: 'BusinessApplication',
            operatingSystem: 'Web browser',
            description: p.summary,
            url: SITE.url + path,
            publisher: { '@id': `${SITE.url}/#organization` },
          },
        ],
      });
    });
  }
}
