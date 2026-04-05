import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { StatementService, AccountStatementDto } from '../../generated';

@Component({
  selector: 'app-statement',
  imports: [
    DatePipe, DecimalPipe, RouterLink,
    MatCardModule, MatTableModule, MatIconModule, MatButtonModule, MatChipsModule,
    TranslateModule
  ],
  templateUrl: './statement.component.html',
  styleUrl: './statement.component.scss'
})
export class StatementComponent implements OnInit {
  statement = signal<AccountStatementDto | null>(null);
  txColumns = ['direction', 'counterparty', 'amount', 'currency', 'status', 'date'];
  clientId: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private statementService: StatementService,
    private translate: TranslateService
  ) {}

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.clientId = this.route.snapshot.queryParamMap.get('clientId') || null;
    this.statementService.getAccountStatement(id).subscribe(s => this.statement.set(s));
  }

  directionIcon(dir: string): string {
    switch (dir) {
      case 'INCOMING': return 'arrow_downward';
      case 'OUTGOING': return 'arrow_upward';
      case 'DEPOSIT': return 'add_circle';
      case 'WITHDRAWAL': return 'remove_circle';
      default: return 'help';
    }
  }

  directionClass(dir: string): string {
    return this.isCredit(dir) ? 'incoming' : 'outgoing';
  }

  directionLabel(dir: string): string {
    return this.translate.instant('direction.' + dir);
  }

  isCredit(dir: string): boolean {
    return dir === 'INCOMING' || dir === 'DEPOSIT';
  }
}
