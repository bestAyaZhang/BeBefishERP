import { createService } from '../../services/serviceFactory';
import { httpSalesOrderService } from './httpSalesOrderService';
import { mockSalesOrderService } from './mockSalesOrderService';

export const salesOrderService = createService(() => mockSalesOrderService, () => httpSalesOrderService);
