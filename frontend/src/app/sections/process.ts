import { Component, input } from '@angular/core';

import { ProcessStep } from '../core/landing.model';

@Component({
  selector: 'app-process',
  template: `
    <section id="process" class="section">
      <div class="container">
        <div class="section-head">
          <span class="eyebrow">How we work</span>
          <h2>A clear path from first call to production.</h2>
        </div>

        <ol class="steps">
          @for (step of steps(); track step.title; let i = $index) {
            <li>
              <span class="num">{{ i + 1 }}</span>
              <h3>{{ step.title }}</h3>
              <p>{{ step.description }}</p>
            </li>
          }
        </ol>
      </div>
    </section>
  `,
  styles: `
    .steps {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 2rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    li {
      position: relative;
    }

    /* Connector between step numbers; hidden once the grid wraps. */
    li:not(:last-child)::after {
      content: '';
      position: absolute;
      top: 1.35rem;
      left: 3.5rem;
      right: -1.25rem;
      border-top: 1px dashed #c9c6ba;
    }

    .num {
      display: grid;
      place-items: center;
      width: 2.75rem;
      height: 2.75rem;
      border-radius: 50%;
      background: var(--ink);
      color: #fff;
      font: 600 1rem var(--font-display);
    }

    h3 {
      margin-top: 1.25rem;
      font-size: 1.2rem;
    }

    p {
      margin-top: 0.6rem;
      color: var(--muted);
    }

    @media (max-width: 1000px) {
      li::after {
        display: none;
      }
    }
  `
})
export class Process {
  readonly steps = input.required<ProcessStep[]>();
}
