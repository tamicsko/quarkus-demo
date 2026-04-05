import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-new-account-dialog',
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'newAccount.title' | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'newAccount.accountNumber' | translate }}</mat-label>
        <input matInput [(ngModel)]="accountNumber" required>
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'newAccount.accountType' | translate }}</mat-label>
        <mat-select [(ngModel)]="accountType">
          <mat-option value="CHECKING">{{ 'account.checking' | translate }}</mat-option>
          <mat-option value="SAVINGS">{{ 'account.savings' | translate }}</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'newAccount.currency' | translate }}</mat-label>
        <mat-select [(ngModel)]="currency">
          <mat-option value="HUF">HUF</mat-option>
          <mat-option value="EUR">EUR</mat-option>
          <mat-option value="USD">USD</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'newAccount.initialBalance' | translate }}</mat-label>
        <input matInput type="number" [(ngModel)]="initialBalance" min="0">
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'newAccount.cancel' | translate }}</button>
      <button mat-flat-button color="primary" [disabled]="!accountNumber" (click)="confirm()">
        {{ 'newAccount.open' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; } mat-dialog-content { min-width: 350px; }`]
})
export class NewAccountDialogComponent {
  accountNumber = '';
  accountType = 'CHECKING';
  currency = 'HUF';
  initialBalance: number | null = 0;

  constructor(public dialogRef: MatDialogRef<NewAccountDialogComponent>) {}

  confirm() {
    if (this.accountNumber) {
      this.dialogRef.close({
        accountNumber: this.accountNumber,
        accountType: this.accountType,
        currency: this.currency,
        initialBalance: this.initialBalance || 0
      });
    }
  }
}
