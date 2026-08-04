import { createService } from '../../services/serviceFactory';
import { httpInventoryService } from './httpInventoryService';
import { mockInventoryService } from './mockInventoryService';

export const inventoryService = createService(
  () => mockInventoryService,
  () => httpInventoryService
);
