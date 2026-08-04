import { createService } from '../../services/serviceFactory';
import { httpProductService } from './httpProductService';
import { mockProductService } from './mockProductService';
import type { ProductService } from './types';

export const productService: ProductService = createService(() => mockProductService, () => httpProductService);
