<script setup lang="ts">
import { ArrowLeft, Save } from 'lucide-vue-next';
import { computed, inject, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { parseCustomerText } from '../addressParser';
import { masterdataService } from '../masterdataService';
import { regionService } from '../regionService';
import type { RegionOption, RegionService } from '../regionService';
import type { Customer, MasterdataService, SaveCustomerPayload } from '../types';

const service = inject<MasterdataService>('masterdataService', masterdataService);
const regions = inject<RegionService>('regionService', regionService);
const route = useRoute();
const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const errorMessage = ref('');
const addressText = ref('');
const parseMessage = ref('');
const provinces = ref<RegionOption[]>([]);
const cities = ref<RegionOption[]>([]);
const districts = ref<RegionOption[]>([]);

const blankForm = (): SaveCustomerPayload => ({
  customerName: '',
  contactPerson: '',
  mobile: '',
  telephone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  transportMethod: 'delivery',
  settlementCycle: 'monthly',
  remark: ''
});

const form = ref<SaveCustomerPayload>(blankForm());
const isEdit = computed(() => route.name === 'customer-edit');
const pageTitle = computed(() => (isEdit.value ? '编辑客户' : '新增客户'));
const selectedProvince = computed(() => provinces.value.find((province) => province.name === form.value.province));
const selectedCity = computed(() => cities.value.find((city) => city.name === form.value.city));

function fillForm(customer: Customer) {
  form.value = {
    customerName: customer.customerName,
    contactPerson: customer.contactPerson,
    mobile: customer.mobile,
    telephone: customer.telephone,
    province: customer.province,
    city: customer.city,
    district: customer.district,
    detailAddress: customer.detailAddress,
    transportMethod: customer.transportMethod,
    settlementCycle: customer.settlementCycle,
    remark: customer.remark
  };
}

async function loadCustomer() {
  if (!isEdit.value) return;

  const id = Number(route.params.id);
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await service.listCustomers({ page: 1, size: 100 });
    const customer = result.records.find((record) => record.id === id);
    if (!customer) throw new Error('客户不存在');
    fillForm(customer);
    addressText.value = [customer.province, customer.city, customer.district, customer.detailAddress].filter(Boolean).join('');
    cities.value = selectedProvince.value ? await regions.listCities(selectedProvince.value.code) : [];
    districts.value = selectedCity.value ? await regions.listDistricts(selectedCity.value.code) : [];
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '客户加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadRegions() {
  provinces.value = await regions.listProvinces();
}

async function handleProvinceChange() {
  form.value.city = '';
  form.value.district = '';
  districts.value = [];
  cities.value = selectedProvince.value ? await regions.listCities(selectedProvince.value.code) : [];
}

async function handleCityChange() {
  form.value.district = '';
  districts.value = selectedCity.value ? await regions.listDistricts(selectedCity.value.code) : [];
}

async function parseAddressInput() {
  const parsed = parseCustomerText(addressText.value);
  if (!parsed.customerName && !parsed.contactPerson && !parsed.mobile && !parsed.province && !parsed.city && !parsed.district) {
    parseMessage.value = '未识别到客户或地址信息，请手动填写。';
    return;
  }

  if (parsed.customerName) form.value.customerName = parsed.customerName;
  if (parsed.contactPerson) form.value.contactPerson = parsed.contactPerson;
  if (parsed.mobile) form.value.mobile = parsed.mobile;
  form.value.province = parsed.province;
  cities.value = selectedProvince.value ? await regions.listCities(selectedProvince.value.code) : [];
  form.value.city = parsed.city;
  districts.value = selectedCity.value ? await regions.listDistricts(selectedCity.value.code) : [];
  form.value.district = parsed.district;
  form.value.detailAddress = parsed.detailAddress;
  parseMessage.value = parsed.customerName && parsed.contactPerson && parsed.mobile && parsed.province && parsed.city && parsed.district
    ? '客户和地址信息已识别并填入。'
    : '已填入可识别的信息，缺失部分请手动补充。';
}

async function goBack() {
  await router.push({ name: 'customers' });
}

async function save() {
  if (!isEdit.value) {
    const requiredFields: Array<[string, string]> = [
      [form.value.customerName, '客户姓名'],
      [form.value.contactPerson, '联系人'],
      [form.value.mobile, '手机号'],
      [form.value.province, '省'],
      [form.value.city, '市'],
      [form.value.district, '区/县'],
      [form.value.detailAddress, '详细地址']
    ];
    const missing = requiredFields.find(([value]) => !value.trim());
    if (missing) {
      errorMessage.value = `请填写${missing[1]}`;
      return;
    }
  }

  saving.value = true;
  errorMessage.value = '';
  try {
    if (isEdit.value) await service.updateCustomer(Number(route.params.id), form.value);
    else await service.createCustomer(form.value);
    await goBack();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存客户失败';
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  await loadRegions();
  await loadCustomer();
});
</script>

<template>
  <section data-testid="customer-form-page" class="mx-auto max-w-[1440px] space-y-6">
    <header class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <button data-testid="customer-form-back" type="button" class="mb-3 inline-flex items-center gap-2 text-sm font-bold text-slate-400 transition hover:text-[#536dff]" @click="goBack">
          <ArrowLeft class="h-4 w-4" aria-hidden="true" />
          返回客户列表
        </button>
        <p class="text-[11px] font-black uppercase tracking-[0.18em] text-[#536dff]">Master data / Customers</p>
        <h1 class="mt-2 text-2xl font-black tracking-tight text-[#25314d]">{{ pageTitle }}</h1>
        <p class="mt-2 text-sm font-medium text-slate-400">客户信息较多，使用独立页面录入，便于完整维护档案。</p>
      </div>
    </header>

    <p v-if="errorMessage" class="rounded-2xl border border-rose-200 bg-rose-50 px-5 py-4 text-sm font-bold text-rose-700">{{ errorMessage }}</p>
    <div v-if="loading" class="rounded-[22px] border border-slate-200 bg-white px-6 py-8 text-sm font-bold text-slate-400">正在加载客户信息...</div>

    <form v-else class="rounded-[22px] border border-slate-200 bg-white p-6 shadow-[0_18px_48px_rgba(31,45,74,0.06)]" @submit.prevent="save">
      <div data-testid="customer-quick-parse" class="mb-6 rounded-2xl border border-dashed border-blue-200 bg-blue-50/45 p-4"><div class="flex flex-wrap items-end gap-3"><label class="min-w-0 flex-1 space-y-2 text-sm font-bold text-slate-500"><span>客户与地址快速识别</span><input data-testid="customer-parse-address" v-model="addressText" placeholder="例如：杭州酒店用品店 张经理 13800138000 浙江省杭州市余杭区良渚街道88号" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label><button data-testid="parse-address" type="button" class="h-11 shrink-0 rounded-xl bg-[#536dff] px-4 text-sm font-black text-white shadow-[0_10px_22px_rgba(83,109,255,0.18)] transition hover:bg-[#4560eb]" @click="parseAddressInput">识别并填入</button></div><p v-if="parseMessage" class="mt-2 text-xs font-bold text-[#536dff]">{{ parseMessage }}</p></div>
      <div class="grid gap-6 xl:grid-cols-2">
        <section class="rounded-2xl bg-slate-50/80 p-5">
          <div class="mb-5 border-b border-slate-200 pb-4">
            <h2 class="text-base font-black text-[#25314d]">基础信息</h2>
            <p class="mt-1 text-sm font-medium text-slate-400">维护客户识别和联系信息。</p>
          </div>
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="space-y-2 text-sm font-bold text-slate-500"><span>客户名称<span v-if="!isEdit" class="ml-1 text-rose-500">*</span></span><input data-testid="customer-name" v-model="form.customerName" :required="!isEdit" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
            <label class="space-y-2 text-sm font-bold text-slate-500"><span>联系人<span v-if="!isEdit" class="ml-1 text-rose-500">*</span></span><input data-testid="customer-contact" v-model="form.contactPerson" :required="!isEdit" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
            <label class="space-y-2 text-sm font-bold text-slate-500"><span>手机号<span v-if="!isEdit" class="ml-1 text-rose-500">*</span></span><input data-testid="customer-mobile" v-model="form.mobile" :required="!isEdit" inputmode="tel" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
            <label class="space-y-2 text-sm font-bold text-slate-500 sm:col-span-2"><span>联系电话</span><input v-model="form.telephone" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
          </div>
        </section>

        <section class="rounded-2xl bg-slate-50/80 p-5">
          <div class="mb-5 border-b border-slate-200 pb-4">
            <h2 class="text-base font-black text-[#25314d]">地址与结算</h2>
            <p class="mt-1 text-sm font-medium text-slate-400">补充配送地址和客户结算约定。</p>
          </div>
          <div class="grid gap-4 sm:grid-cols-3">
            <label class="space-y-2 text-sm font-bold text-slate-500"><span>省<span v-if="!isEdit" class="ml-1 text-rose-500">*</span></span><select data-testid="customer-province" v-model="form.province" :required="!isEdit" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" @change="handleProvinceChange"><option value="">请选择省</option><option v-for="province in provinces" :key="province.code" :value="province.name">{{ province.name }}</option></select></label>
            <label class="space-y-2 text-sm font-bold text-slate-500"><span>市<span v-if="!isEdit" class="ml-1 text-rose-500">*</span></span><select data-testid="customer-city" v-model="form.city" :disabled="!form.province" :required="!isEdit" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50 disabled:cursor-not-allowed disabled:bg-slate-50" @change="handleCityChange"><option value="">请选择市</option><option v-for="city in cities" :key="city.code" :value="city.name">{{ city.name }}</option></select></label>
            <label class="space-y-2 text-sm font-bold text-slate-500"><span>区/县<span v-if="!isEdit" class="ml-1 text-rose-500">*</span></span><select data-testid="customer-district" v-model="form.district" :disabled="!form.city" :required="!isEdit" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50 disabled:cursor-not-allowed disabled:bg-slate-50"><option value="">请选择区/县</option><option v-for="district in districts" :key="district.code" :value="district.name">{{ district.name }}</option></select></label>
            <label class="space-y-2 text-sm font-bold text-slate-500 sm:col-span-3"><span>详细地址<span v-if="!isEdit" class="ml-1 text-rose-500">*</span></span><input data-testid="customer-detail-address" v-model="form.detailAddress" :required="!isEdit" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50" /></label>
            <label class="space-y-2 text-sm font-bold text-slate-500 sm:col-span-3"><span>运输方式</span><select data-testid="customer-transport" v-model="form.transportMethod" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50"><option value="pickup">自提</option><option value="delivery">送货上门</option><option value="consignment">托运</option><option value="express">快递</option></select></label>
            <label class="space-y-2 text-sm font-bold text-slate-500 sm:col-span-3"><span>结算周期</span><select data-testid="customer-settlement" v-model="form.settlementCycle" class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50"><option value="cash">现结</option><option value="daily">日结</option><option value="monthly">月结</option><option value="quarterly">季结</option><option value="annual">年结</option></select></label>
            <label class="space-y-2 text-sm font-bold text-slate-500 sm:col-span-3"><span>备注</span><textarea v-model="form.remark" rows="4" class="w-full resize-y rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm font-bold text-[#25314d] outline-none transition focus:border-[#536dff] focus:ring-4 focus:ring-blue-50"></textarea></label>
          </div>
        </section>
      </div>

      <div class="mt-6 flex flex-wrap justify-end gap-3 border-t border-slate-100 pt-5">
        <button type="button" class="h-11 rounded-xl border border-slate-200 px-5 text-sm font-black text-slate-500 transition hover:border-slate-300 hover:text-[#25314d]" @click="goBack">取消</button>
        <button data-testid="customer-form-save" type="button" class="inline-flex h-11 items-center gap-2 rounded-xl bg-[#536dff] px-5 text-sm font-black text-white shadow-[0_12px_24px_rgba(83,109,255,0.24)] transition hover:bg-[#4560eb] disabled:cursor-not-allowed disabled:opacity-60" :disabled="saving" @click="save">
          <Save class="h-4 w-4" aria-hidden="true" />
          {{ saving ? '保存中...' : '保存客户' }}
        </button>
      </div>
    </form>
  </section>
</template>
