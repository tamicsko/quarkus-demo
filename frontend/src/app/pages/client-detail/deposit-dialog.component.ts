import { Component, Inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';
import { AccountInfoDto } from '../../generated';

export interface BalanceDialogData {
  account: AccountInfoDto;
  mode: 'deposit' | 'withdraw';
}

@Component({
  selector: 'app-balance-dialog',
  imports: [DecimalPipe, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ data.mode === 'deposit' ? ('deposit.title' | translate) : ('deposit.withdraw.title' | translate) }}</h2>
    <mat-dialog-content>
      <p>{{ 'deposit.account' | translate }}: <strong>{{ data.account.accountNumber }}</strong> ({{ data.account.currency }})</p>
      <p>{{ 'deposit.currentBalance' | translate }}: <strong>{{ data.account.balance | number:'1.2-2' }} {{ data.account.currency }}</strong></p>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'deposit.amount' | translate }} ({{ data.account.currency }})</mat-label>
        <input matInput type="number" [(ngModel)]="amount" min="0.01" step="1000" required>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'deposit.reason' | translate }}</mat-label>
        <input matInput [(ngModel)]="reason"
               [placeholder]="data.mode === 'deposit' ? ('deposit.reasonPlaceholder' | translate) : ('deposit.withdraw.reasonPlaceholder' | translate)">
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'deposit.cancel' | translate }}</button>
      <button mat-flat-button
              [color]="data.mode === 'deposit' ? 'primary' : 'warn'"
              [disabled]="!amount || amount <= 0"
              (click)="confirm()">
        {{ data.mode === 'deposit' ? ('deposit.submit' | translate) : ('deposit.withdraw.submit' | translate) }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; } mat-dialog-content { min-width: 300px; }`]
})
export class DepositDialogComponent {
  amount: number | null = null;
  reason = '';

  constructor(
    public dialogRef: MatDialogRef<DepositDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: BalanceDialogData
  ) {}

  confirm() {
    if (this.amount && this.amount > 0) {
      this.dialogRef.close({ amount: this.amount, reason: this.reason || undefined });
    }
  }
}
