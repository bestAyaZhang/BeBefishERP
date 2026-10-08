import type { LoginResult } from '../../types/auth';
import type { Shipment } from './types';

export function canUpdatePreparation(shipment: Shipment, user: LoginResult | null) {
  return !!user?.permissions.includes('shipping:view') && user.permissions.includes('shipping:prepare')
    && (user.roles.includes('SUPER_ADMIN') || (user.employeeId != null && !!shipment.preparerEmployeeIds?.includes(user.employeeId)));
}
