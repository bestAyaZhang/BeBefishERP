export type SidebarIconName =
  | 'dashboard'
  | 'product'
  | 'category'
  | 'customer'
  | 'supplier'
  | 'warehouse'
  | 'inventory'
  | 'organization'
  | 'permission'
  | 'sales-orders'
  | 'shipping'
  | 'finance';

export type WorkspaceIconName = 'dashboard' | 'product' | 'tasks' | 'inventory' | 'purchase' | 'sales' | 'calendar' | 'messages';

export interface SidebarNavigationChild {
  label: string;
  routeName: string;
  permission: string;
}

export interface SidebarNavigationItem {
  id: string;
  label: string;
  icon: SidebarIconName;
  permission?: string;
  routeName?: string;
  children?: SidebarNavigationChild[];
}

export interface WorkspaceNavigationItem {
  id: string;
  key: string;
  label: string;
  icon: WorkspaceIconName;
  permission: string;
}

export interface NavigationCatalog {
  sidebar: SidebarNavigationItem[];
  workspace: WorkspaceNavigationItem[];
}
