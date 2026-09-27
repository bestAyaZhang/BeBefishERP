import { createService } from '../../../services/serviceFactory';
import { httpStocktakeService } from './httpStocktakeService';
import { mockStocktakeService } from './mockStocktakeService';

export const stocktakeService = createService(
  () => mockStocktakeService,
  () => httpStocktakeService
);
