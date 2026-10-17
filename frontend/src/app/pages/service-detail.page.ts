import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PROCESS, SERVICES } from '../core/content';
import { schema, Seo } from '../core/seo.service';
import { CtaBand } from '../shared/cta-band';
import { FaqList } from '../shared/faq';
import { Icon } from '../shared/icon';
import { Reveal } from '../shared/reveal';

@Component({
  selector: 'app-service-detail-page',
  imports: [RouterLink, Icon, FaqList, CtaBand, Reveal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (service(); as s) {
      <section class="page-hero">
        <div class="container hero">
          <div>
            <ol class="crumbs">
              <li><a routerLink="/">Home</a></li><li><a routerLink="/services">Services</a></li><li>{{ s.name }}</li>
            </ol>
            <span class="eyebrow">{{ s.name }}</span>
            <h1>{{ s.headline }}</h1>
            <p class="lead">{{ s.intro }}</p>
            <div class="btn-row">
              <a class="btn btn-primary btn-lg" routerLink="/contact" [queryParams]="{ interest: s.slug }">
                Discuss your project <app-icon class="icon-move" name="arrow" [size]="18" />
              </a>
              <a class="btn btn-ghost btn-lg" [routerLink]="[]" fragment="what-you-get">What you get</a>
            </div>
          </div>
          <div class="outcome-card">
            <span class="icon-tile big"><app-icon [name]="s.icon" [size]="30" /></span>
            <p class="label">What changes for you</p>
            <ul class="check-list">
              @for (o of s.outcomes; track o) {
                <li><span class="tick"><app-icon name="check" [size]="14" /></span><span>{{ o }}</span></li>
              }
            </ul>
          </div>
        </div>
      </section>

      <section class="section" id="what-you-get">
        <div class="container">
          <div class="section-head" data-reveal>
            <span class="eyebrow">What you get</span>
            <h2>What we deliver</h2>
          </div>
          <div class="grid grid-3">
            @for (d of s.deliverables; track d.title; let i = $index) {
              <div class="card deliverable" data-reveal>
                <span class="n">0{{ i + 1 }}</span>
                <h3>{{ d.title }}</h3>
                <p>{{ d.text }}</p>
              </div>
            }
          </div>
        </div>
      </section>

      <section class="section section-mist">
        <div class="container two">
          <div data-reveal>
            <span class="eyebrow">Typical situations</span>
            <h2>Where this helps</h2>
            <ul class="uses">
              @for (u of s.useCases; track u) {
                <li>{{ u }}</li>
              }
            </ul>
          </div>
          <div data-reveal>
            <span class="eyebrow">Technology</span>
            <h2>Tools we use</h2>
            <p class="muted">Chosen for your situation, not out of habit. We'll explain every choice in plain language.</p>
            <div class="chips">
              @for (t of s.stack; track t) {
                <span class="chip">{{ t }}</span>
              }
            </div>
          </div>
        </div>
      </section>

      <section class="section">
        <div class="container">
          <div class="section-head" data-reveal>
            <span class="eyebrow">How it runs</span>
            <h2>From first call to running system</h2>
          </div>
          <ol class="process">
            @for (p of process; track p.title; let i = $index) {
              <li data-reveal>
                <span class="step">0{{ i + 1 }}</span>
                <h3>{{ p.title }}</h3>
                <p class="out">{{ p.outcome }}</p>
                <p>{{ p.description }}</p>
              </li>
            }
          </ol>
        </div>
      </section>

      <section class="section section-mist">
        <div class="container faq-grid">
          <div data-reveal>
            <span class="eyebrow">Questions</span>
            <h2>{{ s.name }}: common questions</h2>
          </div>
          <app-faq [items]="s.faqs" />
        </div>
      </section>

      <section class="section-tight">
        <div class="container">
          <p class="eyebrow">Related services</p>
          <div class="grid grid-3">
            @for (r of related(); track r.slug) {
              <a class="card related" [routerLink]="['/services', r.slug]">
                <app-icon [name]="r.icon" />
                <span><strong>{{ r.name }}</strong><small>{{ r.short }}</small></span>
              </a>
            }
          </div>
        </div>
      </section>

      <app-cta-band [heading]="'Talk to us about ' + s.phrase" [interest]="s.slug" />
    }
  `,
  styles: `
    .hero { display: grid; grid-template-columns: 1.35fr 1fr; gap: clamp(28px, 5vw, 64px); align-items: center; }
    .outcome-card { padding: clamp(22px, 3vw, 32px); border-radius: var(--radius-lg); background: #fff; border: 1px solid var(--line); box-shadow: var(--shadow); }
    .icon-tile.big { width: 60px; height: 60px; border-radius: 16px; margin-bottom: 18px; }
    .label { font-weight: 700; color: var(--navy); margin-bottom: 14px; }
    .deliverable .n { font-weight: 700; color: var(--signal); font-size: .9rem; letter-spacing: .08em; }
    .deliverable h3 { margin: 10px 0 8px; }
    .deliverable p { color: var(--slate); margin: 0; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: clamp(28px, 5vw, 64px); }
    .uses { list-style: none; padding: 0; margin: 20px 0 0; display: grid; gap: 10px; }
    .uses li { padding: 14px 16px; border-radius: 12px; background: #fff; border: 1px solid var(--line); font-weight: 550; color: var(--navy); }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
    .chip { background: #fff; }
    .process { list-style: none; padding: 0; margin: 0; display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; counter-reset: s; }
    .process li { padding-top: 18px; border-top: 3px solid var(--line); }
    .process li:first-child { border-top-color: var(--signal); }
    .step { font-weight: 700; color: var(--signal); font-size: .88rem; letter-spacing: .08em; }
    .process h3 { margin: 8px 0 4px; }
    .out { font-weight: 600; color: var(--navy); font-size: .95rem; margin-bottom: 6px; }
    .process p:last-child { color: var(--slate); font-size: .95rem; }
    .faq-grid { display: grid; grid-template-columns: .8fr 1.4fr; gap: clamp(24px, 5vw, 64px); align-items: start; }
    .related { display: flex; gap: 14px; align-items: flex-start; }
    .related strong { display: block; color: var(--navy); }
    .related small { color: var(--muted); font-size: .88rem; }
    @media (max-width: 900px) { .hero, .two, .faq-grid { grid-template-columns: 1fr; } .process { grid-template-columns: 1fr 1fr; } }
    @media (max-width: 560px) { .process { grid-template-columns: 1fr; } }
  `,
})
export class ServiceDetailPage {
  readonly slug = input.required<string>();

  protected readonly service = computed(() => SERVICES.find((s) => s.slug === this.slug()));
  protected readonly related = computed(() => SERVICES.filter((s) => s.slug !== this.slug()).slice(0, 3));
  protected readonly process = PROCESS;

  constructor() {
    const seo = inject(Seo);
    effect(() => {
      const s = this.service();
      if (!s) {
        return;
      }
      const path = `/services/${s.slug}`;
      seo.set({
        title: s.seoTitle,
        description: s.seoDescription,
        path,
        crumbs: [
          { name: 'Services', path: '/services' },
          { name: s.name, path },
        ],
        schema: [schema.service(s.name, s.seoDescription, path), schema.faq(s.faqs)],
      });
    });
  }
}
