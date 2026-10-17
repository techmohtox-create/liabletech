import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SITE } from '../core/site';
import { EmailText } from './email-text';
import { Icon } from './icon';

/** The closing call to action used at the bottom of every page. */
@Component({
  selector: 'app-cta-band',
  imports: [RouterLink, Icon, EmailText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="section-navy band">
      <div class="container inner">
        <div class="copy">
          <span class="eyebrow">{{ eyebrow() }}</span>
          <h2>{{ heading() }}</h2>
          <p class="lead">{{ text() }}</p>
        </div>
        <div class="actions">
          <a class="btn btn-primary btn-lg" routerLink="/contact" [queryParams]="interest() ? { interest: interest() } : null">
            {{ cta() }} <app-icon class="icon-move" name="arrow" [size]="18" />
          </a>
          <a class="btn btn-outline-light btn-lg" [href]="'mailto:' + email">
            <app-icon name="mail" [size]="18" /> <app-email-text />
          </a>
          <p class="note">{{ promise }} Free, no-obligation discovery call.</p>
        </div>
      </div>
    </section>
  `,
  styles: `
    .band { padding: clamp(56px, 8vw, 96px) 0; position: relative; overflow: hidden; }
    .band::after {
      content: ''; position: absolute; right: -60px; bottom: -60px; width: 220px; height: 220px; border-radius: 28px;
      background: var(--cyan); opacity: .1;
    }
    .inner { display: grid; grid-template-columns: 1.3fr 1fr; gap: 40px; align-items: center; position: relative; z-index: 1; }
    h2 { margin-bottom: 12px; }
    .lead { margin: 0; }
    .actions { display: grid; gap: 12px; justify-items: start; }
    .note { margin: 4px 0 0; font-size: .9rem; color: #94a3bd !important; }
    @media (max-width: 860px) { .inner { grid-template-columns: 1fr; } }
  `,
})
export class CtaBand {
  readonly eyebrow = input('Start with a conversation');
  readonly heading = input('Tell us what you need fixed, built or run.');
  readonly text = input('Share a few lines about the problem. You get a reply within one business day and a written plan before you commit to anything.');
  readonly cta = input('Discuss your project');
  readonly interest = input<string>('');
  protected readonly email = SITE.email;
  protected readonly promise = SITE.replyPromise;
}
