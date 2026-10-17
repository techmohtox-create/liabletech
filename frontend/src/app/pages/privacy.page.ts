import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Seo } from '../core/seo.service';
import { SITE } from '../core/site';

@Component({
  selector: 'app-privacy-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page-hero">
      <div class="container">
        <ol class="crumbs"><li><a routerLink="/">Home</a></li><li>Privacy notice</li></ol>
        <h1>Privacy notice</h1>
        <p class="lead">What we collect when you use liabletech.com, why, and the choices you have. Last updated 17 October 2026.</p>
      </div>
    </section>
    <section class="section-tight">
      <div class="container prose">
        <h2>Who we are</h2>
        <p>LiableTech ("we") runs liabletech.com. For any privacy question or request, write to
          <a [href]="'mailto:' + email">{{ email }}</a>.</p>

        <h2>What we collect</h2>
        <ul>
          <li><strong>Enquiries:</strong> your name, email, company and message, and the optional answers about your
            project (what you need, stage, timing and budget range).</li>
          <li><strong>Waitlists:</strong> your email, and optionally your name and organisation, for the product you chose.</li>
          <li><strong>Where you came from:</strong> the page you used and, if present, campaign tags in the link you followed
            (such as utm_source), stored with your enquiry so we know which channels work.</li>
          <li><strong>The BazaarFlow demo:</strong> anything you enter in the demo at liabletech.com/ecommerce is visible to
            other demo visitors and deleted every night. Please don't enter real personal details there.</li>
        </ul>
        <p>We do not use advertising or tracking cookies. The site keeps a short note of how you arrived in your browser's
          session storage, which is cleared when you close the tab.</p>

        <h2>Why we use it</h2>
        <p>Only to reply to you, to discuss and provide the services you asked about, and to tell you when a product on
          your waitlist opens. We never sell or rent your details.</p>

        <h2>Where it is stored</h2>
        <p>Website pages are served by Cloudflare. Enquiries are processed on our server in India (Oracle Cloud, Hyderabad)
          and stored in a managed PostgreSQL database hosted by Neon in Singapore. These providers process data on our
          behalf and under their security commitments.</p>

        <h2>How long we keep it</h2>
        <p>Enquiries and waitlist entries are kept for as long as they are useful for the conversation or product they
          relate to, and deleted when you ask us to.</p>

        <h2>Your rights</h2>
        <p>You can ask us to show, correct or delete the personal data we hold about you, or withdraw consent to being
          contacted, by emailing <a [href]="'mailto:' + email">{{ email }}</a>. We respond within 30 days. If you are
          not satisfied with our response, you may raise a grievance with the Data Protection Board of India.</p>

        <h2>Changes</h2>
        <p>If we change this notice, we will update the date at the top of this page.</p>
      </div>
    </section>
  `,
  styles: `
    .prose { max-width: 760px; }
    .prose h2 { font-size: 1.3rem; margin: 32px 0 10px; }
    .prose h2:first-child { margin-top: 0; }
    .prose p, .prose li { color: var(--slate); }
    .prose ul { padding-left: 20px; display: grid; gap: 8px; margin: 0 0 1em; }
  `,
})
export class PrivacyPage {
  protected readonly email = SITE.email;

  constructor() {
    inject(Seo).set({
      title: 'Privacy notice | LiableTech',
      description: 'How LiableTech collects, uses and protects personal data from enquiries, waitlists and the product demo.',
      path: '/privacy',
      crumbs: [{ name: 'Privacy notice', path: '/privacy' }],
    });
  }
}
