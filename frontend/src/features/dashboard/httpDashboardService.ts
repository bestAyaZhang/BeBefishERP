import { request } from '../../services/http';
import type { DashboardService } from './types';

export const httpDashboardService: DashboardService = {
  getOverview: (period) => {
    const params = new URLSearchParams({ period });
    return request(`/api/dashboard/overview?${params.toString()}`);
  }
};
