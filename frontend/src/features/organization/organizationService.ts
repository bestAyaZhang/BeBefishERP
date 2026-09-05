import { createService } from '../../services/serviceFactory';
import { httpOrganizationService } from './httpOrganizationService';
import { createMockOrganizationService } from './mockOrganizationService';
import type {
  FeishuSyncTask,
  Department,
  DepartmentListItem,
  DepartmentQuery,
  Employee,
  EmployeeQuery,
  EmployeeStatus,
  OrganizationRecordStatus,
  OrganizationSummary,
  PageResult,
  Position,
  PositionQuery,
  SaveDepartmentPayload,
  SaveEmployeePayload,
  SavePositionPayload
} from './types';

export interface OrganizationService {
  startFeishuSync(): Promise<FeishuSyncTask>;
  getLatestFeishuSync(): Promise<FeishuSyncTask | null>;
  getFeishuSync(id: number): Promise<FeishuSyncTask>;
  listDepartmentPage(query: DepartmentQuery): Promise<PageResult<DepartmentListItem>>;
  listAllDepartments(): Promise<Department[]>;
  getDepartmentEmployeeCounts(): Promise<Record<number, number>>;
  listPositions(query: PositionQuery): Promise<PageResult<Position>>;
  listAllPositions(): Promise<Position[]>;
  listEmployees(query: EmployeeQuery): Promise<PageResult<Employee>>;
  listAllEmployees(): Promise<Employee[]>;
  getSummary(): Promise<OrganizationSummary>;
  getEmployee(id: number): Promise<Employee>;
  createEmployee(payload: SaveEmployeePayload): Promise<Employee>;
  updateEmployee(id: number, payload: SaveEmployeePayload): Promise<Employee>;
  changeEmployeeStatus(id: number, status: EmployeeStatus): Promise<Employee>;
  createDepartment(payload: SaveDepartmentPayload): Promise<Department>;
  updateDepartment(id: number, payload: SaveDepartmentPayload): Promise<Department>;
  changeDepartmentStatus(id: number, status: OrganizationRecordStatus): Promise<Department>;
  createPosition(payload: SavePositionPayload): Promise<Position>;
  updatePosition(id: number, payload: SavePositionPayload): Promise<Position>;
  changePositionStatus(id: number, status: OrganizationRecordStatus): Promise<Position>;
}

export const organizationService: OrganizationService = createService(createMockOrganizationService, () => httpOrganizationService);
