import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ENGAGEMENTS, SERVICES } from '../core/content';
import { Seo } from '../core/seo.service';
import { CtaBand } from '../shared/cta-band';
import { Icon } from '../shared/icon';
import { Reveal } from '../shared/reveal';

@Component({
  selector: 'app-services-page',
  imports: [RouterLink, Icon, CtaBand, Reveal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page-hero">
      <div class="container">
        <ol class="crumbs"><li><a routerLink="/">Home</a></li><li>Services</li></ol>
        <span class="eyebrow">Services</span>
        <h1>IT services that turn complex problems into reliable systems</h1>
        <p class="lead">
          AI automation, custom software, cloud, integrations, reliability and support, delivered by one accountable
          team. Pick a single service or combine them; you always know who owns the result.
        </p>
        <div class="btn-row">
          <a class="btn btn-primary btn-lg" routerLink="/contact">Discuss your project <app-icon class="icon-move" name="arrow" [size]="18" /></a>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="container list">
        @for (s of services; track s.slug; let i = $index) {
          <article class="row" data-reveal>
            <div class="head">
              <span class="icon-tile big"><app-icon [name]="s.icon" [size]="28" /></span>
              <span class="num">0{{ i + 1 }}</span>
            </div>
            <div class="body">
              <h2><a [routerLink]="['/services', s.slug]">{{ s.name }}</a></h2>
              <p class="lead">{{ s.short }}</p>
              <ul class="outcomes">
                @for (o of s.outcomes.slice(0, 3); track o) {
                  <li><app-icon name="check" [size]="16" /> {{ o }}</li>
                }
              </ul>
            </div>
            <a class="btn btn-ghost" [routerLink]="['/services', s.slug]" [attr.aria-label]="'Learn more about ' + s.name">
              Learn more <app-icon class="icon-move" name="arrow" [size]="16" />
            </a>
          </article>
        }
      </div>
    </section>

    <section class="section section-mist">
      <div class="container">
        <div class="section-head" data-reveal>
          <span class="eyebrow">Engagement models</span>
          <h2>Ways to work with us</h2>
        </div>
        <div class="grid grid-4">
          @for (m of engagements; track m.title) {
            <div class="card" data-reveal>
              <h3>{{ m.title }}</h3>
              <p class="muted">{{ m.description }}</p>
              <p class="small"><strong>Best for:</strong> {{ m.best }}</p>
            </div>
          }
        </div>
      </div>
    </section>

    <app-cta-band />
  `,
  styles: `
    .list { display: grid; gap: 0; }
    .row {
      display: grid; grid-template-columns: 120px 1fr auto; gap: 28px; align-items: center; padding: 34px 0;
      border-bottom: 1px solid var(--line);
    }
    .row:first-child { padding-top: 0; }
    .head { display: grid; gap: 10px; justify-items: start; }
    .icon-tile.big { width: 64px; height: 64px; border-radius: 16px; }
    .num { font-weight: 700; color: var(--muted); font-size: .9rem; letter-spacing: .08em; }
    h2 { font-size: clamp(1.4rem, 2.6vw, 1.8rem); margin-bottom: 8px; }
    h2 a { color: var(--navy); }
    .lead { margin-bottom: 14px; }
    .outcomes { list-style: none; padding: 0; margin: 0; display: flex; flex-wrap: wrap; gap: 8px 22px; color: var(--slate); font-size: .95rem; }
    .outcomes li { display: inline-flex; gap: 6px; align-items: center; }
    .outcomes app-icon { color: var(--success); }
    @media (max-width: 760px) { .row { grid-template-columns: 1fr; gap: 14px; } .head { grid-auto-flow: column; align-items: center; } }
  `,
})
export class ServicesPage {
  protected readonly services = SERVICES;
  protected readonly engagements = ENGAGEMENTS;

  constructor() {
    inject(Seo).set({
      title: 'IT services: AI automation, cloud & software | LiableTech',
      description:
        'AI automation, custom software development, cloud engineering, APIs and integrations, site reliability and managed support from one accountable team.',
      path: '/services',
      crumbs: [{ name: 'Services', path: '/services' }],
      schema: [
        {
          '@type': 'ItemList',
          itemListElement: SERVICES.map((s, i) => ({
            '@type': 'ListItem',
            position: i + 1,
            name: s.name,
            url: `https://liabletech.com/services/${s.slug}`,
          })),
        },
      ],
    });
  }
}

