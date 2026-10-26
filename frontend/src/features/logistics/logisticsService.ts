import { createService } from '../../services/serviceFactory';
import { httpLogisticsService } from './httpLogisticsService';
import { createMockLogisticsService } from './mockLogisticsService';
export const logisticsService = createService(createMockLogisticsService, () => httpLogisticsService);
