import { getMockNavigationCatalog } from './mockNavigationService';
import type { NavigationCatalog } from './types';

export interface NavigationService {
  getCatalog(): NavigationCatalog;
}

export const navigationService: NavigationService = {
  getCatalog: getMockNavigationCatalog
};
