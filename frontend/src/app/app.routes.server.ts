import { RenderMode, ServerRoute } from '@angular/ssr';

import { PRODUCTS, SERVICES } from './core/content';

/** Every page is pre-rendered to static HTML at build time; nothing renders on a server at runtime. */
export const serverRoutes: ServerRoute[] = [
  {
    path: 'services/:slug',
    renderMode: RenderMode.Prerender,
    getPrerenderParams: async () => SERVICES.map((s) => ({ slug: s.slug })),
  },
  {
    path: 'products/:slug',
    renderMode: RenderMode.Prerender,
    getPrerenderParams: async () => PRODUCTS.filter((p) => p.status === 'soon').map((p) => ({ slug: p.slug })),
  },
  { path: '**', renderMode: RenderMode.Prerender },
];
