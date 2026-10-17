import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BAZAARFLOW_MODULES, DEMO_LOGINS, Faq, PRODUCTS } from '../core/content';
import { schema, Seo } from '../core/seo.service';
import { SITE } from '../core/site';
import { CtaBand } from '../shared/cta-band';
import { FaqList } from '../shared/faq';
import { Icon } from '../shared/icon';
import { Reveal } from '../shared/reveal';

const FAQS: Faq[] = [
  {
    q: 'Is the demo data real?',
    a: 'No. The demo store, Fieldstone Agro, is fictional and its data resets every night. Feel free to place orders, raise tickets and try every role.',
  },
  {
    q: 'Can we use our own payment gateway and courier?',
    a: 'Yes. Payments, couriers and notifications sit behind adapters, so connecting your gateway, courier partner, SMS or WhatsApp provider is a contained piece of work. The demo uses mock providers.',
  },
  {
    q: 'Can it connect to our ERP or accounting software?',
    a: 'Yes. BazaarFlow exposes documented APIs, and we build the integration to your ERP or accounting system as part of the setup.',
  },
  {
    q: 'Where is it hosted, and who owns the data?',
    a: 'Either we run it for you, or we deploy it in your own cloud account. In both cases the data is yours, and you can export it at any time.',
  },
  {
    q: 'Does it handle GST correctly?',
    a: 'GST is computed per line and split into CGST/SGST or IGST by place of supply, with invoice numbering per Indian financial year. These rules are covered by automated tests.',
  },
];

@Component({
  selector: 'app-bazaarflow-page',
  imports: [RouterLink, Icon, FaqList, CtaBand, Reveal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './bazaarflow.page.html',
  styleUrl: './bazaarflow.page.scss',
})
export class BazaarflowPage {
  protected readonly product = PRODUCTS.find((p) => p.slug === 'bazaarflow')!;
  protected readonly modules = BAZAARFLOW_MODULES;
  protected readonly logins = DEMO_LOGINS;
  protected readonly faqs = FAQS;
  protected readonly moduleId = signal<string>(BAZAARFLOW_MODULES[0].id);
  protected readonly module = computed(() => BAZAARFLOW_MODULES.find((m) => m.id === this.moduleId()) ?? BAZAARFLOW_MODULES[0]);
  protected readonly copied = signal('');

  protected readonly options = [
    {
      title: 'Managed by us',
      text: 'We host, monitor, back up and update BazaarFlow for you. You run your business; we keep the platform running.',
      points: ['Your brand, domain and catalogue', 'Payment gateway and courier connected', 'Monitoring, backups and updates included'],
    },
    {
      title: 'In your own cloud',
      text: 'We deploy BazaarFlow in your cloud account and hand over the keys, with optional support afterwards.',
      points: ['Full control of data and infrastructure', 'Deployment and handover documentation', 'Optional managed support plan'],
    },
    {
      title: 'Customised for you',
      text: 'Start from BazaarFlow and we adapt the workflows, roles and integrations to how your team actually works.',
      points: ['Custom workflows and reports', 'ERP, CRM and accounting integrations', 'Built by the team that built the platform'],
    },
  ];

  protected readonly facts = [
    { k: 'GST', v: 'CGST/SGST or IGST by place of supply' },
    { k: '8', v: 'staff roles with a permission matrix' },
    { k: '8', v: 'business reports with CSV export' },
    { k: '30', v: 'automated tests on money and order rules' },
  ];

  constructor() {
    const p = this.product;
    inject(Seo).set({
      title: p.seoTitle,
      description: p.seoDescription,
      path: '/products/bazaarflow',
      type: 'product',
      crumbs: [
        { name: 'Products', path: '/products' },
        { name: 'BazaarFlow', path: '/products/bazaarflow' },
      ],
      schema: [
        {
          '@type': 'SoftwareApplication',
          name: 'BazaarFlow',
          applicationCategory: 'BusinessApplication',
          applicationSubCategory: 'E-commerce platform',
          operatingSystem: 'Web browser',
          description: p.summary,
          url: `${SITE.url}/products/bazaarflow`,
          featureList: p.scope,
          publisher: { '@id': `${SITE.url}/#organization` },
        },
        schema.faq(FAQS),
      ],
    });
  }

  protected async copy(text: string, key: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(key);
      setTimeout(() => this.copied.set(''), 1600);
    } catch {
      // clipboard blocked: the value is still visible to copy by hand
    }
  }
}
