import { Component, input } from '@angular/core';

import { Principle, TechnologyGroup } from '../core/landing.model';

@Component({
  selector: 'app-approach',
  template: `
    <section id="approach" class="section">
      <div class="container">
        <div class="section-head">
          <span class="eyebrow">Why Liable Tech</span>
          <h2>Liable by name. Accountable by design.</h2>
          <p>Most IT projects fail on ownership, not technology. This is how we make sure someone always answers for the outcome.</p>
        </div>

        <ol class="principles">
          @for (principle of principles(); track principle.title; let i = $index) {
            <li>
              <span>0{{ i + 1 }}</span>
              <h3>{{ principle.title }}</h3>
              <p>{{ principle.description }}</p>
            </li>
          }
        </ol>

        <dl class="tech">
          @for (group of technologies(); track group.category) {
            <div>
              <dt>{{ group.category }}</dt>
              <dd>
                @for (item of group.items; track item) {
                  <span>{{ item }}</span>
                }
              </dd>
            </div>
          }
        </dl>
      </div>
    </section>
  `,
  styles: `
    section {
      background: var(--card);
      border-block: 1px solid var(--line);
    }

    .principles {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
      gap: 2rem;
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        padding-top: 1.25rem;
        border-top: 2px solid var(--ink);
      }

      span {
        color: var(--accent);
        font: 600 0.85rem var(--font-display);
      }

      h3 {
        margin-top: 0.6rem;
        font-size: 1.15rem;
      }

      p {
        margin-top: 0.6rem;
        color: var(--muted);
      }
    }

    .tech {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
      gap: 1.5rem 2rem;
      margin: clamp(2.5rem, 6vw, 4rem) 0 0;
      padding: 1.75rem;
      border-radius: var(--radius);
      background: var(--paper);

      dt {
        color: var(--muted);
        font-size: 0.78rem;
        font-weight: 600;
        letter-spacing: 0.1em;
        text-transform: uppercase;
      }

      dd {
        display: flex;
        flex-wrap: wrap;
        gap: 0.45rem;
        margin: 0.7rem 0 0;
      }

      span {
        padding: 0.3rem 0.75rem;
        border: 1px solid var(--line);
        border-radius: 999px;
        background: var(--card);
        font-size: 0.88rem;
        font-weight: 500;
      }
    }
  `
})
export class Approach {
  readonly principles = input.required<Principle[]>();
  readonly technologies = input.required<TechnologyGroup[]>();
}
