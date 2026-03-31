import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'clients',
    loadComponent: () => import('./pages/client-list/client-list.component').then(m => m.ClientListComponent)
  },
  {
    path: 'clients/:id',
    loadComponent: () => import('./pages/client-detail/client-detail.component').then(m => m.ClientDetailComponent)
  },
  {
    path: 'transfer',
    loadComponent: () => import('./pages/transfer/transfer.component').then(m => m.TransferComponent)
  },
  {
    path: 'statement/:id',
    loadComponent: () => import('./pages/statement/statement.component').then(m => m.StatementComponent)
  }
];
