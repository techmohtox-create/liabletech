import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Seo } from '../core/seo.service';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="section nf">
      <div class="container">
        <span class="eyebrow">Error 404</span>
        <h1>This page doesn't exist</h1>
        <p class="lead">It may have moved, or the link may be mistyped. These are good places to continue:</p>
        <div class="btn-row">
          <a class="btn btn-primary" routerLink="/">Home</a>
          <a class="btn btn-ghost" routerLink="/services">Services</a>
          <a class="btn btn-ghost" routerLink="/products">Products</a>
          <a class="btn btn-ghost" routerLink="/contact">Contact</a>
        </div>
      </div>
    </section>
  `,
  styles: `.nf { min-height: 60vh; display: flex; align-items: center; } .lead { max-width: 560px; }`,
})
export class NotFoundPage {
  constructor() {
    inject(Seo).set({
      title: 'Page not found | LiableTech',
      description: 'The page you were looking for does not exist.',
      path: '/404',
      noindex: true,
    });
  }
}
