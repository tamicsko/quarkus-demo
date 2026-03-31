import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { StatementService, AccountStatementDto } from '../../generated';

@Component({
  selector: 'app-statement',
  imports: [
    DatePipe, DecimalPipe,
    MatCardModule, MatTableModule, MatIconModule, MatButtonModule, MatChipsModule
  ],
  templateUrl: './statement.component.html',
  styleUrl: './statement.component.scss'
})
export class StatementComponent implements OnInit {
  statement = signal<AccountStatementDto | null>(null);
  txColumns = ['direction', 'counterparty', 'amount', 'currency', 'status', 'date'];

  constructor(
    private route: ActivatedRoute,
    private statementService: StatementService
  ) {}

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.statementService.getAccountStatement(id).subscribe(s => this.statement.set(s));
  }
}
