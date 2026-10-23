import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PRODUCTS } from '../core/content';
import { Seo } from '../core/seo.service';
import { CtaBand } from '../shared/cta-band';
import { Icon } from '../shared/icon';
import { Reveal } from '../shared/reveal';

@Component({
  selector: 'app-products-page',
  imports: [RouterLink, Icon, CtaBand, Reveal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page-hero">
      <div class="container">
        <ol class="crumbs"><li><a routerLink="/">Home</a></li><li>Products</li></ol>
        <span class="eyebrow">Products · A LiableTech product family</span>
        <h1>Proven platforms you can launch on, then make your own</h1>
        <p class="lead">
          Every LiableTech product comes from real delivery work. Start from a working platform, customise it to your
          process and go live faster than a build from scratch, with the same team behind it.
        </p>
      </div>
    </section>

    <section class="section">
      <div class="container list">
        @for (p of products; track p.slug) {
          <article class="product" [class.live]="p.status === 'live'" data-reveal>
            <div class="icon-col">
              <span class="product-icon"><app-icon [name]="p.icon" [size]="34" /><i></i></span>
            </div>
            <div class="copy">
              <div class="title-row">
                <h2><a [routerLink]="['/products', p.slug]">{{ p.name }}</a></h2>
                <span class="badge" [class.badge-live]="p.status === 'live'" [class.badge-soon]="p.status === 'soon'">
                  {{ p.status === 'live' ? 'Live demo' : 'Coming soon' }}
                </span>
              </div>
              <p class="cat">{{ p.category }}</p>
              <p class="lead">{{ p.summary }}</p>
              <p class="aud"><strong>For:</strong> {{ p.audience }}</p>
              <div class="btn-row">
                @if (p.status === 'live') {
                  <a class="btn btn-primary" href="/ecommerce/">Open the live demo <app-icon name="external" [size]="16" /></a>
                  <a class="btn btn-ghost" [routerLink]="['/products', p.slug]">Features and how to buy</a>
                } @else {
                  <a class="btn btn-primary" [routerLink]="['/products', p.slug]">Join the waitlist</a>
                }
              </div>
            </div>
          </article>
        }
      </div>
    </section>

    <app-cta-band
      eyebrow="Need something else?"
      heading="Have a product idea of your own?"
      text="We build custom platforms too. Tell us what your business needs and get a written plan before you commit." />
  `,
  styles: `
    .list { display: grid; gap: 24px; }
    .product {
      display: grid; grid-template-columns: 96px 1fr; gap: 28px; padding: clamp(24px, 4vw, 40px);
      border: 1px solid var(--line); border-radius: var(--radius-lg); background: #fff; box-shadow: var(--shadow-sm);
    }
    .product.live { border-color: #b9c9ef; background: linear-gradient(135deg, #f4f8ff, #fff 55%); box-shadow: var(--shadow); }
    .product-icon { position: relative; width: 80px; height: 80px; border-radius: 20px; background: var(--navy); color: #fff; display: grid; place-items: center; }
    .product-icon i { position: absolute; right: 9px; bottom: 9px; width: 16px; height: 16px; border-radius: 4px; background: var(--cyan); }
    .title-row { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
    h2 { margin: 0; }
    h2 a { color: var(--navy); }
    .cat { color: var(--signal); font-weight: 600; margin: 6px 0 12px; }
    .aud { color: var(--slate); margin-bottom: 22px; }
    @media (max-width: 640px) { .product { grid-template-columns: 1fr; gap: 16px; } }
  `,
})
export class ProductsPage {
  protected readonly products = PRODUCTS;

  constructor() {
    inject(Seo).set({
      title: 'Products: BazaarFlow, MedFlow AI, ERP & TenderCheck | LiableTech',
      description:
        'LiableTech products: BazaarFlow e-commerce with GST invoicing (live demo), MedFlow AI hospital management, LiableTech ERP and TenderCheck tender eligibility checks (coming soon).',
      path: '/products',
      crumbs: [{ name: 'Products', path: '/products' }],
    });
  }
}
