<script setup lang="ts">
import { inject, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { currentUser } from '../../../services/authSession';
import { customerAccountService, type CustomerAccountService, type CustomerPlatform } from '../customerAccountService';
import MasterdataFilterBar from '../components/MasterdataFilterBar.vue';
import MasterdataPageHeader from '../components/MasterdataPageHeader.vue';
import MasterdataTable from '../components/MasterdataTable.vue';
import { masterdataService } from '../masterdataService';
import type { Customer, MasterdataRow, MasterdataService, PageResult, RecordStatus } from '../types';

const service = inject<MasterdataService>('masterdataService', masterdataService);
const router = useRouter();
const accountService = inject<CustomerAccountService>('customerAccountService', customerAccountService);
const platforms = ref<CustomerPlatform[]>([]);
const has = (permission: string) => currentUser.value?.permissions.includes(permission) ?? false;
const result = ref<PageResult<Customer>>({ records: [], page: 1, pageSize: 20, total: 0 });
const keyword = ref('');
const status = ref<RecordStatus | ''>('');
const loading = ref(false);
const errorMessage = ref('');
const columns = [{ key: 'customerName', label: '客户名称' }, { key: 'platformId', label: '所属平台' }, { key: 'address', label: '地址' }, { key: 'contactPerson', label: '联系人' }, { key: 'mobile', label: '手机' }, { key: 'transportMethod', label: '运输方式' }, { key: 'settlementCycle', label: '结算周期' }, { key: 'status', label: '状态' }];
const transportLabels: Record<string, string> = { pickup: '自提', delivery: '送货上门', consignment: '托运', freight: '托运', express: '快递' };
const settlementLabels: Record<string, string> = { cash: '现结', daily: '日结', monthly: '月结', quarterly: '季结', annual: '年结', yearly: '年结' };

function asCustomer(row: MasterdataRow) { return row as Customer; }
function isSystemCustomer(row: MasterdataRow) { return asCustomer(row).system; }
async function load(page = result.value.page) { loading.value = true; errorMessage.value = ''; try { result.value = await service.listCustomers({ page, size: result.value.pageSize, keyword: keyword.value, status: status.value || undefined }); } catch (error) { errorMessage.value = error instanceof Error ? error.message : '客户加载失败'; } finally { loading.value = false; } }
async function openCreate() { await router.push({ name: 'customer-new' }); }
async function edit(customer: Customer) { await router.push({ name: 'customer-edit', params: { id: customer.id } }); }
async function toggleStatus(customer: Customer) { try { await service.changeCustomerStatus(customer.id, customer.status === 'enabled' ? 'disabled' : 'enabled'); await load(); } catch (error) { errorMessage.value = error instanceof Error ? error.message : '更新状态失败'; } }
onMounted(() => { void load(1); void accountService.listPlatforms().then(result => { platforms.value = result; }).catch(() => { errorMessage.value = '平台信息加载失败，请刷新重试'; }); });
</script>

<template>
  <section class="mx-auto max-w-[1440px] space-y-5">
    <MasterdataPageHeader title="客户管理" description="维护客户姓名、电话、地址和所属平台，设置专属货品价格，查看账单并开单打印。" button-label="新增客户" button-test-id="add-customer" :hide-create="!has('customer:create')" @create="openCreate" />
    <MasterdataFilterBar :keyword="keyword" :status="status" placeholder="搜索客户名称、联系人或电话" @update:keyword="keyword = $event" @update:status="status = $event" @search="load(1)" />
    <p v-if="errorMessage" class="border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ errorMessage }}</p>
    <MasterdataTable :records="result.records" :columns="columns" :page="result.page" :page-size="result.pageSize" :total="result.total" :loading="loading" @page="load"><template #cell-platformId="{ record }">{{ platforms.find(p => p.id === record.platformId)?.name || '未分配' }}</template><template #cell-address="{ record }">{{ [record.province, record.city, record.district, record.detailAddress].filter(Boolean).join('') || '—' }}</template><template #cell-transportMethod="{ record }">{{ transportLabels[String(record.transportMethod)] ?? record.transportMethod }}</template><template #cell-settlementCycle="{ record }">{{ settlementLabels[String(record.settlementCycle)] ?? record.settlementCycle }}</template><template #actions="{ record }"><div class="flex min-w-max justify-end gap-3 whitespace-nowrap text-sm"><button type="button" class="shrink-0 whitespace-nowrap text-[#536dff] hover:underline" @click="router.push({ name: 'customer-detail', params: { id: record.id } })">货品 / 账单</button><button v-if="has('sales:create') && record.status === 'enabled'" type="button" class="text-[#536dff] hover:underline" @click="router.push({ name: 'sales-create', query: { customerId: record.id } })">开单</button><button v-if="has('customer:edit')" type="button" class="shrink-0 whitespace-nowrap text-slate-700 hover:underline" @click="edit(asCustomer(record))">编辑</button><button :disabled="isSystemCustomer(record) || !has('customer:edit')" :data-testid="`customer-status-${record.id}`" type="button" class="shrink-0 whitespace-nowrap text-slate-500 hover:underline disabled:cursor-not-allowed disabled:opacity-40" @click="toggleStatus(asCustomer(record))">{{ record.status === 'enabled' ? '停用' : '启用' }}</button></div></template></MasterdataTable>
  </section>
</template>
