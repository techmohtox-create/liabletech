import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ENGAGEMENTS, HOME_FAQS, PRINCIPLES, PROBLEMS, PROCESS, PRODUCTS, SERVICES } from '../core/content';
import { schema, Seo } from '../core/seo.service';
import { CtaBand } from '../shared/cta-band';
import { FaqList } from '../shared/faq';
import { HeroArt } from '../shared/hero-art';
import { Icon } from '../shared/icon';
import { Reveal } from '../shared/reveal';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, Icon, HeroArt, FaqList, CtaBand, Reveal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.page.html',
  styleUrl: './home.page.scss',
})
export class HomePage {
  protected readonly services = SERVICES;
  protected readonly products = PRODUCTS;
  protected readonly problems = PROBLEMS;
  protected readonly process = PROCESS;
  protected readonly principles = PRINCIPLES;
  protected readonly engagements = ENGAGEMENTS;
  protected readonly faqs = HOME_FAQS;
  protected readonly stack = ['Java · Spring Boot', 'Angular', 'Google Cloud', 'AI workflows', 'SRE automation'];

  protected readonly problemId = signal(PROBLEMS[0].id);
  protected readonly problem = computed(() => PROBLEMS.find((p) => p.id === this.problemId()) ?? PROBLEMS[0]);
  protected readonly problemTarget = computed(() => {
    const p = this.problem();
    if (p.productSlug) {
      const product = PRODUCTS.find((x) => x.slug === p.productSlug)!;
      return { link: ['/products', product.slug], label: `Explore ${product.name}`, interest: product.slug };
    }
    const service = SERVICES.find((x) => x.slug === p.serviceSlug)!;
    return { link: ['/services', service.slug], label: `More about ${service.phrase}`, interest: service.slug };
  });

  protected readonly step = signal(0);

  constructor() {
    inject(Seo).set({
      title: 'LiableTech | AI automation, cloud & software engineering',
      description:
        'LiableTech builds reliable software, modernises cloud applications and uses AI automation to solve real operational problems. Try our live product demo.',
      path: '/',
      schema: [schema.organization(), schema.website(), schema.faq(HOME_FAQS)],
    });
  }
}
