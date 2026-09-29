import { createService } from '../../services/serviceFactory';
import { httpPlatformService } from './httpPlatformService';
import { createMockPlatformService } from './mockPlatformService';
export const platformService = createService(createMockPlatformService, () => httpPlatformService);
