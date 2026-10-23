import { Routes } from '@angular/router';
import { ownerOnly, signedIn, signedOut } from './core/guards';
import { Shell } from './layout/shell';

export const routes: Routes = [
  { path: 'login', canActivate: [signedOut], loadComponent: () => import('./pages/login').then((m) => m.LoginPage), title: 'Sign in · PayoutCheck' },
  { path: 'signup', canActivate: [signedOut], loadComponent: () => import('./pages/signup').then((m) => m.SignupPage), title: 'Start free · PayoutCheck' },
  {
    path: '',
    component: Shell,
    canActivate: [signedIn],
    children: [
      { path: '', loadComponent: () => import('./pages/dashboard').then((m) => m.DashboardPage), title: 'Dashboard · PayoutCheck' },
      { path: 'findings', loadComponent: () => import('./pages/findings').then((m) => m.FindingsPage), title: 'Disputes · PayoutCheck' },
      { path: 'statements', loadComponent: () => import('./pages/statements').then((m) => m.StatementsPage), title: 'Statements · PayoutCheck' },
      { path: 'statements/:id', loadComponent: () => import('./pages/statement-detail').then((m) => m.StatementDetailPage), title: 'Statement · PayoutCheck' },
      { path: 'orders', loadComponent: () => import('./pages/orders').then((m) => m.OrdersPage), title: 'Orders · PayoutCheck' },
      { path: 'upload', loadComponent: () => import('./pages/upload').then((m) => m.UploadPage), title: 'Upload files · PayoutCheck' },
      { path: 'settings/outlets', loadComponent: () => import('./pages/outlets').then((m) => m.OutletsPage), title: 'Outlets & terms · PayoutCheck' },
      { path: 'settings/team', loadComponent: () => import('./pages/team').then((m) => m.TeamPage), title: 'Team · PayoutCheck' },
      { path: 'settings/plan', loadComponent: () => import('./pages/plan').then((m) => m.PlanPage), title: 'Plan · PayoutCheck' },
      { path: 'settings/audit', canActivate: [ownerOnly], loadComponent: () => import('./pages/audit').then((m) => m.AuditPage), title: 'Audit log · PayoutCheck' },
    ],
  },
  { path: '**', redirectTo: '' },
];
