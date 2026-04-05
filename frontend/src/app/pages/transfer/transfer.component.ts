import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import {
  TransferService, ClientService, TransferRequest, TransferResultDto,
  ClientDetailDto, AccountInfoDto
} from '../../generated';

interface AccountOption {
  accountId: number;
  label: string;
  currency: string;
}

@Component({
  selector: 'app-transfer',
  imports: [
    FormsModule,
    MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    TranslateModule
  ],
  templateUrl: './transfer.component.html',
  styleUrl: './transfer.component.scss'
})
export class TransferComponent implements OnInit {
  request: TransferRequest = {
    fromAccountId: 0,
    toAccountId: 0,
    amount: 0,
    currency: 'HUF'
  };

  accounts = signal<AccountOption[]>([]);
  result = signal<TransferResultDto | null>(null);
  error = signal<string | null>(null);
  loading = signal(false);

  constructor(
    private transferService: TransferService,
    private clientService: ClientService
  ) {}

  ngOnInit() {
    this.clientService.listClients().subscribe(clients => {
      const opts: AccountOption[] = [];
      for (const c of clients) {
        this.clientService.getClientDetail(c.id!).subscribe(detail => {
          for (const a of detail.accounts ?? []) {
            opts.push({
              accountId: a.id!,
              label: `${detail.firstName} ${detail.lastName} — ${a.accountNumber} (${a.currency}, ${a.accountType})`,
              currency: a.currency ?? 'HUF'
            });
          }
          this.accounts.set([...opts].sort((a, b) => a.label.localeCompare(b.label)));
        });
      }
    });
  }

  onFromChange() {
    const from = this.accounts().find(a => a.accountId === this.request.fromAccountId);
    if (from) {
      this.request.currency = from.currency;
    }
  }

  submit() {
    this.loading.set(true);
    this.result.set(null);
    this.error.set(null);

    this.transferService.initiateTransfer(this.request).subscribe({
      next: (res) => {
        this.result.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message || err.message);
        this.loading.set(false);
      }
    });
  }
}
