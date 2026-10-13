import type { LoginResult } from '../../types/auth';
import type { Shipment } from './types';

export function canUpdatePreparation(shipment: Shipment, user: LoginResult | null) {
  return !!user?.permissions.includes('shipping:view') && user.permissions.includes('shipping:prepare')
    && (user.roles.includes('SUPER_ADMIN') || (user.employeeId != null && !!shipment.preparerEmployeeIds?.includes(user.employeeId)));
}

export function shipmentOperatorIdentifier(user: LoginResult | null) {
  return user?.mobile?.trim() || (user?.employeeId != null && user.employeeId > 0 ? `employee:${user.employeeId}` : '');
}

export function canEditPreparationContent(shipment: Shipment, user: LoginResult | null) {
  if (!user?.permissions.includes('shipping:view') || shipment.content.status === 'completed') return false;
  if (user.employeeId == null || user.employeeId <= 0) return false;
  return shipment.creatorEmployeeId != null ? shipment.creatorEmployeeId === user.employeeId
    : shipment.createdBy === `employee:${user.employeeId}`;
}

export function canConfirmCreator(shipment: Shipment, user: LoginResult | null) {
  return shipment.creatorEmployeeId == null && !/^employee:[1-9]\d*$/.test(shipment.createdBy)
    && !!user?.roles.includes('SUPER_ADMIN') && user.permissions.includes('shipping:view') && user.permissions.includes('shipping:edit');
}
