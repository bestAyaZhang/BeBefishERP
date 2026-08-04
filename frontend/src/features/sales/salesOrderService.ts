import { httpSalesOrderService } from './httpSalesOrderService';
import { mockSalesOrderService } from './mockSalesOrderService';

// Keep local development usable with Mock while allowing an explicit real data source
// to exercise the sales draft API.
const useMockSalesOrderService = !import.meta.env.PROD && import.meta.env.VITE_DATA_SOURCE !== 'real';

export const salesOrderService = useMockSalesOrderService ? mockSalesOrderService : httpSalesOrderService;
