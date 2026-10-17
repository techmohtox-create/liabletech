import { DOCUMENT, inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

import { absoluteUrl, SITE } from './site';

export interface Crumb {
  name: string;
  path: string;
}

export interface PageMeta {
  title: string;
  description: string;
  /** Site path such as "/services/ai-automation"; becomes the canonical URL. */
  path: string;
  image?: string;
  type?: 'website' | 'article' | 'product';
  /** Breadcrumb trail after "Home", rendered as BreadcrumbList structured data. */
  crumbs?: Crumb[];
  /** Extra schema.org objects for this page. */
  schema?: object[];
  noindex?: boolean;
}

/**
 * Writes title, description, canonical, Open Graph, Twitter and JSON-LD tags. Runs during
 * pre-rendering, so every page ships with complete metadata in its static HTML.
 */
@Injectable({ providedIn: 'root' })
export class Seo {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);

  set(page: PageMeta): void {
    const url = absoluteUrl(page.path);
    const image = page.image ?? SITE.ogImage;

    this.title.setTitle(page.title);
    this.tag('name', 'description', page.description);
    this.tag('name', 'robots', page.noindex ? 'noindex, follow' : 'index, follow, max-image-preview:large');
    this.tag('property', 'og:type', page.type ?? 'website');
    this.tag('property', 'og:site_name', SITE.name);
    this.tag('property', 'og:title', page.title);
    this.tag('property', 'og:description', page.description);
    this.tag('property', 'og:url', url);
    this.tag('property', 'og:image', image);
    this.tag('property', 'og:image:width', '1200');
    this.tag('property', 'og:image:height', '630');
    this.tag('property', 'og:locale', 'en_IN');
    this.tag('name', 'twitter:card', 'summary_large_image');
    this.tag('name', 'twitter:title', page.title);
    this.tag('name', 'twitter:description', page.description);
    this.tag('name', 'twitter:image', image);
    this.canonical(url);

    const graph: object[] = [...(page.schema ?? [])];
    if (page.crumbs?.length) {
      graph.push(breadcrumbs(page.crumbs));
    }
    this.jsonLd(graph);
  }

  private tag(attr: 'name' | 'property', key: string, content: string): void {
    this.meta.updateTag({ [attr]: key, content }, `${attr}="${key}"`);
  }

  private canonical(url: string): void {
    let link = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', url);
  }

  private jsonLd(graph: object[]): void {
    this.document.head.querySelector('script#page-schema')?.remove();
    if (!graph.length) {
      return;
    }
    const script = this.document.createElement('script');
    script.id = 'page-schema';
    script.setAttribute('type', 'application/ld+json');
    script.textContent = JSON.stringify({ '@context': 'https://schema.org', '@graph': graph }).replace(/</g, '\\u003c');
    this.document.head.appendChild(script);
  }
}

function breadcrumbs(crumbs: Crumb[]): object {
  const trail = [{ name: 'Home', path: '/' }, ...crumbs];
  return {
    '@type': 'BreadcrumbList',
    itemListElement: trail.map((crumb, index) => ({
      '@type': 'ListItem',
      position: index + 1,
      name: crumb.name,
      item: absoluteUrl(crumb.path),
    })),
  };
}

/** schema.org helpers shared by several pages. */
export const schema = {
  organization: (): object => ({
    '@type': 'Organization',
    '@id': `${SITE.url}/#organization`,
    name: SITE.name,
    url: SITE.url + '/',
    logo: SITE.logo,
    email: SITE.email,
    slogan: SITE.tagline,
    description: SITE.positioning,
    contactPoint: { '@type': 'ContactPoint', contactType: 'sales', email: SITE.email, availableLanguage: ['English'] },
  }),
  website: (): object => ({
    '@type': 'WebSite',
    '@id': `${SITE.url}/#website`,
    url: SITE.url + '/',
    name: SITE.name,
    publisher: { '@id': `${SITE.url}/#organization` },
  }),
  faq: (faqs: { q: string; a: string }[]): object => ({
    '@type': 'FAQPage',
    mainEntity: faqs.map((faq) => ({
      '@type': 'Question',
      name: faq.q,
      acceptedAnswer: { '@type': 'Answer', text: faq.a },
    })),
  }),
  service: (name: string, description: string, path: string): object => ({
    '@type': 'Service',
    name,
    description,
    url: absoluteUrl(path),
    serviceType: name,
    provider: { '@id': `${SITE.url}/#organization` },
    areaServed: ['IN', 'Worldwide'],
  }),
};
