import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ENGAGEMENTS, PRINCIPLES, PROCESS } from '../core/content';
import { Seo } from '../core/seo.service';
import { SITE } from '../core/site';
import { CtaBand } from '../shared/cta-band';
import { Icon } from '../shared/icon';
import { Reveal } from '../shared/reveal';

@Component({
  selector: 'app-about-page',
  imports: [RouterLink, Icon, CtaBand, Reveal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page-hero">
      <div class="container">
        <ol class="crumbs"><li><a routerLink="/">Home</a></li><li>Our approach</li></ol>
        <span class="eyebrow">Our approach</span>
        <h1>Reliable by design: what working with LiableTech is like</h1>
        <p class="lead">{{ positioning }}</p>
      </div>
    </section>

    <section class="section">
      <div class="container name">
        <div data-reveal>
          <span class="eyebrow">Why the name</span>
          <h2>"Liable" means we answer for the result</h2>
        </div>
        <div data-reveal>
          <p class="lead">
            In English, liable means responsible. We chose it on purpose. Too much software work ends in finger-pointing
            between vendors, and too many systems run on hope. We would rather be the team that is accountable from
            the first workshop to production support.
          </p>
          <p class="muted">
            Our logo says the same thing: two load-bearing blocks, a deliberate gap between independent modules, and
            the cyan keystone that holds the structure together. Reliability, clean architecture and practical AI.
          </p>
        </div>
      </div>
    </section>

    <section class="section section-mist">
      <div class="container">
        <div class="section-head" data-reveal>
          <span class="eyebrow">Commitments</span>
          <h2>Four promises we make on every engagement</h2>
        </div>
        <div class="grid grid-2">
          @for (p of principles; track p.title; let i = $index) {
            <div class="card principle" data-reveal>
              <span class="n">0{{ i + 1 }}</span>
              <h3>{{ p.title }}</h3>
              <p>{{ p.description }}</p>
            </div>
          }
        </div>
      </div>
    </section>

    <section class="section">
      <div class="container">
        <div class="section-head" data-reveal>
          <span class="eyebrow">Delivery</span>
          <h2>How a project runs</h2>
        </div>
        <ol class="timeline">
          @for (p of process; track p.title; let i = $index) {
            <li data-reveal>
              <span class="dot">{{ i + 1 }}</span>
              <div>
                <h3>{{ p.title }} <small>{{ p.outcome }}</small></h3>
                <p>{{ p.description }}</p>
              </div>
            </li>
          }
        </ol>
      </div>
    </section>

    <section class="section section-mist">
      <div class="container">
        <div class="section-head" data-reveal>
          <span class="eyebrow">Engagement models</span>
          <h2>Pick the way of working that fits</h2>
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
        <p class="more"><a class="text-link" routerLink="/services">See all services <app-icon name="arrow" [size]="16" /></a></p>
      </div>
    </section>

    <app-cta-band />
  `,
  styles: `
    .name { display: grid; grid-template-columns: 1fr 1.3fr; gap: clamp(28px, 5vw, 64px); align-items: start; }
    .principle .n { font-weight: 700; color: var(--signal); letter-spacing: .08em; font-size: .9rem; }
    .principle h3 { margin: 10px 0 8px; }
    .principle p { color: var(--slate); margin: 0; }
    .timeline { list-style: none; padding: 0; margin: 0; max-width: 820px; display: grid; gap: 0; }
    .timeline li { display: grid; grid-template-columns: 48px 1fr; gap: 18px; padding-bottom: 28px; position: relative; }
    .timeline li:not(:last-child)::before { content: ''; position: absolute; left: 23px; top: 48px; bottom: 0; width: 2px; background: var(--line); }
    .dot { width: 48px; height: 48px; border-radius: 14px; background: var(--navy); color: #fff; display: grid; place-items: center; font-weight: 700; }
    .timeline h3 { margin: 10px 0 6px; }
    .timeline small { display: inline-block; margin-left: 8px; font-size: .85rem; font-weight: 600; color: var(--signal); }
    .timeline p { color: var(--slate); margin: 0; }
    .more { margin: 28px 0 0; }
    @media (max-width: 860px) { .name { grid-template-columns: 1fr; } }
  `,
})
export class AboutPage {
  protected readonly positioning = SITE.positioning;
  protected readonly principles = PRINCIPLES;
  protected readonly process = PROCESS;
  protected readonly engagements = ENGAGEMENTS;

  constructor() {
    inject(Seo).set({
      title: 'Our approach | LiableTech: reliable by design',
      description:
        'How LiableTech works: one accountable team, plain-language commitments, security by default and software built to hand over. See our delivery process and engagement models.',
      path: '/about',
      crumbs: [{ name: 'Our approach', path: '/about' }],
    });
  }
}
