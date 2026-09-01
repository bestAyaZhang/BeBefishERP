import { createService } from '../../services/serviceFactory';
import { httpDashboardService } from './httpDashboardService';
import { mockDashboardService } from './mockDashboardService';
import type { DashboardService } from './types';

export const dashboardService: DashboardService = createService(
  () => mockDashboardService,
  () => httpDashboardService
);
