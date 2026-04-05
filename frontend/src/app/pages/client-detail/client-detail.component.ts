import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { ClientService, AccountService, ClientDetailDto, AccountInfoDto } from '../../generated';
import { DepositDialogComponent, BalanceDialogData } from './deposit-dialog.component';
import { NewAccountDialogComponent } from './new-account-dialog.component';

@Component({
  selector: 'app-client-detail',
  imports: [
    RouterLink, DatePipe, DecimalPipe, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatTableModule, MatChipsModule,
    MatDialogModule, MatFormFieldModule, MatInputModule, MatSnackBarModule, MatTooltipModule,
    TranslateModule
  ],
  templateUrl: './client-detail.component.html',
  styleUrl: './client-detail.component.scss'
})
export class ClientDetailComponent implements OnInit {
  client = signal<ClientDetailDto | null>(null);
  accountColumns = ['id', 'accountNumber', 'type', 'balance', 'currency', 'status', 'actions'];

  constructor(
    private route: ActivatedRoute,
    private clientService: ClientService,
    private accountService: AccountService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit() {
    this.loadClient();
  }

  loadClient() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.clientService.getClientDetail(id).subscribe(detail => this.client.set(detail));
  }

  openDepositDialog(account: AccountInfoDto) {
    this.openBalanceDialog(account, 'deposit');
  }

  openWithdrawDialog(account: AccountInfoDto) {
    this.openBalanceDialog(account, 'withdraw');
  }

  openNewAccountDialog() {
    const dialogRef = this.dialog.open(NewAccountDialogComponent, { width: '450px' });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        const clientId = this.route.snapshot.paramMap.get('id')!;
        this.clientService.openAccount(clientId, result).subscribe({
          next: () => {
            this.snackBar.open('Account opened successfully!', 'OK', { duration: 3000 });
            this.loadClient();
          },
          error: (err) => {
            this.snackBar.open(`Error: ${err.error?.message || 'Failed'}`, 'OK', { duration: 5000 });
          }
        });
      }
    });
  }

  private openBalanceDialog(account: AccountInfoDto, mode: 'deposit' | 'withdraw') {
    const dialogRef = this.dialog.open(DepositDialogComponent, {
      width: '400px',
      data: { account, mode } as BalanceDialogData
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        const op = mode === 'deposit'
          ? this.accountService.depositToAccount(account.id!, { amount: result.amount, reason: result.reason })
          : this.accountService.withdrawFromAccount(account.id!, { amount: result.amount, reason: result.reason });

        op.subscribe({
          next: () => {
            const label = mode === 'deposit' ? 'befizetés' : 'kifizetés';
            this.snackBar.open(
              `${result.amount.toLocaleString()} ${account.currency} ${label} sikeres!`,
              'OK', { duration: 3000 }
            );
            this.loadClient();
          },
          error: (err) => {
            this.snackBar.open(
              `Hiba: ${err.error?.message || 'Művelet sikertelen'}`,
              'OK', { duration: 5000 }
            );
          }
        });
      }
    });
  }
}
