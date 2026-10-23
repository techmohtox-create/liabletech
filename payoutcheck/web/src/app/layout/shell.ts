import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Session } from '../core/session';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly session = inject(Session);
  protected readonly menuOpen = signal(false);

  protected readonly nav = [
    { path: '/', icon: 'dashboard', label: 'Dashboard', exact: true },
    { path: '/findings', icon: 'findings', label: 'Disputes', exact: false },
    { path: '/statements', icon: 'statements', label: 'Statements', exact: false },
    { path: '/orders', icon: 'orders', label: 'Orders', exact: false },
    { path: '/upload', icon: 'upload', label: 'Upload files', exact: false },
  ];

  protected readonly settings = [
    { path: '/settings/outlets', icon: 'outlet', label: 'Outlets & terms', owner: false },
    { path: '/settings/team', icon: 'team', label: 'Team', owner: false },
    { path: '/settings/plan', icon: 'plan', label: 'Plan', owner: false },
    { path: '/settings/audit', icon: 'audit', label: 'Audit log', owner: true },
  ];

  constructor() {
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => this.menuOpen.set(false));
  }

  protected chooseOutlet(value: string): void {
    this.session.selectOutlet(value ? Number(value) : null);
  }
}
