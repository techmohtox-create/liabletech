import { Component, input } from '@angular/core';

import { ServiceOffering } from '../core/landing.model';

const ICON_PATHS: Record<string, string> = {
  code: 'M8 6l-6 6 6 6M16 6l6 6-6 6',
  cloud: 'M7 18a4 4 0 0 1-.5-7.97A6 6 0 0 1 18 9.5a4.25 4.25 0 0 1-.5 8.5H7z',
  data: 'M4 5v14h16M8 15v-4M12 15V8M16 15v-6',
  shield: 'M12 3l8 3v6c0 4.5-3.2 8-8 9-4.8-1-8-4.5-8-9V6l8-3zM9 12l2 2 4-4',
  support: 'M4 13a8 8 0 0 1 16 0M4 13v4a2 2 0 0 0 2 2h1v-6H4zM20 13v4a2 2 0 0 1-2 2h-1v-6h3z',
  compass: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM15.5 8.5l-2 5-5 2 2-5 5-2z'
};

@Component({
  selector: 'app-services',
  template: `
    <section id="services" class="section">
      <div class="container">
        <div class="section-head">
          <span class="eyebrow">What we do</span>
          <h2>Everything your technology needs, under one roof.</h2>
          <p>Engage us for a single project or as your long-term IT partner. Either way, one team owns the result.</p>
        </div>

        <ul class="cards">
          @for (service of services(); track service.id) {
            <li class="card">
              <svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true">
                <path [attr.d]="iconPath(service.icon)" />
              </svg>
              <h3>{{ service.title }}</h3>
              <p>{{ service.summary }}</p>
              <ul class="caps">
                @for (capability of service.capabilities; track capability) {
                  <li>{{ capability }}</li>
                }
              </ul>
            </li>
          }
        </ul>
      </div>
    </section>
  `,
  styles: `
    .cards {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 1.25rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .card {
      padding: 1.75rem;
      border: 1px solid var(--line);
      border-radius: var(--radius);
      background: var(--card);
      transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

      &:hover {
        border-color: var(--accent);
        box-shadow: 0 18px 40px -24px rgba(47, 91, 240, 0.45);
        transform: translateY(-3px);
      }
    }

    svg {
      box-sizing: content-box;
      padding: 0.7rem;
      border-radius: 12px;
      background: rgba(47, 91, 240, 0.08);

      path {
        fill: none;
        stroke: var(--accent);
        stroke-width: 1.7;
        stroke-linecap: round;
        stroke-linejoin: round;
      }
    }

    h3 {
      margin-top: 1.25rem;
      font-size: 1.2rem;
    }

    p {
      margin-top: 0.6rem;
      color: var(--muted);
    }

    .caps {
      margin: 1.25rem 0 0;
      padding: 1.1rem 0 0;
      border-top: 1px solid var(--line);
      list-style: none;
      font-size: 0.93rem;

      li {
        position: relative;
        padding-left: 1.25rem;

        & + li {
          margin-top: 0.35rem;
        }

        &::before {
          content: '';
          position: absolute;
          left: 0;
          top: 0.6em;
          width: 0.4rem;
          height: 0.4rem;
          border-radius: 50%;
          background: var(--mint);
        }
      }
    }
  `
})
export class Services {
  readonly services = input.required<ServiceOffering[]>();

  protected iconPath(icon: string): string {
    return ICON_PATHS[icon] ?? ICON_PATHS['code'];
  }
}
