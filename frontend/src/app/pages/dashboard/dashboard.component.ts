import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ClientService, ClientSummaryDto } from '../../generated';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  loading = signal(true);
  clients = signal<ClientSummaryDto[]>([]);
  totalAccounts = signal(0);

  constructor(private clientService: ClientService) {}

  ngOnInit() {
    this.clientService.listClients().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        const total = clients.reduce((sum, c) => sum + (c.accountCount ?? 0), 0);
        this.totalAccounts.set(total);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}
