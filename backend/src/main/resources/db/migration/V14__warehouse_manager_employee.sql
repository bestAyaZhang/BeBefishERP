alter table warehouse add column manager_employee_id bigint null,
 add constraint fk_warehouse_manager foreign key (manager_employee_id) references employee(id);

update warehouse warehouse
join (
 select min(id) id,name
 from employee
 group by name
 having count(*) = 1
) employee on employee.name = warehouse.manager_name
set warehouse.manager_employee_id = employee.id
where warehouse.manager_name is not null;
