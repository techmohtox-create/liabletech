import { Component, input } from '@angular/core';

import { Hero as HeroContent, ProcessStep } from '../core/landing.model';

@Component({
  selector: 'app-hero',
  template: `
    <section class="hero on-ink">
      <div class="container grid">
        <div class="copy">
          <span class="eyebrow">{{ hero().eyebrow }}</span>
          <h1>{{ hero().headline }}</h1>
          <p class="lead">{{ hero().subheadline }}</p>
          <div class="actions">
            <a class="btn btn-primary" [href]="hero().primaryCta.target">{{ hero().primaryCta.label }}</a>
            <a class="btn btn-ghost" [href]="hero().secondaryCta.target">{{ hero().secondaryCta.label }}</a>
          </div>
        </div>

        <div class="board" aria-hidden="true">
          <div class="board-head"><i></i><i></i><i></i><span>engagement.flow</span></div>
          <ol>
            @for (step of steps(); track step.title; let i = $index) {
              <li>
                <b>0{{ i + 1 }}</b>
                <div>
                  <strong>{{ step.title }}</strong>
                  <small>{{ step.outcome }}</small>
                </div>
              </li>
            }
          </ol>
        </div>
      </div>
    </section>
  `,
  styles: `
    .hero {
      position: relative;
      padding: calc(var(--header-h) + clamp(3.5rem, 9vw, 7rem)) 0 clamp(4rem, 9vw, 7rem);
      background:
        radial-gradient(60rem 30rem at 85% -10%, rgba(47, 91, 240, 0.35), transparent 60%),
        radial-gradient(40rem 24rem at 0% 110%, rgba(47, 214, 167, 0.14), transparent 60%),
        var(--ink);
      overflow: hidden;
    }

    .grid {
      display: grid;
      grid-template-columns: 1.15fr 0.85fr;
      align-items: center;
      gap: clamp(2rem, 6vw, 5rem);
    }

    h1 {
      font-size: clamp(2.4rem, 6vw, 4.25rem);
      line-height: 1.05;
      letter-spacing: -0.035em;
    }

    .lead {
      max-width: 34rem;
      margin-top: 1.5rem;
      color: var(--on-ink-muted);
      font-size: clamp(1.05rem, 1.6vw, 1.2rem);
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.85rem;
      margin-top: 2.25rem;
    }

    .board {
      border: 1px solid var(--ink-line);
      border-radius: var(--radius);
      background: rgba(19, 27, 49, 0.8);
      box-shadow: 0 30px 80px -30px rgba(0, 0, 0, 0.7);
    }

    .board-head {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.85rem 1.1rem;
      border-bottom: 1px solid var(--ink-line);
      color: var(--on-ink-muted);
      font: 0.78rem ui-monospace, 'Cascadia Code', Consolas, monospace;

      i {
        width: 0.6rem;
        height: 0.6rem;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.18);
      }

      span {
        margin-left: auto;
      }
    }

    ol {
      margin: 0;
      padding: 0.5rem 1.1rem;
      list-style: none;
    }

    li {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 1rem 0;

      & + li {
        border-top: 1px dashed var(--ink-line);
      }
    }

    b {
      display: grid;
      place-items: center;
      flex: none;
      width: 2.4rem;
      height: 2.4rem;
      border-radius: 10px;
      background: rgba(47, 214, 167, 0.12);
      color: var(--mint);
      font: 600 0.85rem var(--font-display);
    }

    strong {
      display: block;
      font-family: var(--font-display);
      font-weight: 600;
    }

    small {
      color: var(--on-ink-muted);
      font-size: 0.88rem;
    }

    @media (max-width: 900px) {
      .grid {
        grid-template-columns: 1fr;
      }
    }
  `
})
export class Hero {
  readonly hero = input.required<HeroContent>();
  readonly steps = input.required<ProcessStep[]>();
}
