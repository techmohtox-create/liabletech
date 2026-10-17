import { Routes, UrlSegment } from '@angular/router';

import { PRODUCTS, SERVICES } from './core/content';

const knownService = (_: unknown, segments: UrlSegment[]) => SERVICES.some((s) => s.slug === segments[1]?.path);
const upcomingProduct = (_: unknown, segments: UrlSegment[]) =>
  PRODUCTS.some((p) => p.status === 'soon' && p.slug === segments[1]?.path);

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/home.page').then((m) => m.HomePage) },
  { path: 'services', loadComponent: () => import('./pages/services.page').then((m) => m.ServicesPage) },
  {
    path: 'services/:slug',
    canMatch: [knownService],
    loadComponent: () => import('./pages/service-detail.page').then((m) => m.ServiceDetailPage),
  },
  { path: 'products', loadComponent: () => import('./pages/products.page').then((m) => m.ProductsPage) },
  { path: 'products/bazaarflow', loadComponent: () => import('./pages/bazaarflow.page').then((m) => m.BazaarflowPage) },
  {
    path: 'products/:slug',
    canMatch: [upcomingProduct],
    loadComponent: () => import('./pages/coming-soon.page').then((m) => m.ComingSoonPage),
  },
  { path: 'about', loadComponent: () => import('./pages/about.page').then((m) => m.AboutPage) },
  { path: 'contact', loadComponent: () => import('./pages/contact.page').then((m) => m.ContactPage) },
  { path: 'privacy', loadComponent: () => import('./pages/privacy.page').then((m) => m.PrivacyPage) },
  { path: '404', loadComponent: () => import('./pages/not-found.page').then((m) => m.NotFoundPage) },
  { path: '**', loadComponent: () => import('./pages/not-found.page').then((m) => m.NotFoundPage) },
];
