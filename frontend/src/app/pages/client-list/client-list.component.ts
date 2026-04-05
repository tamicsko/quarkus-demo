import { Component, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { ClientService, ClientSummaryDto, RegisterClientRequest } from '../../generated';

@Component({
  selector: 'app-client-list',
  imports: [
    RouterLink, FormsModule,
    MatTableModule, MatButtonModule, MatIconModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatChipsModule, MatTooltipModule,
    TranslateModule
  ],
  templateUrl: './client-list.component.html',
  styleUrl: './client-list.component.scss'
})
export class ClientListComponent implements OnInit {
  clients = signal<ClientSummaryDto[]>([]);
  displayedColumns = ['id', 'name', 'taxId', 'email', 'status', 'accounts', 'actions'];

  showForm = signal(false);
  newClient: RegisterClientRequest = {
    taxId: '', firstName: '', lastName: '', email: '',
    accountNumber: '', accountType: 'CHECKING', currency: 'HUF'
  };

  constructor(private clientService: ClientService, private router: Router) {}

  ngOnInit() {
    this.loadClients();
  }

  loadClients() {
    this.clientService.listClients().subscribe(clients => this.clients.set(clients));
  }

  register() {
    this.clientService.registerClient(this.newClient).subscribe({
      next: (detail) => {
        this.showForm.set(false);
        this.loadClients();
        this.newClient = {
          taxId: '', firstName: '', lastName: '', email: '',
          accountNumber: '', accountType: 'CHECKING', currency: 'HUF'
        };
      },
      error: (err) => alert('Error: ' + (err.error?.message || err.message))
    });
  }
}
