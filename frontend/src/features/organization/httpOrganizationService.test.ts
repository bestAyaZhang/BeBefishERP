import { afterEach, describe, expect, it, vi } from 'vitest';
import { httpOrganizationService as service } from './httpOrganizationService';

afterEach(() => vi.unstubAllGlobals());
describe('organization HTTP contract', () => {
  it('sends filters and nullable employee mutations to the real API', async () => {
    const fetcher = vi.fn().mockImplementation(async () => new Response(JSON.stringify({ code: 'SUCCESS', data: null })));
    vi.stubGlobal('fetch', fetcher);
    await service.listEmployees({ page: 2, size: 10, keyword: '张', departmentId: 4, status: 'active', employmentType: 'formal' });
    expect(fetcher.mock.calls[0][0]).toBe('/api/organization/employees?page=2&size=10&keyword=%E5%BC%A0&departmentId=4&status=active&employmentType=formal');
    const payload = { employeeNo: 'E1', employeeName: '张', mobile: '', departmentId: null, positionId: null, hireDate: null, status: 'active' as const, employmentType: 'formal' as const, passwordLoginEnabled: false };
    await service.updateEmployee(7, payload);
    expect(fetcher.mock.calls[1]).toEqual(['/api/organization/employees/7', expect.objectContaining({ method: 'PUT', body: JSON.stringify(payload) })]);
    expect(await service.getLatestFeishuSync()).toBeNull();
  });
  it('never substitutes fixtures when the backend is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
    await expect(service.listEmployees({ page: 1, size: 20 })).rejects.toThrow('接口服务暂不可用');
  });
});
it('maps every organization route and mutation without adding fallback data', async () => {
 const fetcher = vi.fn().mockImplementation(async () => new Response(JSON.stringify({code:'SUCCESS',data:null})));
 vi.stubGlobal('fetch',fetcher);
 await service.listDepartmentPage({page:1,size:20,keyword:'',status:'enabled'});
 await service.listAllDepartments(); await service.getDepartmentEmployeeCounts();
 await service.listPositions({page:1,size:20,departmentId:2}); await service.listAllPositions();
 await service.listAllEmployees(); await service.getSummary(); await service.getEmployee(9);
 const department = {departmentCode:'D',departmentName:'部门',parentId:null,managerEmployeeId:null,sortOrder:0,status:'enabled' as const};
 const position = {positionCode:'P',positionName:'岗位',departmentId:null,responsibilities:'',status:'enabled' as const};
 await service.createDepartment(department); await service.updateDepartment(2,department); await service.changeDepartmentStatus(2,'disabled');
 await service.createPosition(position); await service.updatePosition(3,position); await service.changePositionStatus(3,'disabled');
 await service.changeEmployeeStatus(9,'resigned'); await service.startFeishuSync(); await service.getFeishuSync(4);
 expect(fetcher.mock.calls.map(([url,init]) => [url,init.method ?? 'GET',init.body ? JSON.parse(init.body) : null])).toEqual([
 ['/api/organization/departments?page=1&size=20&status=enabled','GET',null],
 ['/api/organization/departments/all','GET',null],['/api/organization/departments/employee-counts','GET',null],
 ['/api/organization/positions?page=1&size=20&departmentId=2','GET',null],['/api/organization/positions/all','GET',null],
 ['/api/organization/employees/all','GET',null],['/api/organization/employees/summary','GET',null],['/api/organization/employees/9','GET',null],
 ['/api/organization/departments','POST',department],['/api/organization/departments/2','PUT',department],['/api/organization/departments/2/status','PATCH',{status:'disabled'}],
 ['/api/organization/positions','POST',position],['/api/organization/positions/3','PUT',position],['/api/organization/positions/3/status','PATCH',{status:'disabled'}],
 ['/api/organization/employees/9/status','PATCH',{status:'resigned'}],['/api/organization/feishu-syncs','POST',null],['/api/organization/feishu-syncs/4','GET',null]
 ]);
});
