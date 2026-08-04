import { createService } from '../../services/serviceFactory';
import { httpMasterdataService } from './httpMasterdataService';
import { mockMasterdataService } from './mockMasterdataService';
import type { MasterdataService } from './types';

export const masterdataService: MasterdataService = createService(
  () => mockMasterdataService,
  () => httpMasterdataService
);
