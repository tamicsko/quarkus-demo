export * from './client.service';
import { ClientService } from './client.service';
export * from './statement.service';
import { StatementService } from './statement.service';
export * from './transfer.service';
import { TransferService } from './transfer.service';
export const APIS = [ClientService, StatementService, TransferService];
