export type OrganizationRecordStatus = 'enabled' | 'disabled';
export type EmployeeStatus = 'active' | 'disabled' | 'resigned';
export type EmploymentType = 'formal' | 'temporary';
export type FeishuBindingStatus = 'bound' | 'pending' | 'unbound';

export interface Department {
  id: number;
  departmentCode: string;
  departmentName: string;
  parentId: number | null;
  managerEmployeeId: number | null;
  managerName: string;
  sortOrder: number;
  status: OrganizationRecordStatus;
}

export interface DepartmentTreeNode extends Department {
  children: DepartmentTreeNode[];
}

export interface Position {
  id: number;
  positionCode: string;
  positionName: string;
  departmentId: number;
  responsibilities: string;
  status: OrganizationRecordStatus;
  employeeCount: number;
}

export interface DepartmentListItem extends Department {
  employeeCount: number;
  childCount: number;
  statusActionDisabled: boolean;
}

export interface Employee {
  id: number;
  employeeNo: string;
  employeeName: string;
  mobile: string;
  departmentId: number;
  positionId: number;
  employmentType: EmploymentType;
  status: EmployeeStatus;
  hireDate: string;
  feishuBindingStatus: FeishuBindingStatus;
  feishuDisplayName: string;
  passwordLoginEnabled: boolean;
}

export interface PageResult<T> {
  records: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface EmployeeQuery {
  page: number;
  size: number;
  keyword?: string;
  departmentId?: number;
  employmentType?: EmploymentType;
  status?: EmployeeStatus;
}

export interface PositionQuery {
  page: number;
  size: number;
  keyword?: string;
  departmentId?: number;
  status?: OrganizationRecordStatus;
}

export interface DepartmentQuery {
  page: number;
  size: number;
  keyword?: string;
  status?: OrganizationRecordStatus;
}

export interface OrganizationSummary {
  formalEmployees: number;
  temporaryEmployees: number;
  pendingFeishuBindings: number;
  disabledAccounts: number;
}

export interface SaveEmployeePayload extends Omit<Employee, 'id' | 'feishuDisplayName' | 'feishuBindingStatus'> {
  password?: string;
}

export type SaveDepartmentPayload = Omit<Department, 'id' | 'managerName'>;
export type SavePositionPayload = Omit<Position, 'id' | 'employeeCount'>;
