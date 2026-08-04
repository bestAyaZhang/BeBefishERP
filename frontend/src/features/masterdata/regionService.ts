export interface RegionOption {
  code: string;
  name: string;
}

export interface RegionNode extends RegionOption {
  cities: Array<RegionOption & { districts: RegionOption[] }>;
}

export interface RegionService {
  listProvinces(): Promise<RegionOption[]>;
  listCities(provinceCode: string): Promise<RegionOption[]>;
  listDistricts(cityCode: string): Promise<RegionOption[]>;
}

export const regionTree: RegionNode[] = [
  {
    code: '330000',
    name: '浙江省',
    cities: [
      { code: '330100', name: '杭州市', districts: [{ code: '330110', name: '余杭区' }, { code: '330106', name: '西湖区' }, { code: '330108', name: '滨江区' }, { code: '330109', name: '萧山区' }] },
      { code: '330200', name: '宁波市', districts: [{ code: '330203', name: '海曙区' }, { code: '330205', name: '江北区' }, { code: '330212', name: '鄞州区' }] },
      { code: '330700', name: '金华市', districts: [{ code: '330702', name: '婺城区' }, { code: '330703', name: '金东区' }, { code: '330782', name: '义乌市' }] }
    ]
  },
  {
    code: '320000',
    name: '江苏省',
    cities: [{ code: '320100', name: '南京市', districts: [{ code: '320102', name: '玄武区' }, { code: '320104', name: '秦淮区' }, { code: '320105', name: '建邺区' }] }]
  },
  {
    code: '440000',
    name: '广东省',
    cities: [{ code: '440100', name: '广州市', districts: [{ code: '440103', name: '荔湾区' }, { code: '440104', name: '越秀区' }, { code: '440106', name: '天河区' }] }]
  },
  {
    code: '310000',
    name: '上海市',
    cities: [{ code: '310100', name: '上海市', districts: [{ code: '310115', name: '浦东新区' }, { code: '310104', name: '徐汇区' }, { code: '310105', name: '长宁区' }] }]
  }
];

function copyOptions(options: RegionOption[]) {
  return options.map((option) => ({ ...option }));
}

export const mockRegionService: RegionService = {
  async listProvinces() {
    return copyOptions(regionTree);
  },
  async listCities(provinceCode) {
    return copyOptions(regionTree.find((province) => province.code === provinceCode)?.cities ?? []);
  },
  async listDistricts(cityCode) {
    return copyOptions(regionTree.flatMap((province) => province.cities).find((city) => city.code === cityCode)?.districts ?? []);
  }
};

export const regionService: RegionService = mockRegionService;
