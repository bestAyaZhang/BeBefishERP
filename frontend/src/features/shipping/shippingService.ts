import { createService } from '../../services/serviceFactory';
import { httpShippingService } from './httpShippingService';
import { createMockShippingService } from './mockShippingService';

export const shippingService = createService(createMockShippingService, () => httpShippingService);
