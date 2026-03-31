import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TransferService, TransferRequest, TransferResultDto } from '../../generated';

@Component({
  selector: 'app-transfer',
  imports: [
    FormsModule,
    MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule
  ],
  templateUrl: './transfer.component.html',
  styleUrl: './transfer.component.scss'
})
export class TransferComponent {
  request: TransferRequest = {
    fromAccountId: 0,
    toAccountId: 0,
    amount: 0,
    currency: 'HUF'
  };

  result = signal<TransferResultDto | null>(null);
  error = signal<string | null>(null);
  loading = signal(false);

  constructor(private transferService: TransferService) {}

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
