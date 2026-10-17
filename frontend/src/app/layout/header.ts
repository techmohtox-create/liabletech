import { afterNextRender, ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { PRODUCTS, SERVICES } from '../core/content';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-header',
  imports: [RouterLink, RouterLinkActive, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="site-header" [class.scrolled]="scrolled()">
      <div class="container bar">
        <a class="logo" routerLink="/" aria-label="LiableTech home">
          <img src="/brand/liabletech-logo-primary.svg" alt="LiableTech" width="150" height="29" />
        </a>

        <nav class="nav" aria-label="Main">
          <ul class="menu">
            <li class="has-sub" [class.open]="openMenu() === 'services'" (mouseleave)="closeMenu()">
              <a routerLink="/services" routerLinkActive="active" class="top">Services</a>
              <button type="button" class="caret" (click)="toggleMenu('services')" [attr.aria-expanded]="openMenu() === 'services'"
                      aria-label="Show services">
                <app-icon name="chevron" [size]="16" />
              </button>
              <div class="sub sub-services">
                @for (s of services; track s.slug) {
                  <a class="sub-item" [routerLink]="['/services', s.slug]" (click)="closeMenu()">
                    <span class="icon-tile sm"><app-icon [name]="s.icon" [size]="20" /></span>
                    <span><strong>{{ s.name }}</strong><small>{{ s.short }}</small></span>
                  </a>
                }
                <a class="sub-all" routerLink="/services" (click)="closeMenu()">All services <app-icon name="arrow" [size]="16" /></a>
              </div>
            </li>
            <li class="has-sub" [class.open]="openMenu() === 'products'" (mouseleave)="closeMenu()">
              <a routerLink="/products" routerLinkActive="active" class="top">Products</a>
              <button type="button" class="caret" (click)="toggleMenu('products')" [attr.aria-expanded]="openMenu() === 'products'"
                      aria-label="Show products">
                <app-icon name="chevron" [size]="16" />
              </button>
              <div class="sub sub-products">
                @for (p of products; track p.slug) {
                  <a class="sub-item" [routerLink]="['/products', p.slug]" (click)="closeMenu()">
                    <span class="icon-tile sm prod"><app-icon [name]="p.slug === 'bazaarflow' ? 'cart' : p.slug === 'erp' ? 'grid' : 'pulse'" [size]="20" /></span>
                    <span>
                      <strong>{{ p.name }}
                        <em class="badge" [class.badge-live]="p.status === 'live'" [class.badge-soon]="p.status === 'soon'">
                          {{ p.status === 'live' ? 'Live demo' : 'Soon' }}</em>
                      </strong>
                      <small>{{ p.category }}</small>
                    </span>
                  </a>
                }
              </div>
            </li>
            <li><a routerLink="/about" routerLinkActive="active" class="top">Approach</a></li>
            <li><a routerLink="/contact" routerLinkActive="active" class="top">Contact</a></li>
          </ul>
        </nav>

        <div class="actions">
          <a class="btn btn-dark btn-sm cta" routerLink="/contact">Discuss your project</a>
          <button type="button" class="burger" (click)="drawer.set(!drawer())" [attr.aria-expanded]="drawer()"
                  aria-controls="mobile-nav" [attr.aria-label]="drawer() ? 'Close menu' : 'Open menu'">
            <app-icon [name]="drawer() ? 'close' : 'menu'" />
          </button>
        </div>
      </div>

      @if (drawer()) {
        <div class="drawer" id="mobile-nav">
          <div class="container">
            <p class="drawer-label">Services</p>
            @for (s of services; track s.slug) {
              <a class="drawer-link" [routerLink]="['/services', s.slug]">
                <app-icon [name]="s.icon" [size]="20" /> {{ s.name }}
              </a>
            }
            <p class="drawer-label">Products</p>
            @for (p of products; track p.slug) {
              <a class="drawer-link" [routerLink]="['/products', p.slug]">{{ p.name }}
                <em class="badge" [class.badge-live]="p.status === 'live'" [class.badge-soon]="p.status === 'soon'">
                  {{ p.status === 'live' ? 'Live demo' : 'Soon' }}</em>
              </a>
            }
            <p class="drawer-label">Company</p>
            <a class="drawer-link" routerLink="/about">Our approach</a>
            <a class="drawer-link" routerLink="/contact">Contact</a>
            <a class="btn btn-primary btn-lg full" routerLink="/contact">Discuss your project</a>
          </div>
        </div>
      }
    </header>
  `,
  styleUrl: './header.scss',
})
export class Header {
  protected readonly services = SERVICES;
  protected readonly products = PRODUCTS;
  protected readonly drawer = signal(false);
  protected readonly openMenu = signal<'services' | 'products' | null>(null);
  protected readonly scrolled = signal(false);

  constructor() {
    const router = inject(Router);
    router.events
      .pipe(filter((event) => event instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => {
        this.drawer.set(false);
        this.openMenu.set(null);
      });

    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const onScroll = () => this.scrolled.set(window.scrollY > 8);
      onScroll();
      window.addEventListener('scroll', onScroll, { passive: true });
      destroyRef.onDestroy(() => window.removeEventListener('scroll', onScroll));
    });
  }

  protected toggleMenu(menu: 'services' | 'products'): void {
    this.openMenu.update((current) => (current === menu ? null : menu));
  }

  protected closeMenu(): void {
    this.openMenu.set(null);
  }
}
